package com.sitp.arequipa.data.remote

import com.sitp.arequipa.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Fuente de datos para geocodificación inversa (coordenadas → nombre de calle/distrito).
 * Separada de GroqAISource para respetar el principio de responsabilidad única (SRP).
 */
class GeocodingSource {

    suspend fun coordenadasANombre(lat: Double, lng: Double): String {
        return withContext(Dispatchers.IO) {
            try {
                val apiKey = BuildConfig.GOOGLE_MAPS_API_KEY
                val url = "https://maps.googleapis.com/maps/api/geocode/json" +
                        "?latlng=$lat,$lng&key=$apiKey&language=es&region=pe"
                val connection = java.net.URL(url).openConnection()
                        as java.net.HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                val response = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                val results = json.getJSONArray("results")

                if (results.length() == 0) return@withContext "zona de Arequipa"

                fun extraerComponente(result: JSONObject, tipos: List<String>): String? {
                    val components = result.getJSONArray("address_components")
                    for (j in 0 until components.length()) {
                        val comp = components.getJSONObject(j)
                        val compTypes = comp.getJSONArray("types")
                        val compTypesList = (0 until compTypes.length()).map { compTypes.getString(it) }
                        if (tipos.any { it in compTypesList }) {
                            return comp.getString("long_name")
                        }
                    }
                    return null
                }

                val prefijosAvenida = listOf("av.", "avenida", "jr.", "jirón", "calle", "pasaje")
                var mejorAvenida: String? = null
                var mejorDistrito: String? = null

                for (i in 0 until results.length()) {
                    val result = results.getJSONObject(i)
                    val calle = extraerComponente(result, listOf("route"))
                    val distrito = extraerComponente(
                        result,
                        listOf("sublocality_level_1", "neighborhood", "administrative_area_level_3")
                    )

                    if (calle != null && mejorAvenida == null) {
                        mejorAvenida = calle
                    }
                    if (calle != null &&
                        prefijosAvenida.any { calle.contains(it, ignoreCase = true) } &&
                        calle.contains("av", ignoreCase = true)
                    ) {
                        mejorAvenida = calle
                    }
                    if (distrito != null && mejorDistrito == null) {
                        mejorDistrito = distrito
                    }
                }

                val partes = listOfNotNull(mejorAvenida, mejorDistrito).distinct()
                if (partes.isNotEmpty()) {
                    val nombre = partes.take(2).joinToString(", ")
                    println("DEBUG geocoding resultado: $nombre (lat=$lat, lng=$lng)")
                    return@withContext nombre
                }

                val fallback = results.getJSONObject(0)
                    .getString("formatted_address")
                    .split(",")
                    .map { it.trim() }
                    .filterNot { it.equals("Perú", ignoreCase = true) }
                    .filterNot { it.equals("Arequipa", ignoreCase = true) }
                    .filterNot { it.matches(Regex("\\d{5}")) }
                    .filterNot { it.matches(Regex("\\d+")) }
                    .filterNot { it.isBlank() }
                    .take(2).joinToString(", ").trim()

                println("DEBUG geocoding fallback: $fallback")
                fallback.ifEmpty { "zona de Arequipa" }

            } catch (e: Exception) {
                println("DEBUG geocoding error: ${e.message}")
                "zona de Arequipa"
            }
        }
    }
}
