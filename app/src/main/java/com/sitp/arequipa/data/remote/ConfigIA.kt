package com.sitp.arequipa.data.remote

import com.sitp.arequipa.BuildConfig

/**
 * Configuración del proveedor de IA (ADR-011). **Es el único lugar que hay que tocar para cambiar de
 * modelo o de proveedor**: cualquier servicio con una API de chat compatible con la de OpenAI sirve
 * (cambiar [ACTUAL]). El nombre del proveedor va aquí como dato, no en los nombres de las clases (ADR-007).
 *
 * @property extras parámetros propios del modelo que se agregan tal cual al pedido.
 */
data class ConfigIA(
    val urlChat: String,
    val modelo: String,
    val clave: String,
    val extras: Map<String, Any> = emptyMap(),
    val maxTokens: Int = 600,
    val temperatura: Double = 0.0
) {
    companion object {
        /** Proveedor y modelo en uso. La clave viene de gradle.properties (GROQ_API_KEY). */
        val ACTUAL = ConfigIA(
            urlChat = "https://api.groq.com/openai/v1/chat/completions",
            modelo = "openai/gpt-oss-20b",
            clave = BuildConfig.GROQ_API_KEY,
            // gpt-oss es un modelo de razonamiento: poco esfuerzo (tarea simple) y sin devolver el razonamiento
            extras = mapOf("reasoning_effort" to "low", "include_reasoning" to false)
        )
    }
}
