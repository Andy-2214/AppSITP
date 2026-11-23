package com.sitp.arequipa.domain.model

import java.util.Locale

/** Punto geográfico (grados decimales). Modelo propio: no depende de ningún SDK de mapas. */
data class Coordenada(val lat: Double, val lng: Double) {

    /**
     * Texto con el que el historial guarda el punto ("-16.3971, -71.5371"). "Repetir búsqueda" y los
     * favoritos lo vuelven a leer con [desdeTexto], así que siempre usa punto decimal (Locale.US).
     */
    fun comoTexto(): String = String.format(Locale.US, "%.4f, %.4f", lat, lng)

    companion object {
        /**
         * Lee "lat, lng". También acepta el formato con coma decimal que guardaban algunos teléfonos
         * en español ("-16,3971, -71,5371"). Devuelve null si el texto no es un punto válido.
         */
        fun desdeTexto(texto: String): Coordenada? {
            val partes = texto.split(",").map { it.trim() }
            val (lat, lng) = when (partes.size) {
                2 -> partes[0] to partes[1]
                4 -> "${partes[0]}.${partes[1]}" to "${partes[2]}.${partes[3]}"
                else -> return null
            }
            val la = lat.toDoubleOrNull() ?: return null
            val ln = lng.toDoubleOrNull() ?: return null
            return if (la in -90.0..90.0 && ln in -180.0..180.0) Coordenada(la, ln) else null
        }
    }
}
