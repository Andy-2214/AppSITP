package com.sitp.arequipa.data.local.cache

import com.sitp.arequipa.data.json.Json
import com.sitp.arequipa.domain.model.OpcionesBusqueda
import com.sitp.arequipa.domain.model.ModoBusqueda
import com.sitp.arequipa.domain.ruteo.MotorRutas
import com.sitp.arequipa.domain.ruteo.RedSintetica.c
import com.sitp.arequipa.domain.ruteo.RedSintetica.recorrido
import com.sitp.arequipa.domain.ruteo.RedSintetica.ruta
import com.sitp.arequipa.domain.ruteo.RedTransporte
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CacheBusquedasTest {

    private fun opciones(): OpcionesBusqueda = runBlocking {
        val rutas = listOf(
            ruta("R1", recorrido(0.0 to 0.0, 3000.0 to 0.0), frecuencia = "8-12"),
            ruta("R2", recorrido(3000.0 to 0.0, 3000.0 to 3000.0)),
            ruta("R3", recorrido(0.0 to 30.0, 3000.0 to 30.0))
        )
        MotorRutas(RedTransporte.construir(rutas)).buscar(c(0.0, 0.0), c(3000.0, 3000.0), ModoBusqueda.TIEMPO)
    }

    @Test fun `el json de la cache conserva las opciones`() {
        val o = opciones()
        val vuelta = SerializadorOpciones.desdeTexto(SerializadorOpciones.aTexto(o))!!
        assertEquals(o.porTiempo.size, vuelta.porTiempo.size)
        assertEquals(o.porCosto.size, vuelta.porCosto.size)
        for ((a, b) in o.porTiempo.zip(vuelta.porTiempo)) {
            assertEquals(a.clave, b.clave)
            assertEquals(a.minutosEstimado, b.minutosEstimado, 1e-9)
            assertEquals(a.minutosMinimo, b.minutosMinimo, 1e-9)
            assertEquals(a.minutosMaximo, b.minutosMaximo, 1e-9)
            assertEquals(a.costo, b.costo, 1e-9)
            assertEquals(a.tramos.size, b.tramos.size)
            for ((ta, tb) in a.tramos.zip(b.tramos)) {
                assertEquals(ta.tipo, tb.tipo)
                assertEquals(ta.codigoRuta, tb.codigoRuta)
                assertEquals(ta.sentido, tb.sentido)
                assertEquals(ta.alternativas, tb.alternativas)
                assertEquals(ta.recorrido.size, tb.recorrido.size)
                assertEquals(ta.desde.lat, tb.desde.lat, 1e-6)
            }
        }
    }

    @Test fun `un texto danado devuelve null`() {
        assertNull(SerializadorOpciones.desdeTexto("{\"tiempo\": 5}"))
        assertNull(SerializadorOpciones.desdeTexto("no es json"))
    }

    @Test fun `la cache guarda lee y sobrevive a reabrir el archivo`() = runBlocking {
        val archivo = File.createTempFile("cache_busquedas", ".json")
        archivo.deleteOnExit()
        val o = opciones()
        val cache = CacheBusquedasRepositoryImpl(archivo)
        assertNull(cache.leer("a"))
        cache.guardar("a", o)
        assertNotNull(cache.leer("a"))

        val reabierta = CacheBusquedasRepositoryImpl(archivo)
        val leida = reabierta.leer("a")!!
        assertEquals(o.porTiempo.map { it.clave }, leida.porTiempo.map { it.clave })
    }

    @Test fun `la cache descarta las mas antiguas`() = runBlocking {
        val archivo = File.createTempFile("cache_busquedas", ".json")
        archivo.deleteOnExit()
        val o = opciones()
        val cache = CacheBusquedasRepositoryImpl(archivo)
        for (i in 0 until 45) cache.guardar("k$i", o)
        assertNull(cache.leer("k0"))
        assertNotNull(cache.leer("k44"))
    }

    @Test fun `un archivo corrupto no rompe la cache`() = runBlocking {
        val archivo = File.createTempFile("cache_busquedas", ".json")
        archivo.deleteOnExit()
        archivo.writeText("{{{ basura")
        val cache = CacheBusquedasRepositoryImpl(archivo)
        assertNull(cache.leer("a"))
        cache.guardar("a", opciones())
        assertNotNull(cache.leer("a"))
    }

    @Test fun `el lector de json maneja escapes y numeros`() {
        val m = Json.leer("""{"a":"x\nyá","b":[1,-2.5,3e2,true,null],"c":{}}""") as Map<*, *>
        assertEquals("x\nyá", m["a"])
        assertEquals(listOf(1.0, -2.5, 300.0, true, null), m["b"])
        assertTrue((m["c"] as Map<*, *>).isEmpty())
        assertEquals("""{"k":"a\"b","n":1,"l":[1.5,"x"]}""", Json.escribir(mapOf("k" to "a\"b", "n" to 1, "l" to listOf(1.5, "x"))))
    }
}
