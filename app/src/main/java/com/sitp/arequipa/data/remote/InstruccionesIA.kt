package com.sitp.arequipa.data.remote

import com.sitp.arequipa.data.json.Json
import com.sitp.arequipa.domain.model.ConsultaInterpretada
import com.sitp.arequipa.domain.model.LugarPedido
import com.sitp.arequipa.domain.model.ModoBusqueda

/**
 * Instrucciones fijas de la IA y lectura de sus respuestas (ADR-011). Kotlin puro: se prueba sin Android.
 * Las reglas viven en el código y no en Firestore para que nadie pueda cambiar, sin querer, el formato
 * que la app espera. El panel solo puede agregar contexto de la ciudad (campo `contexto_interprete`).
 */
object InstruccionesIA {

    val INTERPRETAR = """
        Eres el intérprete de consultas de una app de rutas de combis de Arequipa (Perú).
        Tu única tarea es leer la frase del usuario y devolver un objeto JSON. NO calculas rutas, combis, tiempos ni costos.

        Formato exacto:
        {"origen":{"texto":"...","referencia":null},"destino":{"texto":"...","referencia":null},"preferencia":null,"restriccionesNoSoportadas":[]}

        Reglas:
        - "texto": el nombre del lugar tal como lo escribió el usuario (solo corrige tildes o mayúsculas evidentes). No inventes lugares ni agregues distritos que el usuario no dijo.
        - Si el usuario sale desde donde está ("desde aquí", "estoy acá", "desde donde estoy") usa "${LugarPedido.MI_UBICACION}" como texto del origen.
        - Si no menciona el origen o el destino, pon null en ese campo.
        - "referencia": otro lugar que el usuario da para ubicar el sitio ("cerca del colegio X", "frente al parque Y"); si no hay, null.
        - "preferencia": "tiempo" si pide llegar rápido, "costo" si pide gastar menos o tomar menos combis; si no dice nada, null.
        - "restriccionesNoSoportadas": frases cortas con pedidos que la app no puede cumplir, como pasar por un lugar, evitar una zona, horarios, comodidad o usar taxi. Si no hay, [].
        - Responde solo con el JSON, sin texto adicional.
    """.trimIndent()

    val ELEGIR_LUGAR = """
        Eres el asistente de una app de rutas de combis de Arequipa (Perú).
        El usuario escribió un lugar y el catálogo de la app tiene varios candidatos con nombre parecido.
        Elige el candidato que mejor corresponde a lo que escribió el usuario. No inventes lugares.
        Responde solo con un JSON: {"indice": n}, donde n es el número del candidato (empieza en 0), o {"indice": null} si ninguno corresponde.
    """.trimIndent()

    fun sistemaInterpretar(contextoCiudad: String?): String =
        if (contextoCiudad.isNullOrBlank()) INTERPRETAR else "$INTERPRETAR\n\nContexto adicional de la ciudad:\n$contextoCiudad"

    fun usuarioElegir(textoUsuario: String, candidatos: List<String>): String =
        "Lo que escribió el usuario: \"$textoUsuario\"\nCandidatos:\n" +
            candidatos.mapIndexed { i, c -> "$i. $c" }.joinToString("\n")

    /** Lee la respuesta de [INTERPRETAR]. Devuelve null si no es el JSON esperado. */
    fun leerInterpretacion(respuesta: String): ConsultaInterpretada? {
        val raiz = Json.leerPrimerObjeto(respuesta) ?: return null
        if (!raiz.containsKey("origen") && !raiz.containsKey("destino")) return null
        val preferencia = when ((raiz["preferencia"] as? String)?.lowercase()) {
            "tiempo" -> ModoBusqueda.TIEMPO
            "costo" -> ModoBusqueda.COSTO
            else -> null
        }
        val restricciones = (raiz["restriccionesNoSoportadas"] as? List<*>)
            ?.mapNotNull { (it as? String)?.trim()?.takeIf { s -> s.isNotEmpty() } }
            .orEmpty()
        return ConsultaInterpretada(lugar(raiz["origen"]), lugar(raiz["destino"]), preferencia, restricciones)
    }

    /** Lee la respuesta de [ELEGIR_LUGAR]: el índice elegido o null. */
    fun leerIndice(respuesta: String, cantidad: Int): Int? {
        val raiz = Json.leerPrimerObjeto(respuesta) ?: return null
        val n = (raiz["indice"] as? Number)?.toInt() ?: return null
        return n.takeIf { it in 0 until cantidad }
    }

    private fun lugar(valor: Any?): LugarPedido? {
        val texto: String?
        val referencia: String?
        when (valor) {
            is Map<*, *> -> {
                texto = (valor["texto"] as? String)?.trim()
                referencia = (valor["referencia"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
            }
            is String -> { texto = valor.trim(); referencia = null }
            else -> return null
        }
        if (texto.isNullOrEmpty() || texto.equals("null", ignoreCase = true)) return null
        return LugarPedido(texto, referencia)
    }
}
