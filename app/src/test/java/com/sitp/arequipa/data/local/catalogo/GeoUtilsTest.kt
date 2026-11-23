package com.sitp.arequipa.data.local.catalogo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoUtilsTest {

    private val lat0 = -16.40
    private val lng0 = -71.53

    /** Desplaza un punto [norte] y [este] metros. */
    private fun mover(norte: Double, este: Double, lat: Double = lat0, lng: Double = lng0) =
        Pair(lat + GeoUtils.gradosLat(norte), lng + GeoUtils.gradosLng(este, lat))

    @Test
    fun `distanciaMetros mide bien en ambos ejes`() {
        val (latN, lngN) = mover(100.0, 0.0)
        val (latE, lngE) = mover(0.0, 100.0)
        assertEquals(100.0, GeoUtils.distanciaMetros(lat0, lng0, latN, lngN), 0.01)
        assertEquals(100.0, GeoUtils.distanciaMetros(lat0, lng0, latE, lngE), 0.01)
        assertEquals(0.0, GeoUtils.distanciaMetros(lat0, lng0, lat0, lng0), 0.0)
    }

    @Test
    fun `distanciaMetros coincide con Haversine a escala urbana`() {
        // 2 km al noreste: la aproximación plana debe diferir < 0,5 % de la fórmula exacta
        val (lat, lng) = mover(1414.0, 1414.0)
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat - lat0)
        val dLng = Math.toRadians(lng - lng0)
        val a = Math.sin(dLat / 2).let { it * it } +
            Math.cos(Math.toRadians(lat0)) * Math.cos(Math.toRadians(lat)) * Math.sin(dLng / 2).let { it * it }
        val haversine = 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        assertEquals(haversine, GeoUtils.distanciaMetros(lat0, lng0, lat, lng), haversine * 0.005)
    }

    @Test
    fun `distancia a un tramo usa la perpendicular cuando cae dentro del tramo`() {
        // Tramo este-oeste de 200 m que pasa 60 m al norte del punto
        val (la1, ln1) = mover(60.0, -100.0)
        val (la2, ln2) = mover(60.0, 100.0)
        assertEquals(60.0, GeoUtils.distanciaPuntoSegmento(lat0, lng0, Segmento(la1, ln1, la2, ln2)), 0.05)
    }

    @Test
    fun `distancia a un tramo usa el extremo cuando la perpendicular cae fuera`() {
        // Tramo que empieza 30 m al este y termina 80 m al este: lo más cercano es su inicio (30 m)
        val (la1, ln1) = mover(0.0, 30.0)
        val (la2, ln2) = mover(0.0, 80.0)
        assertEquals(30.0, GeoUtils.distanciaPuntoSegmento(lat0, lng0, Segmento(la1, ln1, la2, ln2)), 0.05)
        // Mismo resultado con el tramo al revés
        assertEquals(30.0, GeoUtils.distanciaPuntoSegmento(lat0, lng0, Segmento(la2, ln2, la1, ln1)), 0.05)
    }

    @Test
    fun `distancia a un tramo de largo cero es la distancia al punto`() {
        val (la, ln) = mover(40.0, 30.0)
        assertEquals(50.0, GeoUtils.distanciaPuntoSegmento(lat0, lng0, Segmento(la, ln, la, ln)), 0.05)
    }

    private fun cuadrado(lado: Double, centroLat: Double = lat0, centroLng: Double = lng0): List<Segmento> {
        val m = lado / 2
        val (n, e) = mover(m, m, centroLat, centroLng)
        val (s, o) = mover(-m, -m, centroLat, centroLng)
        return listOf(
            Segmento(n, o, n, e), Segmento(n, e, s, e), Segmento(s, e, s, o), Segmento(s, o, n, o)
        )
    }

    @Test
    fun `punto dentro y fuera de un poligono`() {
        val borde = cuadrado(1000.0)
        assertTrue(GeoUtils.dentroDePoligono(lat0, lng0, borde))
        val (latFuera, lngFuera) = mover(0.0, 800.0)
        assertFalse(GeoUtils.dentroDePoligono(latFuera, lngFuera, borde))
        val (latNorte, lngNorte) = mover(800.0, 0.0)
        assertFalse(GeoUtils.dentroDePoligono(latNorte, lngNorte, borde))
    }

    @Test
    fun `el orden de los tramos del borde no importa`() {
        val borde = cuadrado(1000.0).reversed().let { listOf(it[2], it[0], it[3], it[1]) }
        assertTrue(GeoUtils.dentroDePoligono(lat0, lng0, borde))
    }

    @Test
    fun `un hueco (anillo interior) queda fuera del poligono`() {
        val borde = cuadrado(1000.0) + cuadrado(200.0)
        assertFalse("el centro está en el hueco", GeoUtils.dentroDePoligono(lat0, lng0, borde))
        val (lat, lng) = mover(300.0, 0.0)
        assertTrue("entre el borde exterior y el hueco", GeoUtils.dentroDePoligono(lat, lng, borde))
    }

    @Test
    fun `un borde vacio no contiene nada`() {
        assertFalse(GeoUtils.dentroDePoligono(lat0, lng0, emptyList()))
    }

    @Test
    fun `el area de busqueda contiene el circulo y redondea hacia afuera`() {
        val area = GeoUtils.areaAlrededor(lat0, lng0, 150.0)
        for ((norte, este) in listOf(150.0 to 0.0, -150.0 to 0.0, 0.0 to 150.0, 0.0 to -150.0)) {
            val (lat, lng) = mover(norte, este)
            val latE6 = Math.round(lat * 1e6).toInt()
            val lngE6 = Math.round(lng * 1e6).toInt()
            assertTrue(latE6 in area.minLatE6..area.maxLatE6)
            assertTrue(lngE6 in area.minLngE6..area.maxLngE6)
        }
        assertTrue(area.minLatE6 < area.maxLatE6 && area.minLngE6 < area.maxLngE6)
    }

    @Test
    fun `conversion desde millonesimas de grado`() {
        assertEquals(-16.391475, GeoUtils.desdeE6(-16_391_475), 1e-12)
        assertEquals(0.0, GeoUtils.desdeE6(0), 0.0)
    }
}
