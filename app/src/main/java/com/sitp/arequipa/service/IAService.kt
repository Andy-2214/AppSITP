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
        rutas: List<Map<String, Any>>
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

        val promptCompleto = """
    $promptBase
    
    INFORMACIÓN IMPORTANTE:
    - Todas las combis en Arequipa cobran S/1.30 por pasaje
    - Si el usuario necesita transbordar, cada combi adicional cuesta S/1.30
    - Las respuestas deben ser cortas no tan largas
    
    Rutas disponibles (con sus avenidas de recorrido):
    $rutasTexto
    
    El usuario está en: $origen
    Quiere llegar a: $destino
    Preferencia: $preferencia
    
    INSTRUCCIONES PARA RECOMENDAR:
    - Analiza qué rutas pasan cerca del origen Y del destino
    - Una ruta "pasa cerca" si sus avenidas están en la misma zona
    - Si ninguna ruta va directo, busca combinaciones con transbordo
    - ${if (preferencia == "tiempo")
            "Prioriza MENOS tiempo aunque haya más transbordos (más pasajes)"
        else
            "Prioriza MENOS transbordos para ahorrar dinero (S/1.30 por combi)"}
    
    Responde EXACTAMENTE en este formato:
    RUTAS: [código1, código2]
    INSTRUCCIONES:
    1. [paso 1]
    2. [paso 2]
    ESTIMACION: X minutos, S/X.XX (X pasaje(s) x S/1.30)
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
                val url = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lng&format=json"
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.setRequestProperty("User-Agent", "SistemaTransporteArequipa/1.0")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                val response = connection.inputStream.bufferedReader().readText()
                println("DEBUG nominatim response: $response")
                val json = org.json.JSONObject(response)
                val address = json.getJSONObject("address")
                address.optString("suburb")
                    .ifEmpty { address.optString("neighbourhood") }
                    .ifEmpty { address.optString("road") }
                    .ifEmpty { address.optString("city_district") }
                    .ifEmpty { "zona de Arequipa" }
            } catch (e: Exception) {
                println("DEBUG nominatim error: ${e.message}")
                "zona de Arequipa"
            }
        }
    }
}