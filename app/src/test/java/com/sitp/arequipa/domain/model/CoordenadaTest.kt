package com.sitp.arequipa.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoordenadaTest {

    @Test
    fun comoTexto_usaPuntoDecimalYCuatroDecimales() {
        assertEquals("-16.3971, -71.5371", Coordenada(-16.39712, -71.53708).comoTexto())
    }

    @Test
    fun desdeTexto_leeElFormatoDelHistorial() {
        assertEquals(Coordenada(-16.3971, -71.5371), Coordenada.desdeTexto("-16.3971, -71.5371"))
        assertEquals(Coordenada(-16.3908, -71.5448), Coordenada.desdeTexto("  -16.3908,-71.5448 "))
    }

    @Test
    fun desdeTexto_aceptaComaDecimalDeTelefonosEnEspanol() {
        assertEquals(Coordenada(-16.3971, -71.5371), Coordenada.desdeTexto("-16,3971, -71,5371"))
    }

    @Test
    fun idaYVuelta_conservaElPunto() {
        val p = Coordenada(-16.4273, -71.5141)
        assertEquals(p, Coordenada.desdeTexto(p.comoTexto()))
    }

    @Test
    fun desdeTexto_rechazaTextosQueNoSonCoordenadas() {
        assertNull(Coordenada.desdeTexto("Calle Moral, Arequipa"))
        assertNull(Coordenada.desdeTexto(""))
        assertNull(Coordenada.desdeTexto("-16.3971"))
        assertNull(Coordenada.desdeTexto("-96.0, -71.5"))   // latitud fuera de rango
        assertNull(Coordenada.desdeTexto("-16.3, -181.0"))  // longitud fuera de rango
    }
}
