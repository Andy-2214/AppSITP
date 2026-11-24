package com.sitp.arequipa.data.json

/**
 * Lector y escritor de JSON mínimo en Kotlin puro (sin Android ni librerías nuevas), para leer las
 * respuestas de la IA y guardar la caché de búsquedas. Se puede probar con pruebas unitarias.
 *
 * Tipos: objeto → Map<String, Any?> · arreglo → List<Any?> · texto → String · número → Double ·
 * true/false → Boolean · null → null.
 */
object Json {

    /** @throws IllegalArgumentException si el texto no es JSON válido. */
    fun leer(texto: String): Any? {
        val lector = Lector(texto)
        lector.espacios()
        val valor = lector.valor()
        lector.espacios()
        require(lector.fin()) { "Sobra texto después del JSON (posición ${lector.i})" }
        return valor
    }

    /** Busca el primer objeto `{…}` dentro de un texto (la IA a veces lo envuelve en ``` o en una frase). */
    fun leerPrimerObjeto(texto: String): Map<String, Any?>? {
        val inicio = texto.indexOf('{')
        if (inicio < 0) return null
        var profundidad = 0
        var enTexto = false
        var escape = false
        for (i in inicio until texto.length) {
            val c = texto[i]
            if (enTexto) {
                when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> enTexto = false
                }
                continue
            }
            when (c) {
                '"' -> enTexto = true
                '{' -> profundidad++
                '}' -> {
                    profundidad--
                    if (profundidad == 0) {
                        @Suppress("UNCHECKED_CAST")
                        return try { leer(texto.substring(inicio, i + 1)) as? Map<String, Any?> } catch (e: IllegalArgumentException) { null }
                    }
                }
            }
        }
        return null
    }

    fun escribir(valor: Any?): String = StringBuilder().also { escribirEn(it, valor) }.toString()

    private fun escribirEn(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> texto(sb, v)
            is Boolean -> sb.append(v)
            is Int, is Long -> sb.append(v)
            is Number -> {
                val d = v.toDouble()
                require(!d.isNaN() && !d.isInfinite()) { "Número no válido en JSON: $d" }
                if (d == Math.rint(d) && Math.abs(d) < 1e15) sb.append(d.toLong()) else sb.append(d)
            }
            is Map<*, *> -> {
                sb.append('{')
                var primero = true
                for ((k, valor) in v) {
                    if (!primero) sb.append(',')
                    primero = false
                    texto(sb, k.toString()); sb.append(':'); escribirEn(sb, valor)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var primero = true
                for (e in v) {
                    if (!primero) sb.append(',')
                    primero = false
                    escribirEn(sb, e)
                }
                sb.append(']')
            }
            else -> texto(sb, v.toString())
        }
    }

    private fun texto(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        sb.append('"')
    }

    private class Lector(private val s: String) {
        var i = 0

        fun fin() = i >= s.length
        fun espacios() { while (i < s.length && s[i].isWhitespace()) i++ }

        fun valor(): Any? {
            espacios()
            require(i < s.length) { "JSON incompleto" }
            return when (val c = s[i]) {
                '{' -> objeto()
                '[' -> arreglo()
                '"' -> cadena()
                't' -> palabra("true", true)
                'f' -> palabra("false", false)
                'n' -> palabra("null", null)
                else -> if (c == '-' || c.isDigit()) numero() else throw IllegalArgumentException("Carácter inesperado '$c' en $i")
            }
        }

        private fun palabra(p: String, v: Any?): Any? {
            require(s.startsWith(p, i)) { "Se esperaba $p en $i" }
            i += p.length
            return v
        }

        private fun numero(): Double {
            val inicio = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            return s.substring(inicio, i).toDoubleOrNull() ?: throw IllegalArgumentException("Número inválido en $inicio")
        }

        private fun cadena(): String {
            i++ // comilla inicial
            val sb = StringBuilder()
            while (true) {
                require(i < s.length) { "Texto sin cerrar" }
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        require(i < s.length) { "Escape incompleto" }
                        when (val e = s[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                require(i + 4 <= s.length) { "Escape \\u incompleto" }
                                sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4
                            }
                            else -> throw IllegalArgumentException("Escape desconocido \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun arreglo(): List<Any?> {
            i++
            val lista = ArrayList<Any?>()
            espacios()
            if (i < s.length && s[i] == ']') { i++; return lista }
            while (true) {
                lista.add(valor())
                espacios()
                require(i < s.length) { "Arreglo sin cerrar" }
                when (s[i++]) {
                    ',' -> continue
                    ']' -> return lista
                    else -> throw IllegalArgumentException("Se esperaba , o ] en ${i - 1}")
                }
            }
        }

        private fun objeto(): Map<String, Any?> {
            i++
            val mapa = LinkedHashMap<String, Any?>()
            espacios()
            if (i < s.length && s[i] == '}') { i++; return mapa }
            while (true) {
                espacios()
                require(i < s.length && s[i] == '"') { "Se esperaba una clave en $i" }
                val clave = cadena()
                espacios()
                require(i < s.length && s[i] == ':') { "Se esperaba : en $i" }
                i++
                mapa[clave] = valor()
                espacios()
                require(i < s.length) { "Objeto sin cerrar" }
                when (s[i++]) {
                    ',' -> continue
                    '}' -> return mapa
                    else -> throw IllegalArgumentException("Se esperaba , o } en ${i - 1}")
                }
            }
        }
    }
}
