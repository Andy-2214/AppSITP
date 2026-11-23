package com.sitp.arequipa.presentation.historial

import com.sitp.arequipa.domain.model.RutaFavorita
import org.junit.Assert.assertEquals
import org.junit.Test

class TextosHistorialTest {

    private val nombres = mapOf(
        "-16.3971, -71.5371" to "Calle Moral, Arequipa",
        "-16.3908, -71.5448" to "Avenida Ejército, Yanahuara"
    )

    @Test
    fun textoPunto_muestraElNombreSiLoConoce() {
        assertEquals("Calle Moral, Arequipa", textoPunto("-16.3971, -71.5371", nombres))
    }

    @Test
    fun textoPunto_muestraLoGuardadoMientrasNoHayNombre() {
        assertEquals("-16.4273, -71.5141", textoPunto("-16.4273, -71.5141", nombres))
    }

    @Test
    fun tituloFavorito_automaticoMuestraNombres() {
        val fav = RutaFavorita(origen = "-16.3971, -71.5371", destino = "-16.3908, -71.5448",
            nombre = "-16.3971, -71.5371 → -16.3908, -71.5448")
        assertEquals("Calle Moral, Arequipa → Avenida Ejército, Yanahuara", tituloFavorito(fav, nombres))
    }

    @Test
    fun tituloFavorito_respetaElNombrePuestoPorElUsuario() {
        val fav = RutaFavorita(origen = "-16.3971, -71.5371", destino = "-16.3908, -71.5448", nombre = "Casa → Trabajo")
        assertEquals("Casa → Trabajo", tituloFavorito(fav, nombres))
    }
}
