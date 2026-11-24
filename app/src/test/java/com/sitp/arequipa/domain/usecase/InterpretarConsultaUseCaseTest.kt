package com.sitp.arequipa.domain.usecase

import com.sitp.arequipa.domain.model.AtribucionDatos
import com.sitp.arequipa.domain.model.ConsultaInterpretada
import com.sitp.arequipa.domain.model.Coordenada
import com.sitp.arequipa.domain.model.LugarEncontrado
import com.sitp.arequipa.domain.model.LugarPedido
import com.sitp.arequipa.domain.model.ModoBusqueda
import com.sitp.arequipa.domain.model.PuntoInterpretado
import com.sitp.arequipa.domain.repository.AsistenteRepository
import com.sitp.arequipa.domain.repository.LugaresRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Interpretación (ADR-011): la IA extrae nombres, la app busca; la IA solo desempata entre candidatos. */
class InterpretarConsultaUseCaseTest {

    private class AsistenteFalso(val consulta: ConsultaInterpretada, val elegido: Int? = null) : AsistenteRepository {
        var llamadasElegir = 0
        var candidatosRecibidos: List<String> = emptyList()
        override suspend fun interpretar(frase: String) = consulta
        override suspend fun elegirLugar(textoUsuario: String, candidatos: List<String>): Int? {
            llamadasElegir++; candidatosRecibidos = candidatos; return elegido
        }
    }

    private class LugaresFalsos(val porTexto: Map<String, List<LugarEncontrado>>) : LugaresRepository {
        val consultas = mutableListOf<Pair<String, Coordenada?>>()
        override suspend fun coordenadasANombre(lat: Double, lng: Double) = "x"
        override suspend fun atribucion(): AtribucionDatos? = null
        override suspend fun buscarPorNombre(texto: String, cerca: Coordenada?, limite: Int): List<LugarEncontrado> {
            consultas.add(texto to cerca)
            return porTexto[texto].orEmpty().take(limite)
        }
    }

    private fun lugar(nombre: String, puntaje: Int, lat: Double = -16.40) =
        LugarEncontrado(nombre, "Lugar · Arequipa", Coordenada(lat, -71.53), puntaje)

    private val gps = Coordenada(-16.41, -71.52)

    @Test fun `busca cada nombre en el catalogo y devuelve candidatos`() = runBlocking {
        val lugares = LugaresFalsos(mapOf("UNSA" to listOf(lugar("Universidad Nacional de San Agustín", 85)), "San Camilo" to listOf(lugar("Mercado San Camilo", 100))))
        val asistente = AsistenteFalso(ConsultaInterpretada(LugarPedido("UNSA"), LugarPedido("San Camilo"), ModoBusqueda.COSTO))
        val r = InterpretarConsultaUseCase(asistente, lugares)("de la UNSA a San Camilo", gps)
        assertEquals("Universidad Nacional de San Agustín", (r.origen as PuntoInterpretado.Candidatos).lugares.single().nombre)
        assertEquals("Mercado San Camilo", (r.destino as PuntoInterpretado.Candidatos).lugares.single().nombre)
        assertEquals(ModoBusqueda.COSTO, r.preferencia)
        assertEquals(0, asistente.llamadasElegir) // sin empate, la IA no se vuelve a llamar
        assertTrue(r.avisos.isEmpty())
    }

    @Test fun `desde aqui usa el gps y no busca nada`() = runBlocking {
        val lugares = LugaresFalsos(mapOf("Cayma" to listOf(lugar("Cayma", 100))))
        val asistente = AsistenteFalso(ConsultaInterpretada(LugarPedido(LugarPedido.MI_UBICACION), LugarPedido("Cayma"), null))
        val r = InterpretarConsultaUseCase(asistente, lugares)("desde aquí a Cayma", gps)
        assertTrue(r.origen is PuntoInterpretado.MiUbicacion)
        assertTrue(lugares.consultas.none { it.first == LugarPedido.MI_UBICACION })
        assertTrue(r.avisos.isEmpty())
    }

    @Test fun `desde aqui sin gps avisa`() = runBlocking {
        val asistente = AsistenteFalso(ConsultaInterpretada(LugarPedido(LugarPedido.MI_UBICACION), null, null))
        val r = InterpretarConsultaUseCase(asistente, LugaresFalsos(emptyMap()))("desde aquí", null)
        assertTrue(r.avisos.any { it.contains("ubicación") })
        assertTrue(r.destino is PuntoInterpretado.NoIndicado)
    }

    @Test fun `un lugar que no existe se avisa y no se inventa`() = runBlocking {
        val asistente = AsistenteFalso(ConsultaInterpretada(LugarPedido("Lugar Inexistente"), null, null))
        val r = InterpretarConsultaUseCase(asistente, LugaresFalsos(emptyMap()))("a lugar inexistente", gps)
        assertTrue((r.origen as PuntoInterpretado.Candidatos).lugares.isEmpty())
        assertTrue(r.avisos.single().contains("Lugar Inexistente"))
    }

    @Test fun `las restricciones que no se pueden cumplir se avisan`() = runBlocking {
        val asistente = AsistenteFalso(ConsultaInterpretada(null, null, null, listOf("pasar por el mercado")))
        val r = InterpretarConsultaUseCase(asistente, LugaresFalsos(emptyMap()))("quiero pasar por el mercado", gps)
        assertTrue(r.avisos.single().contains("pasar por el mercado"))
    }

    @Test fun `con empate la IA elige solo entre los candidatos`() = runBlocking {
        val lugares = LugaresFalsos(mapOf("Plaza de Armas" to listOf(lugar("Plaza de Armas", 80, -16.40), lugar("Plaza de Armas Cayma", 80, -16.35))))
        val asistente = AsistenteFalso(ConsultaInterpretada(LugarPedido("Plaza de Armas"), null, null), elegido = 1)
        val r = InterpretarConsultaUseCase(asistente, lugares)("a la plaza de armas", gps)
        assertEquals(1, asistente.llamadasElegir)
        assertEquals(2, asistente.candidatosRecibidos.size)
        val candidatos = (r.origen as PuntoInterpretado.Candidatos).lugares
        assertEquals("Plaza de Armas Cayma", candidatos.first().nombre) // el elegido pasa al primer lugar
        assertEquals(2, candidatos.size)
    }

    @Test fun `la referencia ordena por cercania`() = runBlocking {
        val lugares = LugaresFalsos(mapOf(
            "colegio X" to listOf(lugar("Colegio X", 100, -16.37)),
            "Tambo" to listOf(lugar("Tambo", 70))
        ))
        val asistente = AsistenteFalso(ConsultaInterpretada(LugarPedido("Tambo", referencia = "colegio X"), null, null))
        InterpretarConsultaUseCase(asistente, lugares)("al tambo cerca del colegio X", gps)
        val consultaTambo = lugares.consultas.first { it.first == "Tambo" }
        assertEquals(-16.37, consultaTambo.second!!.lat, 1e-9) // se busca cerca de la referencia, no del GPS
    }
}
