package com.sitp.arequipa.presentation.busqueda

import com.sitp.arequipa.domain.model.Coordenada
import com.sitp.arequipa.domain.model.Itinerario
import com.sitp.arequipa.domain.model.TipoTramo
import com.sitp.arequipa.domain.model.Tramo
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Convierte un [Itinerario] calculado por la app en lo que muestra la pantalla y en el texto del historial.
 * No usa IA: todos los datos vienen del motor de rutas (ADR-011).
 */
object TextoItinerario {

    /** «25 min» o «25 min (entre 20 y 32)». */
    fun tiempo(it: Itinerario): String {
        val est = it.minutosEstimado.roundToInt()
        val min = it.minutosMinimo.roundToInt()
        val max = it.minutosMaximo.roundToInt()
        return if (max - min >= 2) "$est min (entre $min y $max)" else "$est min"
    }

    fun costo(it: Itinerario): String = String.format(Locale.US, "S/%.2f", it.costo)

    /**
     * Pasos para la línea de tiempo del panel (mismo modelo que antes, así se reutilizan sus tarjetas).
     * @param combiDe convierte el código interno («LA-C8») en lo que ve el usuario («LA»).
     */
    fun aRutaParseada(it: Itinerario, nombres: Map<Coordenada, String>, combiDe: (String) -> String): RutaParseada {
        val tramos = it.tramos
        val combis = tramos.withIndex().filter { (_, t) -> t.tipo == TipoTramo.COMBI }
        val segmentos = combis.mapIndexed { n, (indice, t) ->
            val codigo = t.codigoRuta.orEmpty()
            val caminataAntes = tramos.getOrNull(indice - 1)?.takeIf { c -> c.tipo == TipoTramo.CAMINATA }
            val caminataDespues = tramos.getOrNull(indice + 1)?.takeIf { c -> c.tipo == TipoTramo.CAMINATA }
            val esUltima = n == combis.lastIndex

            val detalleSubida = buildList {
                caminataAntes?.let { c -> add(if (n == 0) "Camina ${metrosTexto(c.metros)} (${minTexto(c.minutos)})" else "Camina ${metrosTexto(c.metros)} hasta esta combi") }
                add("espera ~${minTexto(t.esperaMin)} (pasa cada ${minTexto(t.intervaloMin)})")
                if (t.alternativas.isNotEmpty()) add("también sirve: ${t.alternativas.joinToString(", ") { a -> combiDe(a) }}, la que pase primero")
            }.joinToString(" · ")
            val detalleBajada = buildList {
                add("${minTexto(t.minutos)} a bordo")
                if (esUltima) caminataDespues?.let { c -> add("luego camina ${metrosTexto(c.metros)} hasta el destino") }
            }.joinToString(" · ")

            SegmentoRuta(
                numero = n + 1,
                codigoRuta = codigo,
                abordarTexto = "Tome la combi $codigo en ${lugar(t.desde, nombres)}",
                abordarDetalle = detalleSubida.replaceFirstChar { c -> c.uppercase() },
                bajarTexto = "Bájese en ${lugar(t.hasta, nombres)}",
                bajarDetalle = detalleBajada.replaceFirstChar { c -> c.uppercase() }
            )
        }
        return RutaParseada(it.codigos.distinct(), segmentos, tiempo(it), costo(it))
    }

    /**
     * Texto que se guarda en el historial, con el mismo formato que leía «Mi Actividad»
     * (RUTAS / pasos / ESTIMACIÓN / COSTO TOTAL), para que las búsquedas viejas y nuevas se vean igual.
     */
    fun aTextoHistorial(it: Itinerario, nombres: Map<Coordenada, String>): String {
        val combis = it.tramosCombi
        val pasos = combis.mapIndexed { i, t ->
            "${i + 1}. Tome la combi ${t.codigoRuta} en ${lugar(t.desde, nombres)}, y bájese en ${lugar(t.hasta, nombres)}"
        }.joinToString("\n")
        val n = combis.size
        return buildString {
            append("RUTAS: [${it.codigos.joinToString(", ")}]\n\n")
            append(pasos)
            append("\n\nESTIMACIÓN: ${it.minutosEstimado.roundToInt()} minutos aproximadamente\n")
            append("COSTO TOTAL: ${costo(it)} ($n ${if (n == 1) "combi" else "combis"} x S/1.30)")
        }
    }

    private fun lugar(p: Coordenada, nombres: Map<Coordenada, String>) = nombres[p] ?: "el punto marcado en el mapa"
    private fun minTexto(m: Double) = "${m.roundToInt().coerceAtLeast(1)} min"
    private fun metrosTexto(m: Double) = if (m >= 1000) String.format(Locale.US, "%.1f km", m / 1000) else "${(m / 10).roundToInt() * 10} m"

    /** Sentido de cada código en la opción elegida («IDA» / «VUELTA»), para la insignia del panel. */
    fun sentidos(it: Itinerario): Map<String, String> =
        it.tramosCombi.filter { t -> t.codigoRuta != null && t.sentido != null }
            .associate { t: Tramo -> t.codigoRuta!!.trim() to t.sentido!!.name }
}
