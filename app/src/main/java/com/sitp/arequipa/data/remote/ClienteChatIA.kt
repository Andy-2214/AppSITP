package com.sitp.arequipa.data.remote

import com.sitp.arequipa.data.json.Json
import com.sitp.arequipa.domain.repository.AsistenteNoDisponibleException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente genérico de una API de chat compatible con la de OpenAI (`/chat/completions`).
 * No sabe qué proveedor ni qué modelo usa: todo viene en [ConfigIA].
 */
class ClienteChatIA(private val config: ConfigIA = ConfigIA.ACTUAL) {

    /**
     * Envía un mensaje de sistema y uno del usuario y devuelve el texto de la respuesta.
     * Con [soloJson] pide al modelo responder en modo JSON; si el proveedor no lo admite, reintenta sin él.
     * @throws AsistenteNoDisponibleException con un mensaje apto para el usuario.
     */
    suspend fun completar(sistema: String, usuario: String, soloJson: Boolean = true): String =
        withContext(Dispatchers.IO) {
            try {
                val (codigo, cuerpo) = enviar(sistema, usuario, soloJson)
                when {
                    codigo == 200 -> leerContenido(cuerpo)
                    codigo == 400 && soloJson -> {
                        val (codigo2, cuerpo2) = enviar(sistema, usuario, false)
                        if (codigo2 == 200) leerContenido(cuerpo2) else throw errorPorCodigo(codigo2, cuerpo2)
                    }
                    else -> throw errorPorCodigo(codigo, cuerpo)
                }
            } catch (e: AsistenteNoDisponibleException) {
                throw e
            } catch (e: IOException) {
                throw AsistenteNoDisponibleException("El chat con IA necesita internet. Puedes ubicar los puntos moviendo el mapa o con «Buscar lugar».")
            }
        }

    private fun enviar(sistema: String, usuario: String, soloJson: Boolean): Pair<Int, String> {
        val cuerpo = LinkedHashMap<String, Any?>().apply {
            put("model", config.modelo)
            put("messages", listOf(
                mapOf("role" to "system", "content" to sistema),
                mapOf("role" to "user", "content" to usuario)
            ))
            put("temperature", config.temperatura)
            put("max_completion_tokens", config.maxTokens)
            if (soloJson) put("response_format", mapOf("type" to "json_object"))
            putAll(config.extras)
        }
        val conexion = URL(config.urlChat).openConnection() as HttpURLConnection
        try {
            conexion.connectTimeout = 15_000
            conexion.readTimeout = 30_000
            conexion.requestMethod = "POST"
            conexion.setRequestProperty("Content-Type", "application/json")
            conexion.setRequestProperty("Authorization", "Bearer ${config.clave}")
            conexion.doOutput = true
            OutputStreamWriter(conexion.outputStream, Charsets.UTF_8).use { it.write(Json.escribir(cuerpo)) }
            val codigo = conexion.responseCode
            val flujo = if (codigo in 200..299) conexion.inputStream else conexion.errorStream
            val texto = flujo?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return codigo to texto
        } finally {
            conexion.disconnect()
        }
    }

    private fun leerContenido(cuerpo: String): String {
        @Suppress("UNCHECKED_CAST")
        val raiz = try { Json.leer(cuerpo) as? Map<String, Any?> } catch (e: IllegalArgumentException) { null }
        val opciones = raiz?.get("choices") as? List<*>
        val mensaje = (opciones?.firstOrNull() as? Map<*, *>)?.get("message") as? Map<*, *>
        val contenido = mensaje?.get("content") as? String
        if (contenido.isNullOrBlank()) throw AsistenteNoDisponibleException("La IA no devolvió una respuesta. Intenta de nuevo.")
        return contenido
    }

    private fun errorPorCodigo(codigo: Int, cuerpo: String): AsistenteNoDisponibleException {
        println("DEBUG ClienteChatIA: respuesta $codigo: ${cuerpo.take(300)}")
        return AsistenteNoDisponibleException(
            if (codigo == 429) "La IA está saturada, espera unos segundos e intenta de nuevo."
            else "La IA no está disponible ahora (error $codigo). Puedes ubicar los puntos moviendo el mapa."
        )
    }
}
