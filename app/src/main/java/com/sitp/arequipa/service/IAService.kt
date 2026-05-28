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
        consultaExtra: String = ""
    ): String {
        val promptDoc = db.collection("prompt_ia")
            .document("config")
            .get()
            .await()
        val promptBase = promptDoc.getString("texto") ?:
        "Eres un asistente de transporte público de Arequipa."

        val rutasTexto = rutas.joinToString("\n") { ruta ->
            "- Código: ${ruta["codigo"]}, Nombre: ${ruta["nombre"]}, " +
                    "Empresa: ${ruta["empresa"]}, Avenidas: ${ruta["avenidas"]}"
        }

        val instruccionPreferencia = when (preferencia) {
            "tiempo"      -> "Prioriza MENOS tiempo de viaje aunque haya más transbordos"
            "costo"       -> "Prioriza MENOS transbordos para ahorrar dinero (S/1.30 por combi)"
            "transbordos" -> "Prioriza la ruta con el MENOR número de transbordos posible"
            else          -> "Prioriza MENOS tiempo de viaje"
        }

        val seccionExtra = if (consultaExtra.isNotBlank()) {
            "\nInformación adicional del usuario: $consultaExtra"
        } else ""

        val promptCompleto = """
$promptBase

Rutas disponibles:
$rutasTexto

Usuario está en: $origen
Quiere llegar a: $destino
Preferencia: $instruccionPreferencia
$seccionExtra

Reglas adicionales:
- Todas las combis cobran S/1.30 por pasaje
- NUNCA sugieras taxi, mototaxi u otro medio que no sea combi/bus de la lista
- Si no es posible llegar con las rutas disponibles, dilo claramente
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
                    put("model", "llama-3.3-70b-versatile")
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    })
                    put("max_tokens", 800)
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
                val url = "https://nominatim.openstreetmap.org/reverse" +
                        "?lat=$lat&lon=$lng&format=json&zoom=16&addressdetails=1"
                val connection = java.net.URL(url).openConnection()
                        as java.net.HttpURLConnection
                connection.setRequestProperty("User-Agent", "SistemaTransporteArequipa/1.0")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                val response = connection.inputStream.bufferedReader().readText()
                val json = org.json.JSONObject(response)
                val address = json.getJSONObject("address")

                // Construir nombre compuesto más específico
                val barrio = address.optString("suburb")
                    .ifEmpty { address.optString("neighbourhood") }
                    .ifEmpty { address.optString("quarter") }
                    .ifEmpty { address.optString("city_district") }

                val via = address.optString("road")
                    .ifEmpty { address.optString("pedestrian") }

                val resultado = when {
                    barrio.isNotEmpty() && via.isNotEmpty() -> "$via, $barrio"
                    barrio.isNotEmpty() -> barrio
                    via.isNotEmpty() -> via
                    else -> "zona de Arequipa"
                }

                println("DEBUG nominatim resultado: $resultado")
                resultado

            } catch (e: Exception) {
                println("DEBUG nominatim error: ${e.message}")
                "zona de Arequipa"
            }
        }
    }
}