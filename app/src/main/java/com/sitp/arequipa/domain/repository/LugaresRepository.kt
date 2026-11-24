package com.sitp.arequipa.domain.repository

import com.sitp.arequipa.domain.model.AtribucionDatos
import com.sitp.arequipa.domain.model.Coordenada
import com.sitp.arequipa.domain.model.LugarEncontrado

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

    /**
     * Lugares y calles cuyo nombre coincide con [texto] (sin importar tildes ni mayúsculas), del mejor al
     * peor. Con [cerca], los empates se resuelven por cercanía. Funciona sin internet. Nunca lanza excepción.
     * Aquí se sumarán los alias del ADR-003 sin cambiar a quien lo usa.
     */
    suspend fun buscarPorNombre(texto: String, cerca: Coordenada? = null, limite: Int = 8): List<LugarEncontrado>
}
