package com.sitp.arequipa.data.local.catalogo

import androidx.room.Dao
import androidx.room.Query
import com.sitp.arequipa.data.local.catalogo.entity.CatalogoInfoEntity
import com.sitp.arequipa.data.local.catalogo.entity.DistritoEntity
import com.sitp.arequipa.data.local.catalogo.entity.DistritoSegmentoEntity
import com.sitp.arequipa.data.local.catalogo.entity.LugarEntity
import com.sitp.arequipa.data.local.catalogo.entity.SegmentoCalle

/**
 * Consultas de solo lectura sobre el catálogo local de calles, lugares y distritos.
 * Coordenadas en millonésimas de grado (E6).
 *
 * Funciones síncronas (como RutaDao): Room 2.6.1 + KSP genera código Java inválido para DAOs con
 * `suspend` en este proyecto. Llamar siempre desde un hilo de fondo (LugaresRepositoryImpl usa Dispatchers.IO).
 */
@Dao
interface CatalogoDao {

    /** Tramos de calle cuyo punto inicial cae dentro del rectángulo indicado. */
    @Query(
        """
        SELECT c.nombre AS nombre, s.lat1E6 AS lat1E6, s.lng1E6 AS lng1E6, s.lat2E6 AS lat2E6, s.lng2E6 AS lng2E6
        FROM calle_segmentos s INNER JOIN calles c ON c.id = s.calleId
        WHERE s.lat1E6 BETWEEN :minLatE6 AND :maxLatE6 AND s.lng1E6 BETWEEN :minLngE6 AND :maxLngE6
        """
    )
    fun segmentosEnArea(minLatE6: Int, maxLatE6: Int, minLngE6: Int, maxLngE6: Int): List<SegmentoCalle>

    /** Lugares de referencia dentro del rectángulo indicado. */
    @Query(
        """
        SELECT * FROM lugares
        WHERE latE6 BETWEEN :minLatE6 AND :maxLatE6 AND lngE6 BETWEEN :minLngE6 AND :maxLngE6
        """
    )
    fun lugaresEnArea(minLatE6: Int, maxLatE6: Int, minLngE6: Int, maxLngE6: Int): List<LugarEntity>

    @Query("SELECT * FROM distritos ORDER BY id")
    fun distritos(): List<DistritoEntity>

    @Query("SELECT * FROM distrito_segmentos")
    fun segmentosDeDistritos(): List<DistritoSegmentoEntity>

    /** Datos del propio catálogo (fuente, crédito de la licencia, fecha de los datos...). */
    @Query("SELECT * FROM catalogo_info")
    fun info(): List<CatalogoInfoEntity>
}
