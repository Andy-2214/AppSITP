package com.sitp.arequipa.data.local.catalogo

import com.sitp.arequipa.data.local.catalogo.entity.CatalogoInfoEntity
import com.sitp.arequipa.data.local.catalogo.entity.DistritoEntity
import com.sitp.arequipa.data.local.catalogo.entity.DistritoSegmentoEntity
import com.sitp.arequipa.data.local.catalogo.entity.LugarEntity
import com.sitp.arequipa.data.local.catalogo.entity.SegmentoCalle
import com.sitp.arequipa.data.repository.LugaresRepositoryImpl
import com.sitp.arequipa.domain.model.AtribucionDatos
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * Prueba cada regla de CatalogoLugaresSource (y del repositorio que la usa) con un catálogo falso en memoria
 * (las distancias se arman en metros alrededor de un punto fijo).
 */
class CatalogoLugaresSourceTest {

    private val lat0 = -16.40
    private val lng0 = -71.53

    private fun e6(grados: Double) = (grados * 1_000_000).roundToInt()

    /** Coordenada a [norte] y [este] metros del punto base. */
    private fun p(norte: Double, este: Double) =
        Pair(lat0 + GeoUtils.gradosLat(norte), lng0 + GeoUtils.gradosLng(este, lat0))

    /** Catálogo falso: aplica los mismos filtros por área que las consultas SQL reales. */
    private class CatalogoFalso : CatalogoDao {
        val calles = mutableListOf<SegmentoCalle>()
        val lugares = mutableListOf<LugarEntity>()
        val distritosLista = mutableListOf<DistritoEntity>()
        val bordes = mutableListOf<DistritoSegmentoEntity>()
        val infoLista = mutableListOf<CatalogoInfoEntity>()
        var lecturasDeDistritos = 0
        var lecturasDeInfo = 0
        var fallar = false

        override fun segmentosEnArea(minLatE6: Int, maxLatE6: Int, minLngE6: Int, maxLngE6: Int): List<SegmentoCalle> {
            if (fallar) error("base dañada")
            return calles.filter { it.lat1E6 in minLatE6..maxLatE6 && it.lng1E6 in minLngE6..maxLngE6 }
        }

        override fun lugaresEnArea(minLatE6: Int, maxLatE6: Int, minLngE6: Int, maxLngE6: Int) =
            lugares.filter { it.latE6 in minLatE6..maxLatE6 && it.lngE6 in minLngE6..maxLngE6 }

        override fun distritos(): List<DistritoEntity> {
            lecturasDeDistritos++
            return distritosLista
        }

        override fun segmentosDeDistritos() = bordes

        override fun info(): List<CatalogoInfoEntity> {
            lecturasDeInfo++
            if (fallar) error("base dañada")
            return infoLista
        }
    }

    private val catalogo = CatalogoFalso()
    private val fuente = CatalogoLugaresSource(catalogo)

    /** Tramo recto de calle entre dos puntos (en metros desde la base). */
    private fun calle(nombre: String, desde: Pair<Double, Double>, hasta: Pair<Double, Double>) {
        val a = p(desde.first, desde.second)
        val b = p(hasta.first, hasta.second)
        catalogo.calles += SegmentoCalle(nombre, e6(a.first), e6(a.second), e6(b.first), e6(b.second))
    }

    private fun lugar(nombre: String, tipo: String, norte: Double, este: Double) {
        val (lat, lng) = p(norte, este)
        catalogo.lugares += LugarEntity(catalogo.lugares.size + 1, 1L, nombre, tipo, e6(lat), e6(lng))
    }

    /** Distrito cuadrado de [lado] metros centrado en la base. */
    private fun distrito(nombre: String, lado: Double = 2000.0) {
        val id = catalogo.distritosLista.size + 1
        val m = lado / 2
        val (n, e) = p(m, m)
        val (s, o) = p(-m, -m)
        catalogo.distritosLista += DistritoEntity(id, nombre, e6(s), e6(n), e6(o), e6(e))
        listOf(
            listOf(n, o, n, e), listOf(n, e, s, e), listOf(s, e, s, o), listOf(s, o, n, o)
        ).forEach { (a, b, c, d) -> catalogo.bordes += DistritoSegmentoEntity(0, id, e6(a), e6(b), e6(c), e6(d)) }
    }

