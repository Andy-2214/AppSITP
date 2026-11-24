package com.sitp.arequipa.data.remote

import com.sitp.arequipa.domain.model.LugarPedido
import com.sitp.arequipa.domain.model.ModoBusqueda
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lectura de las respuestas de la IA (ADR-011): válidas, envueltas en texto, inválidas y MI_UBICACION. */
class InstruccionesIATest {

    @Test fun `lee un json valido`() {
        val c = InstruccionesIA.leerInterpretacion(
            """{"origen":{"texto":"UNSA","referencia":null},"destino":{"texto":"mercado San Camilo","referencia":"cerca del colegio X"},"preferencia":"costo","restriccionesNoSoportadas":["pasar por Yanahuara"]}"""
        )!!
        assertEquals("UNSA", c.origen!!.texto)
        assertEquals("mercado San Camilo", c.destino!!.texto)
        assertEquals("cerca del colegio X", c.destino!!.referencia)
        assertEquals(ModoBusqueda.COSTO, c.preferencia)
        assertEquals(listOf("pasar por Yanahuara"), c.restriccionesNoSoportadas)
    }

    @Test fun `acepta el json envuelto en bloque de codigo`() {
        val c = InstruccionesIA.leerInterpretacion("Claro:\n```json\n{\"origen\":{\"texto\":\"MI_UBICACION\"},\"destino\":{\"texto\":\"Cayma\"},\"preferencia\":null,\"restriccionesNoSoportadas\":[]}\n```")!!
        assertTrue(c.origen!!.esMiUbicacion)
        assertNull(c.preferencia)
    }

    @Test fun `campos faltantes o null quedan sin indicar`() {
        val c = InstruccionesIA.leerInterpretacion("""{"origen":null,"destino":{"texto":"Socabaya"}}""")!!
        assertNull(c.origen)
        assertEquals("Socabaya", c.destino!!.texto)
        assertTrue(c.restriccionesNoSoportadas.isEmpty())
    }

    @Test fun `texto con comillas y tildes`() {
        val c = InstruccionesIA.leerInterpretacion("""{"origen":{"texto":"Peña \"Alta\""},"destino":null}""")!!
        assertEquals("Peña \"Alta\"", c.origen!!.texto)
    }

    @Test fun `respuestas que no son el json esperado devuelven null`() {
        assertNull(InstruccionesIA.leerInterpretacion("No sé qué quieres"))
        assertNull(InstruccionesIA.leerInterpretacion("{\"hola\": 1}"))
        assertNull(InstruccionesIA.leerInterpretacion("{\"origen\": {\"texto\": \"A\""))
        assertNull(InstruccionesIA.leerInterpretacion(""))
    }

    @Test fun `preferencia desconocida se ignora`() {
        assertNull(InstruccionesIA.leerInterpretacion("""{"origen":{"texto":"A"},"destino":{"texto":"B"},"preferencia":"comodidad"}""")!!.preferencia)
    }

    @Test fun `indice elegido`() {
        assertEquals(1, InstruccionesIA.leerIndice("""{"indice": 1}""", 3))
        assertNull(InstruccionesIA.leerIndice("""{"indice": null}""", 3))
        assertNull(InstruccionesIA.leerIndice("""{"indice": 7}""", 3))
        assertNull(InstruccionesIA.leerIndice("nada", 3))
    }

    @Test fun `las reglas obligan a no calcular y piden solo json`() {
        assertTrue(InstruccionesIA.INTERPRETAR.contains("NO calculas"))
        assertTrue(InstruccionesIA.INTERPRETAR.contains(LugarPedido.MI_UBICACION))
        assertNotNull(InstruccionesIA.sistemaInterpretar("Arequipa tiene 29 distritos"))
        assertTrue(InstruccionesIA.sistemaInterpretar("contexto X").contains("contexto X"))
    }
}
