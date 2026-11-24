package com.sitp.arequipa.data.repository

import com.sitp.arequipa.data.local.catalogo.CatalogoLugaresSource
import com.sitp.arequipa.domain.model.AtribucionDatos
import com.sitp.arequipa.domain.model.Coordenada
import com.sitp.arequipa.domain.model.LugarEncontrado
import com.sitp.arequipa.domain.repository.LugaresRepository
import kotlinx.coroutines.CancellationException
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

    override suspend fun buscarPorNombre(texto: String, cerca: Coordenada?, limite: Int): List<LugarEncontrado> =
        withContext(Dispatchers.IO) {
            try {
                catalogoSource.buscador().buscar(texto, cerca, limite).map { r ->
                    val e = r.entrada
                    val distrito = catalogoSource.distrito(e.lat, e.lng)
                    val tipo = CatalogoLugaresSource.etiquetaTipo(e.tipo)
                    LugarEncontrado(
                        nombre = e.nombre,
                        detalle = listOfNotNull(tipo, distrito).joinToString(" · "),
                        coordenada = Coordenada(e.lat, e.lng),
                        puntaje = r.puntaje,
                        esCalle = e.esCalle
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("DEBUG LugaresRepository: error buscando «$texto» (${e.message})")
                emptyList()
            }
        }

    private companion object {
        const val CLAVE_ATRIBUCION = "atribucion"
        const val CLAVE_ENLACE = "enlace_atribucion"
    }
}