    private fun nombrar(norte: Double = 0.0, este: Double = 0.0) = runBlocking {
        val (lat, lng) = p(norte, este)
        fuente.coordenadasANombre(lat, lng)
    }

    @Test
    fun `calle con nombre a menos de 100 m y distrito`() {
        distrito("Cayma")
        calle("Avenida Ejército", -40.0 to -25.0, -40.0 to 25.0) // pasa a 40 m
        assertEquals("Avenida Ejército, Cayma", nombrar())
    }

    @Test
    fun `gana la calle mas cercana`() {
        distrito("Cayma")
        calle("Avenida Lejana", 90.0 to -25.0, 90.0 to 25.0)
        calle("Calle Cercana", -20.0 to -25.0, -20.0 to 25.0)
        assertEquals("Calle Cercana, Cayma", nombrar())
    }

    @Test
    fun `una calle a mas de 100 m no se usa`() {
        distrito("Cayma")
        calle("Avenida Lejana", 120.0 to -25.0, 120.0 to 25.0)
        assertEquals("Cayma", nombrar())
    }

    @Test
    fun `encuentra el tramo aunque su inicio este lejos del punto`() {
        // Tramo de 50 m: empieza a 149 m (fuera de los 100 m) y termina a 99 m del punto.
        // Solo se encuentra porque la búsqueda por área usa 100 + 55 m de margen.
        distrito("Cayma")
        calle("Avenida Larga", 0.0 to 149.0, 0.0 to 99.0)
        assertEquals("Avenida Larga, Cayma", nombrar())
    }

    @Test
    fun `un tramo que no llega a 100 m no cuenta aunque su inicio entre en el area`() {
        distrito("Cayma")
        calle("Avenida Larga", 0.0 to 150.0, 0.0 to 102.0)
        assertEquals("Cayma", nombrar())
    }

    @Test
    fun `calle generica se reemplaza por un lugar de referencia cercano`() {
        distrito("Paucarpata")
        calle("Calle 1", -10.0 to -25.0, -10.0 to 25.0)
        lugar("Mercado San Camilo", "amenity=marketplace", 150.0, 0.0)
        assertEquals("cerca de Mercado San Camilo, Paucarpata", nombrar())
    }

    @Test
    fun `calle generica se mantiene si no hay lugares cerca`() {
        distrito("Paucarpata")
        calle("Pasaje B", -10.0 to -25.0, -10.0 to 25.0)
        assertEquals("Pasaje B, Paucarpata", nombrar())
    }

    @Test
    fun `sin calle cercana se usa el lugar de referencia`() {
        distrito("Arequipa")
        lugar("Plaza de Armas", "place=square", 120.0, 0.0)
        assertEquals("cerca de Plaza de Armas, Arequipa", nombrar())
    }

    @Test
    fun `se prefiere un lugar de referencia aunque haya otro mas cerca`() {
        distrito("Paucarpata")
        lugar("Bodega Don Pepe", "shop=convenience", 20.0, 0.0)
        lugar("Mall Aventura Plaza", "shop=mall", 180.0, 0.0)
        assertEquals("cerca de Mall Aventura Plaza, Paucarpata", nombrar())
    }

    @Test
    fun `si ningun lugar es de referencia se usa el mas cercano`() {
        distrito("Paucarpata")
        lugar("Bodega Lejana", "shop=convenience", 150.0, 0.0)
        lugar("Bodega Don Pepe", "shop=convenience", 20.0, 0.0)
        assertEquals("cerca de Bodega Don Pepe, Paucarpata", nombrar())
    }

    @Test
    fun `un lugar a mas de 200 m no se usa`() {
        distrito("Socabaya")
        lugar("Colegio Lejano", "amenity=school", 250.0, 0.0)
        assertEquals("Socabaya", nombrar())
    }

