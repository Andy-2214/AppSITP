package com.sitp.arequipa.domain.ruteo

/**
 * Lee el campo `frecuencia` que carga el panel web y lo convierte en minutos entre combis.
 *
 * Acepta «10», «8-12» (se toma el promedio: 10), «cada 10 min», «8 a 12 minutos» y «cada 1 hora».
 * Devuelve null si el campo está vacío o no se entiende; el motor usa entonces el valor por defecto.
 */
object Frecuencia {
    private val NUMERO = Regex("""\d+(?:[.,]\d+)?""")
    private const val MINIMO = 1.0
    private const val MAXIMO = 120.0

    fun minutos(texto: String?): Double? {
        if (texto.isNullOrBlank()) return null
        val numeros = NUMERO.findAll(texto).map { it.value.replace(',', '.').toDouble() }.take(2).toList()
        if (numeros.isEmpty()) return null
        var valor = numeros.average()
        val t = texto.lowercase()
        if (t.contains("hora") && !t.contains("min")) valor *= 60.0
        return valor.takeIf { it in MINIMO..MAXIMO }
    }
}
