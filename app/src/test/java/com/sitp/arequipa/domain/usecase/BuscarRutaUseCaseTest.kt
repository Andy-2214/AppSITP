package com.sitp.arequipa.domain.usecase

import com.sitp.arequipa.domain.model.ModoBusqueda
import com.sitp.arequipa.domain.model.OpcionesBusqueda
import com.sitp.arequipa.domain.repository.CacheBusquedasRepository
import com.sitp.arequipa.domain.ruteo.RedSintetica.c
import com.sitp.arequipa.domain.ruteo.RedSintetica.recorrido
import com.sitp.arequipa.domain.ruteo.RedSintetica.ruta
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Entrega progresiva y caché del caso de uso (ADR-010). */
class BuscarRutaUseCaseTest {

    private class CacheEnMemoria : CacheBusquedasRepository {
        val mapa = HashMap<String, OpcionesBusqueda>()
        var guardados = 0
        override suspend fun leer(clave: String) = mapa[clave]
        override suspend fun guardar(clave: String, opciones: OpcionesBusqueda) { guardados++; mapa[clave] = opciones }
    }

    private val rutas = (0 until 6).map { i ->
        ruta("P$i", recorrido(0.0 to i * 40.0, 4000.0 to i * 40.0), frecuencia = "${8 + i * 2}")
    } + ruta("X", recorrido(0.0 to 0.0, 2000.0 to 0.0)) + ruta("Y", recorrido(2000.0 to 0.0, 4000.0 to 0.0))

    @Test
    fun `la ultima emision esta completa y las anteriores no`() = runBlocking {
        val emisiones = BuscarRutaUseCase()(rutas, c(0.0, 0.0), c(4000.0, 0.0), ModoBusqueda.TIEMPO).toList()
        assertTrue(emisiones.isNotEmpty())
        assertTrue(emisiones.last().completa)
        emisiones.dropLast(1).forEach { assertFalse(it.completa) }
        // Si hubo emisiones parciales, la primera ya trae al menos 2 opciones del modo elegido
        val parcial = emisiones.firstOrNull { !it.completa }
        if (parcial != null) assertTrue(parcial.porTiempo.size >= 2)
    }

    @Test
    fun `la misma busqueda sale de la cache sin recalcular`() = runBlocking {
        val cache = CacheEnMemoria()
        val caso = BuscarRutaUseCase(cache)
        val primera = caso(rutas, c(0.0, 0.0), c(4000.0, 0.0), ModoBusqueda.TIEMPO).toList().last()
        assertEquals(1, cache.guardados)
        val segunda = caso(rutas, c(0.0, 0.0), c(4000.0, 0.0), ModoBusqueda.COSTO).toList()
        assertEquals(1, segunda.size)
        assertTrue(segunda.single().desdeCache)
        assertEquals(primera.porTiempo, segunda.single().porTiempo)
        assertEquals(1, cache.guardados)
    }

    @Test
    fun `si cambian las rutas cambia la clave de la cache`() {
        val otra = rutas.map { if (it.codigo == "P0") it.copy(frecuencia = "30") else it }
        assertNotEquals(BuscarRutaUseCase.firmaRutas(rutas), BuscarRutaUseCase.firmaRutas(otra))
        assertEquals(BuscarRutaUseCase.firmaRutas(rutas), BuscarRutaUseCase.firmaRutas(rutas.reversed()))
    }

    @Test
    fun `sin rutas devuelve vacio`() = runBlocking {
        val r = BuscarRutaUseCase()(emptyList(), c(0.0, 0.0), c(1.0, 1.0), ModoBusqueda.TIEMPO).toList()
        assertTrue(r.single().vacia)
    }
}
