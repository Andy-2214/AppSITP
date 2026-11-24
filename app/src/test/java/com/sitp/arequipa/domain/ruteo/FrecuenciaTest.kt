package com.sitp.arequipa.domain.ruteo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrecuenciaTest {

    @Test fun `rango toma el promedio`() = assertEquals(10.0, Frecuencia.minutos("8-12")!!, 1e-9)
    @Test fun `numero solo`() = assertEquals(15.0, Frecuencia.minutos("15")!!, 1e-9)
    @Test fun `texto con palabras`() = assertEquals(10.0, Frecuencia.minutos("cada 8 a 12 minutos")!!, 1e-9)
    @Test fun `horas a minutos`() = assertEquals(60.0, Frecuencia.minutos("cada 1 hora")!!, 1e-9)
    @Test fun `vacio usa el valor por defecto`() = assertNull(Frecuencia.minutos(""))
    @Test fun `sin numeros usa el valor por defecto`() = assertNull(Frecuencia.minutos("frecuente"))
    @Test fun `fuera de rango usa el valor por defecto`() = assertNull(Frecuencia.minutos("500"))
}
