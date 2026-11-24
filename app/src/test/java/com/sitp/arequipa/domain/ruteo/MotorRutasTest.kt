package com.sitp.arequipa.domain.ruteo

import com.sitp.arequipa.domain.model.ModoBusqueda
import com.sitp.arequipa.domain.model.OpcionesBusqueda
import com.sitp.arequipa.domain.model.ParametrosBusqueda
import com.sitp.arequipa.domain.model.Ruta
import com.sitp.arequipa.domain.model.Sentido
import com.sitp.arequipa.domain.model.TipoTramo
import com.sitp.arequipa.domain.ruteo.RedSintetica.c
import com.sitp.arequipa.domain.ruteo.RedSintetica.recorrido
import com.sitp.arequipa.domain.ruteo.RedSintetica.ruta
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Motor de rutas (ADR-010) con redes sintéticas en metros. */
class MotorRutasTest {

    private val param = ParametrosBusqueda()

    private fun buscar(rutas: List<Ruta>, origen: Pair<Double, Double>, destino: Pair<Double, Double>, modo: ModoBusqueda = ModoBusqueda.TIEMPO): OpcionesBusqueda =
        runBlocking {
            MotorRutas(RedTransporte.construir(rutas, param), param)
                .buscar(c(origen.first, origen.second), c(destino.first, destino.second), modo)
        }

    @Test
    fun `respeta el sentido de la combi`() {
        // Ida hacia el este por y=0; vuelta hacia el oeste por y=60 (calle paralela)
        val r = ruta("R1", recorrido(0.0 to 0.0, 3000.0 to 0.0), recorrido(3000.0 to 60.0, 0.0 to 60.0))
        val haciaEste = buscar(listOf(r), 100.0 to 0.0, 2900.0 to 0.0).porTiempo.first()
        val combiEste = haciaEste.tramosCombi.single()
        assertEquals(Sentido.IDA, combiEste.sentido)
        // Viaja ~2,8 km sobre el recorrido, no da toda la vuelta (6 km)
        assertTrue("metros = ${combiEste.metros}", combiEste.metros in 2500.0..3100.0)

        val haciaOeste = buscar(listOf(r), 2900.0 to 60.0, 100.0 to 60.0).porTiempo.first()
        assertEquals(Sentido.VUELTA, haciaOeste.tramosCombi.single().sentido)
    }

    @Test
    fun `ruta directa da una combi y cuesta un pasaje`() {
        val r = ruta("R1", recorrido(0.0 to 0.0, 4000.0 to 0.0))
        val op = buscar(listOf(r), 0.0 to 0.0, 4000.0 to 0.0).porTiempo.first()
        assertEquals(listOf("R1"), op.codigos)
        assertEquals(1.30, op.costo, 1e-9)
    }

    @Test
    fun `encuentra el transbordo entre dos rutas`() {
        val r1 = ruta("R1", recorrido(0.0 to 0.0, 3000.0 to 0.0))
        val r2 = ruta("R2", recorrido(3000.0 to 0.0, 3000.0 to 3000.0))
        val op = buscar(listOf(r1, r2), 0.0 to 0.0, 3000.0 to 3000.0).porTiempo.first()
        assertEquals(listOf("R1", "R2"), op.codigos)
        assertEquals(2.60, op.costo, 1e-9)
        // El transbordo queda cerca de la esquina (3000, 0)
        val bajada = op.tramosCombi[0].hasta
        assertTrue(Geo.metros(bajada.lat, bajada.lng, c(3000.0, 0.0).lat, c(3000.0, 0.0).lng) < 300.0)
    }

    @Test
    fun `espera es alfa por el intervalo de la frecuencia`() {
        val r = ruta("R1", recorrido(0.0 to 0.0, 4000.0 to 0.0), frecuencia = "8-12")
        val tramo = buscar(listOf(r), 0.0 to 0.0, 4000.0 to 0.0).porTiempo.first().tramosCombi.single()
        assertEquals(10.0, tramo.intervaloMin, 1e-9)
        assertEquals(0.75 * 10.0, tramo.esperaMin, 1e-9)

        val sinDato = ruta("R1", recorrido(0.0 to 0.0, 4000.0 to 0.0))
        val t2 = buscar(listOf(sinDato), 0.0 to 0.0, 4000.0 to 0.0).porTiempo.first().tramosCombi.single()
        assertEquals(param.intervaloPorDefectoMin, t2.intervaloMin, 1e-9)
    }

