package com.sitp.arequipa.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.sitp.arequipa.data.local.entity.CoordenadaEntity
import com.sitp.arequipa.data.local.entity.CuencaEntity
import com.sitp.arequipa.data.local.entity.RutaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RutaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertarCuencas(cuencas: List<CuencaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertarRutas(rutas: List<RutaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertarCoordenadas(coordenadas: List<CoordenadaEntity>)

    @Transaction
    @Query("SELECT * FROM rutas")
    fun obtenerTodasLasRutasConCoordenadas(): Flow<List<com.sitp.arequipa.data.local.entity.RutaConCoordenadas>>

    @Query("SELECT * FROM rutas")
    fun obtenerRutasSync(): List<RutaEntity>

    @Query("SELECT * FROM cuencas")
    fun obtenerCuencasSync(): List<CuencaEntity>

    @Query("SELECT * FROM coordenadas WHERE rutaCodigo = :rutaCodigo ORDER BY orden ASC")
    fun obtenerCoordenadasDeRuta(rutaCodigo: String): List<CoordenadaEntity>

    @Query("DELETE FROM rutas")
    fun limpiarRutas()

    @Query("DELETE FROM coordenadas")
    fun limpiarCoordenadas()

    @Query("DELETE FROM cuencas")
    fun limpiarCuencas()

    @Transaction
    fun actualizarCache(
        cuencas: List<CuencaEntity>,
        rutas: List<RutaEntity>,
        coordenadas: List<CoordenadaEntity>
    ) {
        // Borrar de hijos a padres e insertar de padres a hijos (llaves foraneas)
        limpiarCoordenadas()
        limpiarRutas()
        limpiarCuencas()
        insertarCuencas(cuencas)
        insertarRutas(rutas)
        insertarCoordenadas(coordenadas)
    }
}
