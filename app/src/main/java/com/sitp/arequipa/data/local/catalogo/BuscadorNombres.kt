package com.sitp.arequipa.data.local.catalogo

import com.sitp.arequipa.domain.model.Coordenada
import com.sitp.arequipa.domain.ruteo.Geo
import java.text.Normalizer

/**
 * Búsqueda por nombre sobre los lugares y calles del catálogo, en memoria y sin internet (ADR-011).
 * Kotlin puro: se prueba sin Android. Ignora tildes, mayúsculas y signos, y entiende abreviaturas
 * comunes («av.», «jr.», «mcdo.»).
 *
 * Orden: exacto > empieza igual > todas las palabras al inicio de palabras del nombre > contiene el texto
 * > contiene todas las palabras. Luego los lugares de referencia (mercados, colegios…), la cercanía a
 * [buscar]`(cerca)` y el nombre más corto.
 */
class BuscadorNombres(entradas: List<Entrada>) {

    data class Entrada(val nombre: String, val lat: Double, val lng: Double, val tipo: String, val esCalle: Boolean)
    data class Resultado(val entrada: Entrada, val puntaje: Int, val distanciaM: Double?)

    private val lista = entradas
    private val normalizados = Array(entradas.size) { normalizar(entradas[it].nombre) }
    private val palabras = Array(entradas.size) { normalizados[it].split(' ').filter { p -> p.isNotEmpty() } }

    fun buscar(texto: String, cerca: Coordenada?, limite: Int): List<Resultado> {
        val consulta = normalizar(texto)
        if (consulta.isEmpty()) return emptyList()
        val todas = consulta.split(' ').filter { it.isNotEmpty() }
        val utiles = todas.filter { it !in PALABRAS_VACIAS }.ifEmpty { todas }

        val encontrados = ArrayList<Resultado>()
        for (i in lista.indices) {
            val nombre = normalizados[i]
            val base = when {
                nombre == consulta -> 100
                nombre.startsWith(consulta) -> 85
                utiles.all { u -> palabras[i].any { it.startsWith(u) } } -> 70
                nombre.contains(consulta) -> 55
                utiles.all { nombre.contains(it) } -> 45
                else -> continue
            }
            val e = lista[i]
            val bonus = when {
                e.tipo in CatalogoLugaresSource.TIPOS_REFERENCIA -> 8
                e.esCalle && !CatalogoLugaresSource.esNombreGenerico(e.nombre) -> 4
                else -> 0
            }
            val distancia = cerca?.let { Geo.metros(it.lat, it.lng, e.lat, e.lng) }
            encontrados.add(Resultado(e, base + bonus, distancia))
        }

        val orden = encontrados.sortedWith(
            compareByDescending<Resultado> { it.puntaje }
                .thenBy { it.distanciaM ?: 0.0 }
                .thenBy { it.entrada.nombre.length }
                .thenBy { it.entrada.nombre }
        )
        // Un mismo lugar suele estar varias veces (nodo y área, o calle partida): uno por nombre cada 300 m
        val resultado = ArrayList<Resultado>()
        for (r in orden) {
            val repetido = resultado.any {
                normalizar(it.entrada.nombre) == normalizar(r.entrada.nombre) &&
                    Geo.metros(it.entrada.lat, it.entrada.lng, r.entrada.lat, r.entrada.lng) < DISTANCIA_DUPLICADO_M
            }
            if (!repetido) resultado.add(r)
            if (resultado.size >= limite) break
        }
        return resultado
    }

    companion object {
        private const val DISTANCIA_DUPLICADO_M = 300.0
        private val PALABRAS_VACIAS = setOf("de", "del", "la", "el", "los", "las", "y", "en", "al", "a")
        private val ABREVIATURAS = mapOf(
            "av" to "avenida", "avda" to "avenida", "jr" to "jiron", "psje" to "pasaje", "pje" to "pasaje",
            "ca" to "calle", "cl" to "calle", "urb" to "urbanizacion", "mcdo" to "mercado", "merc" to "mercado",
            "univ" to "universidad", "col" to "colegio", "hosp" to "hospital", "pq" to "parque", "pque" to "parque",
            "sta" to "santa", "sto" to "santo", "pza" to "plaza", "cc" to "centro comercial"
        )
        private val DIACRITICOS = Regex("\\p{Mn}+")
        private val NO_ALFANUMERICO = Regex("[^a-z0-9ñ ]")
        private val ESPACIOS = Regex(" +")

        /** «Av. Ejército» → «avenida ejercito». */
        fun normalizar(texto: String): String {
            val sinTildes = Normalizer.normalize(texto.lowercase().replace('ñ', '\u0001'), Normalizer.Form.NFD)
                .replace(DIACRITICOS, "")
                .replace('\u0001', 'ñ')
            val limpio = sinTildes.replace(NO_ALFANUMERICO, " ").replace(ESPACIOS, " ").trim()
            return limpio.split(' ').joinToString(" ") { ABREVIATURAS[it] ?: it }
        }
    }
}
