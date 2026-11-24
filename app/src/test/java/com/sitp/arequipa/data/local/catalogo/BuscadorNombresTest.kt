package com.sitp.arequipa.data.local.catalogo

import com.sitp.arequipa.data.local.catalogo.BuscadorNombres.Entrada
import com.sitp.arequipa.domain.model.Coordenada
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuscadorNombresTest {

    private fun lugar(nombre: String, lat: Double, lng: Double, tipo: String = "shop=convenience") = Entrada(nombre, lat, lng, tipo, esCalle = false)
    private fun calle(nombre: String, lat: Double, lng: Double) = Entrada(nombre, lat, lng, "highway=residential", esCalle = true)

    private val buscador = BuscadorNombres(listOf(
        lugar("Mercado San Camilo", -16.4005, -71.5370, "amenity=marketplace"),
        lugar("Plaza de Armas", -16.3989, -71.5369, "place=square"),
        lugar("Plaza de Armas", -16.3500, -71.5800, "place=square"),
        lugar("Universidad Nacional de San Agustín", -16.4056, -71.5247, "amenity=university"),
        lugar("Colegio San Camilo", -16.4100, -71.5400, "amenity=school"),
        calle("Avenida Ejército", -16.3908, -71.5448),
        calle("Calle Moral", -16.3971, -71.5371)
    ))

    @Test fun `ignora tildes y mayusculas`() {
        val r = buscador.buscar("EJERCITO", null, 5)
        assertEquals("Avenida Ejército", r.first().entrada.nombre)
        assertEquals("Universidad Nacional de San Agustín", buscador.buscar("san agustin", null, 5).first().entrada.nombre)
    }

    @Test fun `entiende abreviaturas`() {
        assertEquals("Avenida Ejército", buscador.buscar("Av. Ejército", null, 3).first().entrada.nombre)
        assertEquals("Mercado San Camilo", buscador.buscar("mcdo san camilo", null, 3).first().entrada.nombre)
    }

    @Test fun `el nombre exacto gana al parcial`() {
        val r = buscador.buscar("Mercado San Camilo", null, 5)
        assertEquals("Mercado San Camilo", r.first().entrada.nombre)
        assertTrue(r.first().puntaje > r.getOrNull(1)?.puntaje ?: 0)
    }

    @Test fun `palabras sueltas encuentran el lugar`() {
        assertEquals("Mercado San Camilo", buscador.buscar("san camilo mercado", null, 3).first().entrada.nombre)
    }

    @Test fun `con dos lugares iguales gana el mas cercano a la referencia`() {
        val cercaDelCentro = buscador.buscar("plaza de armas", Coordenada(-16.3990, -71.5370), 5)
        assertEquals(-16.3989, cercaDelCentro.first().entrada.lat, 1e-6)
        val cercaDelNorte = buscador.buscar("plaza de armas", Coordenada(-16.3510, -71.5790), 5)
        assertEquals(-16.3500, cercaDelNorte.first().entrada.lat, 1e-6)
    }

    @Test fun `quita duplicados del mismo nombre muy cercanos`() {
        val b = BuscadorNombres(listOf(
            lugar("Parque Selva Alegre", -16.3900, -71.5200), lugar("Parque Selva Alegre", -16.3901, -71.5201)
        ))
        assertEquals(1, b.buscar("selva alegre", null, 5).size)
    }

    @Test fun `un texto sin coincidencias no devuelve nada`() {
        assertTrue(buscador.buscar("zzzzzz", null, 5).isEmpty())
        assertTrue(buscador.buscar("   ", null, 5).isEmpty())
    }

    @Test fun `normalizar`() {
        assertEquals("avenida ejercito", BuscadorNombres.normalizar("Av. Ejército"))
        assertEquals("peña", BuscadorNombres.normalizar("Peña"))
    }
}
