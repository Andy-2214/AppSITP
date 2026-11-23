package com.sitp.arequipa.domain.repository

import com.sitp.arequipa.domain.model.AtribucionDatos

/**
 * Puerto de dominio para nombres de lugares.
 *
 * No depende de ningún proveedor: hoy lo implementa un catálogo local (ADR-001/002), pero podría
 * implementarlo cualquier otra fuente sin cambiar el dominio ni la UI (arquitectura hexagonal).
 */
interface LugaresRepository {

    /** Texto legible para un punto, p. ej. "Avenida Ejército, Cayma". Nunca lanza excepción. */
    suspend fun coordenadasANombre(lat: Double, lng: Double): String

    /** Crédito que exige la licencia de la fuente de datos actual, o null si no exige ninguno. */
    suspend fun atribucion(): AtribucionDatos?
}
