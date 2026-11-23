package com.sitp.arequipa.data.local.catalogo

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sitp.arequipa.data.local.catalogo.entity.CalleEntity
import com.sitp.arequipa.data.local.catalogo.entity.CalleSegmentoEntity
import com.sitp.arequipa.data.local.catalogo.entity.CatalogoInfoEntity
import com.sitp.arequipa.data.local.catalogo.entity.DistritoEntity
import com.sitp.arequipa.data.local.catalogo.entity.DistritoSegmentoEntity
import com.sitp.arequipa.data.local.catalogo.entity.LugarEntity

/**
 * Catálogo local de calles, lugares y distritos de la provincia de Arequipa.
 * La fuente de los datos y el crédito que exige su licencia están en la tabla catalogo_info.
 *
 * - Base SEPARADA de AppDatabase (ADR-001): los cambios en las rutas no la afectan.
 * - De solo lectura: se copia desde assets/catalogo.db la primera vez que se usa.
 * - Para actualizarla: subir [VERSION], compilar (Room exporta el nuevo esquema en app/schemas)
 *   y regenerar assets/catalogo.db con scripts/catalogo/construir_catalogo.py.
 *   Al cambiar la versión, Room descarta la copia anterior y vuelve a copiar el asset
 *   (fallbackToDestructiveMigration), sin migraciones.
 */
@Database(
    entities = [
        CalleEntity::class,
        CalleSegmentoEntity::class,
        LugarEntity::class,
        DistritoEntity::class,
        DistritoSegmentoEntity::class,
        CatalogoInfoEntity::class
    ],
    version = CatalogoDatabase.VERSION,
    exportSchema = true
)
abstract class CatalogoDatabase : RoomDatabase() {

    abstract fun catalogoDao(): CatalogoDao

    companion object {
        /** Versión del catálogo. Subirla en cada actualización trimestral (ver manual del informe de ADR). */
        const val VERSION = 1
        const val ARCHIVO = "catalogo.db"

        @Volatile
        private var INSTANCE: CatalogoDatabase? = null

        fun getDatabase(context: Context): CatalogoDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CatalogoDatabase::class.java,
                    ARCHIVO
                )
                    .createFromAsset(ARCHIVO)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
