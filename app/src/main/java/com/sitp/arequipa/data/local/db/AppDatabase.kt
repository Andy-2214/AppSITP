package com.sitp.arequipa.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sitp.arequipa.data.local.entity.CoordenadaEntity
import com.sitp.arequipa.data.local.entity.RutaEntity

@Database(
    entities = [RutaEntity::class, CoordenadaEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun rutaDao(): RutaDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "appsitp_database"
                )
                .fallbackToDestructiveMigration() // Útil en desarrollo si cambiamos el esquema
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
