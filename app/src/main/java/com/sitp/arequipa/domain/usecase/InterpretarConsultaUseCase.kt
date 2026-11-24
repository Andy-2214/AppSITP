package com.sitp.arequipa.domain.usecase

import com.sitp.arequipa.domain.model.Coordenada
import com.sitp.arequipa.domain.model.LugarEncontrado
import com.sitp.arequipa.domain.model.LugarPedido
import com.sitp.arequipa.domain.model.PuntoInterpretado
import com.sitp.arequipa.domain.model.ResultadoInterpretacion
import com.sitp.arequipa.domain.repository.AsistenteRepository
import com.sitp.arequipa.domain.repository.LugaresRepository
import kotlinx.coroutines.CancellationException

/**
 * Chat con IA (ADR-011): la IA solo convierte la frase en nombres; la app los busca en su catálogo
 * (sin enviarlo a nadie) y propone hasta [CANDIDATOS] lugares por pin. El usuario confirma cada pin
 * moviendo el mapa; recién entonces el motor de rutas calcula. La IA no calcula nada.
 */
class InterpretarConsultaUseCase(
    private val asistente: AsistenteRepository,
    private val lugares: LugaresRepository
) {
    /**
     * @param ubicacionActual posición del GPS, para «desde aquí» y para desempatar por cercanía.
     * @throws com.sitp.arequipa.domain.repository.AsistenteNoDisponibleException sin conexión o respuesta inválida.
     */
    suspend operator fun invoke(frase: String, ubicacionActual: Coordenada?): ResultadoInterpretacion {
        val consulta = asistente.interpretar(frase)
        val avisos = mutableListOf<String>()

        val origen = resolver(consulta.origen, ubicacionActual, avisos)
        val destino = resolver(consulta.destino, ubicacionActual, avisos)
        if (origen is PuntoInterpretado.MiUbicacion && ubicacionActual == null) {
            avisos.add("No tengo tu ubicación: activa el GPS o mueve el mapa hasta el punto de partida.")
        }
        consulta.restriccionesNoSoportadas.forEach { avisos.add("Todavía no puedo tener en cuenta: $it.") }
        return ResultadoInterpretacion(origen, destino, consulta.preferencia, avisos)
    }

    private suspend fun resolver(pedido: LugarPedido?, ubicacion: Coordenada?, avisos: MutableList<String>): PuntoInterpretado {
        if (pedido == null || pedido.texto.isBlank()) return PuntoInterpretado.NoIndicado
        if (pedido.esMiUbicacion) return PuntoInterpretado.MiUbicacion

        // La referencia («cerca del colegio X») se busca primero y sirve para ordenar por cercanía
        val cerca = pedido.referencia?.takeIf { it.isNotBlank() }
            ?.let { lugares.buscarPorNombre(it, ubicacion, 1).firstOrNull()?.coordenada }
            ?: ubicacion
        var candidatos = lugares.buscarPorNombre(pedido.texto, cerca, CANDIDATOS)
        if (candidatos.isEmpty()) {
            avisos.add("No encontré «${pedido.texto}» en el mapa. Ubícalo moviendo el mapa o escribe un lugar cercano.")
        } else if (hayEmpate(candidatos)) {
            candidatos = desempatarConIA(pedido, candidatos)
        }
        return PuntoInterpretado.Candidatos(pedido.texto, candidatos)
    }

    private fun hayEmpate(c: List<LugarEncontrado>) =
        c.size >= 2 && c[0].puntaje == c[1].puntaje && c[0].nombre != c[1].nombre

    /** Solo cuando dos candidatos empatan: la IA elige entre esos pocos nombres (nunca inventa uno). */
    private suspend fun desempatarConIA(pedido: LugarPedido, candidatos: List<LugarEncontrado>): List<LugarEncontrado> {
        val texto = pedido.referencia?.let { "${pedido.texto} (cerca de $it)" } ?: pedido.texto
        val elegido = try {
            asistente.elegirLugar(texto, candidatos.map { "${it.nombre} (${it.detalle})" })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (elegido == null || elegido !in candidatos.indices || elegido == 0) return candidatos
        return listOf(candidatos[elegido]) + candidatos.filterIndexed { i, _ -> i != elegido }
    }

    companion object {
        const val CANDIDATOS = 3
    }
}