    @Test
    fun `sin calle, lugar ni distrito devuelve la zona por defecto`() {
        assertEquals(CatalogoLugaresSource.ZONA_POR_DEFECTO, nombrar())
    }

    @Test
    fun `fuera de los distritos pero con calle muestra solo la calle`() {
        distrito("Cayma", lado = 400.0)
        calle("Carretera Yura", 5000.0 - 30.0 to 4975.0, 5000.0 - 30.0 to 5025.0)
        assertEquals("Carretera Yura", nombrar(5000.0, 5000.0))
    }

    @Test
    fun `si el catalogo falla devuelve la zona por defecto sin romper la app`() {
        distrito("Cayma")
        catalogo.fallar = true
        assertEquals(CatalogoLugaresSource.ZONA_POR_DEFECTO, nombrar())
    }

    @Test
    fun `los distritos se leen de la base una sola vez`() {
        distrito("Cayma")
        repeat(5) { nombrar(it * 10.0, 0.0) }
        assertEquals(1, catalogo.lecturasDeDistritos)
    }

    @Test
    fun `deteccion de nombres genericos`() {
        listOf("Calle 1", "Calle 12", "Pasaje B", "Av. 3A", "JIRÓN 5", "Psje. C", "calle  7").forEach {
            assertTrue("'$it' debería ser genérico", CatalogoLugaresSource.esNombreGenerico(it))
        }
        listOf("Calle Mercaderes", "Avenida Ejército", "Calle 1 de Mayo", "Pasaje Los Olivos", "Avenida Perú").forEach {
            assertFalse("'$it' no es genérico", CatalogoLugaresSource.esNombreGenerico(it))
        }
    }

    @Test
    fun `el repositorio delega en el catalogo a traves del puerto LugaresRepository`() = runBlocking {
        distrito("Yanahuara")
        calle("Avenida Ejército", -40.0 to -25.0, -40.0 to 25.0)
        val repo = LugaresRepositoryImpl(fuente)
        assertEquals("Avenida Ejército, Yanahuara", repo.coordenadasANombre(lat0, lng0))
    }

    @Test
    fun `el credito de la fuente sale del catalogo, no del codigo`() = runBlocking {
        catalogo.infoLista += CatalogoInfoEntity("fuente", "Fuente de prueba")
        catalogo.infoLista += CatalogoInfoEntity("atribucion", "© Fuente de prueba")
        catalogo.infoLista += CatalogoInfoEntity("enlace_atribucion", "https://ejemplo.org/licencia")
        val repo = LugaresRepositoryImpl(fuente)
        assertEquals(AtribucionDatos("© Fuente de prueba", "https://ejemplo.org/licencia"), repo.atribucion())
    }

    @Test
    fun `credito sin enlace`() = runBlocking {
        catalogo.infoLista += CatalogoInfoEntity("atribucion", "© Fuente de prueba")
        catalogo.infoLista += CatalogoInfoEntity("enlace_atribucion", "  ")
        assertEquals(AtribucionDatos("© Fuente de prueba", null), LugaresRepositoryImpl(fuente).atribucion())
    }

    @Test
    fun `si la fuente no exige credito no se muestra nada`() = runBlocking {
        catalogo.infoLista += CatalogoInfoEntity("fuente", "Datos propios")
        assertNull(LugaresRepositoryImpl(fuente).atribucion())
    }

    @Test
    fun `si no se puede leer la informacion del catalogo no hay credito pero la app sigue`() = runBlocking {
        catalogo.fallar = true
        assertNull(LugaresRepositoryImpl(fuente).atribucion())
    }

    @Test
    fun `la informacion del catalogo se lee una sola vez`() = runBlocking {
        catalogo.infoLista += CatalogoInfoEntity("atribucion", "© Fuente de prueba")
        val repo = LugaresRepositoryImpl(fuente)
        repeat(3) { repo.atribucion() }
        assertEquals(1, catalogo.lecturasDeInfo)
    }
}
