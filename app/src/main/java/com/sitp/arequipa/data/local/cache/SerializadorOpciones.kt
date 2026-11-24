package com.sitp.arequipa.data.local.cache

import com.sitp.arequipa.data.json.Json
import com.sitp.arequipa.domain.model.Coordenada
import com.sitp.arequipa.domain.model.Itinerario
import com.sitp.arequipa.domain.model.OpcionesBusqueda
import com.sitp.arequipa.domain.model.Sentido
import com.sitp.arequipa.domain.model.TipoTramo
import com.sitp.arequipa.domain.model.Tramo

/** Convierte las opciones de una búsqueda a JSON y de vuelta, para la caché (Kotlin puro, se prueba sin Android). */
object SerializadorOpciones {

    fun aMapa(o: OpcionesBusqueda): Map<String, Any?> = mapOf(
        "tiempo" to o.porTiempo.map(::itinerario),
        "costo" to o.porCosto.map(::itinerario)
    )

    fun desdeMapa(m: Map<*, *>): OpcionesBusqueda? = try {
        OpcionesBusqueda(
            porTiempo = (m["tiempo"] as List<*>).map { leerItinerario(it as Map<*, *>) },
            porCosto = (m["costo"] as List<*>).map { leerItinerario(it as Map<*, *>) },
            completa = true
        )
    } catch (e: RuntimeException) {
        null // entrada vieja o dañada: se vuelve a calcular
    }

    fun aTexto(o: OpcionesBusqueda): String = Json.escribir(aMapa(o))

    fun desdeTexto(texto: String): OpcionesBusqueda? =
        (try { Json.leer(texto) } catch (e: IllegalArgumentException) { null } as? Map<*, *>)?.let(::desdeMapa)

    private fun itinerario(i: Itinerario): Map<String, Any?> = mapOf(
        "est" to i.minutosEstimado, "min" to i.minutosMinimo, "max" to i.minutosMaximo, "costo" to i.costo,
        "tramos" to i.tramos.map(::tramo)
    )

    private fun tramo(t: Tramo): Map<String, Any?> = mapOf(
        "tipo" to t.tipo.name,
        "desde" to punto(t.desde), "hasta" to punto(t.hasta),
        "min" to t.minutos, "m" to t.metros,
        "ruta" to t.codigoRuta, "sentido" to t.sentido?.name,
        "espera" to t.esperaMin, "intervalo" to t.intervaloMin,
        "alt" to t.alternativas,
        // Recorrido como [lat, lng, lat, lng, …] para ocupar menos
        "rec" to t.recorrido.flatMap { listOf(redondear(it.lat), redondear(it.lng)) }
    )

    private fun punto(c: Coordenada) = listOf(redondear(c.lat), redondear(c.lng))
    private fun redondear(v: Double) = Math.round(v * 1e6) / 1e6

    private fun leerItinerario(m: Map<*, *>) = Itinerario(
        tramos = (m["tramos"] as List<*>).map { leerTramo(it as Map<*, *>) },
        minutosEstimado = num(m["est"]), minutosMinimo = num(m["min"]), minutosMaximo = num(m["max"]),
        costo = num(m["costo"])
    )

    private fun leerTramo(m: Map<*, *>): Tramo {
        val rec = (m["rec"] as? List<*>).orEmpty().map { num(it) }
        return Tramo(
            tipo = TipoTramo.valueOf(m["tipo"] as String),
            desde = leerPunto(m["desde"]), hasta = leerPunto(m["hasta"]),
            minutos = num(m["min"]), metros = num(m["m"]),
            codigoRuta = m["ruta"] as? String,
            sentido = (m["sentido"] as? String)?.let { Sentido.valueOf(it) },
            esperaMin = num(m["espera"]), intervaloMin = num(m["intervalo"]),
            alternativas = (m["alt"] as? List<*>).orEmpty().map { it as String },
            recorrido = rec.chunked(2).filter { it.size == 2 }.map { Coordenada(it[0], it[1]) }
        )
    }

    private fun leerPunto(v: Any?): Coordenada {
        val l = v as List<*>
        return Coordenada(num(l[0]), num(l[1]))
    }

    private fun num(v: Any?): Double = (v as Number).toDouble()
}
