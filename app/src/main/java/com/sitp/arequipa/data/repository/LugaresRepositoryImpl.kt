package com.sitp.arequipa.data.repository

import com.sitp.arequipa.data.local.catalogo.CatalogoLugaresSource
import com.sitp.arequipa.domain.model.AtribucionDatos
import com.sitp.arequipa.domain.repository.LugaresRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementación del puerto [LugaresRepository] con el catálogo local (ADR-001/002):
 * sin internet ni costo por consulta. Para usar otra fuente de datos basta con otra
 * implementación de [LugaresRepository]; el dominio y la UI no cambian.
 */
class LugaresRepositoryImpl(
    private val catalogoSource: CatalogoLugaresSource
) : LugaresRepository {

    override suspend fun coordenadasANombre(lat: Double, lng: Double): String =
        withContext(Dispatchers.IO) { catalogoSource.coordenadasANombre(lat, lng) }

    override suspend fun atribucion(): AtribucionDatos? = withContext(Dispatchers.IO) {
        val info = catalogoSource.info()
        info[CLAVE_ATRIBUCION]?.takeIf { it.isNotBlank() }?.let {
            AtribucionDatos(texto = it, enlace = info[CLAVE_ENLACE]?.takeIf { e -> e.isNotBlank() })
        }
    }

    private companion object {
        const val CLAVE_ATRIBUCION = "atribucion"
        const val CLAVE_ENLACE = "enlace_atribucion"
    }
}