    @Test
    fun `rango de tiempo va de espera cero al intervalo completo`() {
        val r = ruta("R1", recorrido(0.0 to 0.0, 4000.0 to 0.0), frecuencia = "12")
        val op = buscar(listOf(r), 0.0 to 0.0, 4000.0 to 0.0).porTiempo.first()
        val t = op.tramosCombi.single()
        assertEquals(op.minutosEstimado - t.esperaMin, op.minutosMinimo, 1e-9)
        assertEquals(op.minutosMinimo + t.intervaloMin, op.minutosMaximo, 1e-9)
    }

    @Test
    fun `lineas atractivas suman sus frecuencias`() {
        // Dos rutas con el mismo recorrido, cada 10 min: juntas pasan cada 5 min
        val a = ruta("A", recorrido(0.0 to 0.0, 4000.0 to 0.0), frecuencia = "10")
        val b = ruta("B", recorrido(0.0 to 20.0, 4000.0 to 20.0), frecuencia = "10")
        val t = buscar(listOf(a, b), 0.0 to 0.0, 4000.0 to 0.0).porTiempo.first().tramosCombi.single()
        assertEquals(1, t.alternativas.size)
        assertEquals(5.0, t.intervaloMin, 1e-6)
        assertEquals(0.75 * 5.0, t.esperaMin, 1e-6)
    }

    @Test
    fun `da entre 3 y 5 opciones distintas por modo`() {
        // Seis rutas paralelas (a 0, 40, 80… m) entre origen y destino, con frecuencias distintas
        val rutas = (0 until 6).map { i ->
            ruta("P$i", recorrido(0.0 to i * 40.0, 4000.0 to i * 40.0), frecuencia = "${8 + i * 2}")
        }
        val op = buscar(rutas, 0.0 to 0.0, 4000.0 to 0.0)
        assertTrue("porTiempo=${op.porTiempo.size}", op.porTiempo.size in 3..5)
        assertTrue("porCosto=${op.porCosto.size}", op.porCosto.size in 3..5)
        assertEquals(op.porTiempo.size, op.porTiempo.map { it.clave }.toSet().size)
        // Ordenadas por tiempo
        assertEquals(op.porTiempo.map { it.minutosEstimado }.sorted(), op.porTiempo.map { it.minutosEstimado })
    }

    @Test
    fun `modo costo prefiere menos combis y modo tiempo la mas rapida`() {
        // Una combi lenta que da un rodeo de 11 km, o dos combis directas de 1,5 km cada una
        val lenta = ruta("LENTA", recorrido(0.0 to 0.0, 0.0 to 4000.0, 3000.0 to 4000.0, 3000.0 to 0.0))
        val a = ruta("A", recorrido(0.0 to 0.0, 1500.0 to 0.0))
        val b = ruta("B", recorrido(1500.0 to 0.0, 3000.0 to 0.0))
        // Origen y destino a más de 1 km entre sí para que caminar no sea una opción de acceso directo
        val op = buscar(listOf(lenta, a, b), 0.0 to 0.0, 3000.0 to 0.0)
        assertEquals(2, op.porTiempo.first().numeroCombis)
        assertEquals(1, op.porCosto.first().numeroCombis)
        assertEquals(listOf("LENTA"), op.porCosto.first().codigos)
    }

    @Test
    fun `sin combis cerca devuelve vacio y completo`() {
        val r = ruta("R1", recorrido(0.0 to 0.0, 1000.0 to 0.0))
        val op = buscar(listOf(r), 0.0 to 10_000.0, 1000.0 to 10_000.0)
        assertTrue(op.vacia)
        assertTrue(op.completa)
    }

    @Test
    fun `cada tramo de combi trae su recorrido para dibujar`() {
        val r = ruta("R1", recorrido(0.0 to 0.0, 2000.0 to 0.0, 2000.0 to 2000.0))
        val t = buscar(listOf(r), 0.0 to 0.0, 2000.0 to 2000.0).porTiempo.first().tramosCombi.single()
        assertTrue(t.recorrido.size > 10)
        assertEquals(t.desde, t.recorrido.first())
        assertEquals(t.hasta, t.recorrido.last())
        val tipos = buscar(listOf(r), 0.0 to 0.0, 2000.0 to 2000.0).porTiempo.first().tramos.map { it.tipo }
        assertTrue(TipoTramo.COMBI in tipos)
    }
}
