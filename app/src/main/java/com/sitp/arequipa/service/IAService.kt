package com.sitp.arequipa.service

import com.google.firebase.firestore.FirebaseFirestore
import com.sitp.arequipa.BuildConfig
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class IAService {

    private val db = FirebaseFirestore.getInstance()

    suspend fun recomendarRuta(
        origen: String,
        destino: String,
        preferencia: String,
        rutas: List<Map<String, Any>>,
        consultaExtra: String = "",
        rutasOrigen: List<String> = emptyList(),
        rutasDestino: List<String> = emptyList(),
        esDirecta: Boolean = false
    ): String {
        val promptDoc = db.collection("prompt_ia")
            .document("config")
            .get()
            .await()
        val promptBase = promptDoc.getString("texto") ?:
        "Eres un asistente de transporte público de Arequipa."

        // Una línea por ruta para ahorrar tokens
        val rutasTexto = rutas.joinToString("\n") { ruta ->
            val ida = ruta["avenidas"]?.toString() ?: ""
            val vuelta = ruta["avenidaVuelta"]?.toString() ?: ""
            if (vuelta.isNotBlank() && vuelta != ida) {
                "${ruta["codigo"]}: $ida / $vuelta"
            } else {
                "${ruta["codigo"]}: $ida"
            }
        }

        val instruccionPreferencia = when (preferencia) {
            "tiempo"      -> "Prioriza MENOS tiempo de viaje aunque haya más transbordos"
            "costo"       -> "Prioriza MENOS transbordos para ahorrar dinero (S/1.30 por combi)"
            else          -> "Prioriza MENOS tiempo de viaje"
        }

        val seccionExtra = if (consultaExtra.isNotBlank()) {
            "\nInformación adicional del usuario: $consultaExtra"
        } else ""

        val seccionDirecta = if (esDirecta) {
            """

⚠️ RUTA DIRECTA DISPONIBLE:
El sistema GPS confirmó que hay una ruta que pasa cerca de AMBOS puntos.
USA SOLO 1 RUTA — responde con un único paso numerado.
NUNCA agregues una segunda ruta si hay una directa disponible."""
        } else ""

        val reglaDirecta = if (preferencia == "costo") {
            "Si una ruta aparece en ambos grupos es DIRECTA, recomiéndala como único paso obligatoriamente para ahorrar dinero."
        } else {
            "Si una ruta aparece en ambos grupos es DIRECTA. Sin embargo, prioriza el tiempo total de viaje; si una combinación de 2 o 3 rutas es más rápida que la ruta directa, prefiere la combinación."
        }

        val seccionProximidad = if (rutasOrigen.isNotEmpty() || rutasDestino.isNotEmpty()) {
            """

DATOS DE PROXIMIDAD GEOGRÁFICA (calculados por GPS, son exactos, ordenados de más cercana a más lejana):
- Rutas que pasan cerca del ORIGEN ($origen): ${if (rutasOrigen.isEmpty()) "ninguna directa" else rutasOrigen.joinToString(", ")}
- Rutas que pasan cerca del DESTINO ($destino): ${if (rutasDestino.isEmpty()) "ninguna directa" else rutasDestino.joinToString(", ")}
- REGLA 1: El usuario DEBE abordar una ruta del grupo ORIGEN (prioriza enormemente las primeras de la lista por estar físicamente más cerca) y bajar cerca del DESTINO (prioriza las primeras de la lista).
- REGLA 2: $reglaDirecta"""
        } else ""

        val promptCompleto = """
$promptBase
$seccionDirecta
$seccionProximidad
 
Itinerarios de las rutas candidatas (úsalos para confirmar sentido del viaje):
$rutasTexto
 
Usuario está en: $origen
Quiere llegar a: $destino
Preferencia: $instruccionPreferencia
$seccionExtra
 
REGLAS OBLIGATORIAS:
- Todas las combis cobran S/1.30 por pasaje
- NUNCA sugieras taxi, mototaxi u otro medio que no sea combi/bus de la lista
- Si no es posible llegar con las rutas disponibles, dilo claramente
- DEBES responder SIEMPRE en el formato exacto de abajo, sin excepciones
- NO agregues explicaciones, saludos ni texto extra fuera del formato
 
FORMATO DE RESPUESTA OBLIGATORIO:
 
RUTAS: [CODIGO1, CODIGO2, CODIGO3]
 
1. Tome la combi CODIGO1 en [avenida de abordaje], cerca de [referencia], y bájese en [avenida de bajada], cerca de [referencia]
2. Tome la combi CODIGO2 en [avenida de abordaje], cerca de [referencia], y bájese en [avenida de bajada], cerca de [referencia]
3. Tome la combi CODIGO3 en [avenida de abordaje], cerca de [referencia], y bájese en [avenida de bajada], cerca de [referencia]
 
ESTIMACION: X minutos aproximadamente
COSTO TOTAL: S/X.XX (X combis x S/1.30)
 
Puedes sugerir 1, 2, 3 o más combis (pasos numerados) dependiendo de cuántos transbordos se necesiten para hacer la ruta más rápida.
Si no hay ruta posible, escribe solo: "No es posible llegar con las rutas disponibles desde [origen] hasta [destino]."
""".trimIndent()

        return llamarGroq(promptCompleto)
    }

    private suspend fun llamarGroq(prompt: String): String {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val url = URL("https://api.groq.com/openai/v1/chat/completions")
                val connection = url.openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer ${BuildConfig.GROQ_API_KEY}")
                connection.doOutput = true

                val body = JSONObject().apply {
                    put("model", "llama-3.1-8b-instant")
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                    put("max_tokens", 400)  // respuesta corta y formateada
                    put("temperature", 0.1)
                }.toString()

                OutputStreamWriter(connection.outputStream).use { it.write(body) }

                val responseCode = connection.responseCode
                println("DEBUG Groq responseCode: $responseCode")

                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                } else if (responseCode == 429) {
                    "La IA está saturada, espera unos segundos e intenta de nuevo."
                } else {
                    val error = connection.errorStream?.bufferedReader()?.readText()
                    println("DEBUG Groq error: $error")
                    "Error $responseCode: $error"
                }
            } catch (e: Exception) {
                println("DEBUG Groq exception: ${e.message}")
                "Error: ${e.message}"
            }
        }
    }

    suspend fun coordenadasANombre(lat: Double, lng: Double): String {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val apiKey = BuildConfig.GOOGLE_MAPS_API_KEY
                val url = "https://maps.googleapis.com/maps/api/geocode/json" +
                        "?latlng=$lat,$lng&key=$apiKey&language=es&region=pe"
                val connection = java.net.URL(url).openConnection()
                        as java.net.HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                val response = connection.inputStream.bufferedReader().readText()
                val json = org.json.JSONObject(response)
                val results = json.getJSONArray("results")

                if (results.length() == 0) return@withContext "zona de Arequipa"

                fun extraerComponente(result: org.json.JSONObject, tipos: List<String>): String? {
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
                    val distrito = extraerComponente(result,
                        listOf("sublocality_level_1", "neighborhood", "administrative_area_level_3"))

                    if (calle != null && mejorAvenida == null) {
                        mejorAvenida = calle
                    }
                    if (calle != null &&
                        prefijosAvenida.any { calle.contains(it, ignoreCase = true) } &&
                        calle.contains("av", ignoreCase = true)) {
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
                    .filterNot { it.matches(Regex("\\d{5}")) }         // códigos postales
                    .filterNot { it.matches(Regex("\\d+")) }           // números sueltos
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