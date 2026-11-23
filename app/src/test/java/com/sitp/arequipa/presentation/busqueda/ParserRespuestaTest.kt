package com.sitp.arequipa.presentation.busqueda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Pasos de la ruta que se muestran al usuario: sin "Bájese en en..." y con los nombres de calles. */
class ParserRespuestaTest {

    private fun pasos(respuesta: String) = parsearRespuestaIA(respuesta).segmentos

    @Test
    fun `algoritmo propio con una combi - caso real de la app`() {
        val respuesta = """
            RUTAS: [SAN-MIGUEL-C3]

            1. Tome la combi SAN-MIGUEL-C3 en el origen, cerca de Calle Moral, Arequipa, y bájese en el destino, cerca de Avenida Ejército, Yanahuara

            ESTIMACIÓN: 11 minutos aproximadamente
            COSTO TOTAL: S/1.30 (1 combi x S/1.30)
        """.trimIndent()
        val s = pasos(respuesta).single()
        assertEquals("SAN-MIGUEL-C3", s.codigoRuta)
        assertEquals("Tome la combi SAN-MIGUEL-C3 en el origen", s.abordarTexto)
        assertEquals("Cerca de Calle Moral, Arequipa", s.abordarDetalle)
        assertEquals("Bájese en el destino", s.bajarTexto)
        assertEquals("Cerca de Avenida Ejército, Yanahuara", s.bajarDetalle)
    }

    @Test
    fun `algoritmo propio con transbordo`() {
        val respuesta = """
            RUTAS: [A-1, B-2]

            1. Tome la combi A-1 en el origen, cerca de Calle Moral, Arequipa, y bájese en el punto de transbordo 1
            2. Tome la combi B-2 en el punto de transbordo 1, y bájese en el destino, cerca de Avenida Perú, Paucarpata

            ESTIMACIÓN: 25 minutos aproximadamente
            COSTO TOTAL: S/2.60 (2 combis x S/1.30)
        """.trimIndent()
        val (primero, segundo) = pasos(respuesta)
        assertEquals("Bájese en el punto de transbordo 1", primero.bajarTexto)
        assertEquals("Bájese en el destino", segundo.bajarTexto)
        assertEquals("Cerca de Avenida Perú, Paucarpata", segundo.bajarDetalle)
    }

    @Test
    fun `respuesta de la IA con avenida de bajada`() {
        val respuesta = """
            RUTAS: [LA-C8]

            1. Tome la combi LA-C8 en Av. Ejército, cerca de Real Plaza, y bájese en Av. Venezuela, cerca de Mercado Avelino

            ESTIMACIÓN: 20 minutos aproximadamente
            COSTO TOTAL: S/1.30 (1 combi x S/1.30)
        """.trimIndent()
        val s = pasos(respuesta).single()
        assertEquals("Bájese en Av. Venezuela", s.bajarTexto)
        assertEquals("Cerca de Mercado Avelino", s.bajarDetalle)
    }

    @Test
    fun `bajada sin preposicion se completa con en`() {
        val s = pasos("RUTAS: [LA-C8]\n\n1. Tome la combi LA-C8 en Av. Ejército, y bájese Av. Goyeneche").single()
        assertEquals("Bájese en Av. Goyeneche", s.bajarTexto)
    }

    @Test
    fun `no recorta calles que empiezan con En`() {
        val s = pasos("RUTAS: [LA-C8]\n\n1. Tome la combi LA-C8 en Av. Ejército, y bájese en Enrique Palacios").single()
        assertEquals("Bájese en Enrique Palacios", s.bajarTexto)
    }

    @Test
    fun `ningun formato produce en en`() {
        val variantes = listOf(
            "y bájese en el destino", "y bajese en el destino", "y baje en el destino",
            "y bájese en Av. Ejército", "y bájese Av. Ejército", "y bájese en en el destino"
        )
        for (v in variantes) {
            val s = pasos("RUTAS: [X-1]\n\n1. Tome la combi X-1 en el origen, $v").single()
            assertFalse("'$v' produjo '${s.bajarTexto}'", s.bajarTexto.contains(Regex("""\ben en\b""", RegexOption.IGNORE_CASE)))
        }
    }
}
