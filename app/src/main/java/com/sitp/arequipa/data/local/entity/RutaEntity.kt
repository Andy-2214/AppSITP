package com.sitp.arequipa.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "rutas",
    foreignKeys = [
        ForeignKey(
            entity = CuencaEntity::class,
            parentColumns = ["cuenca"],
            childColumns = ["cuenca"]
        )
    ],
    indices = [Index(value = ["cuenca"])]
)
data class RutaEntity(
    @PrimaryKey val codigo: String,
    val codigoCombi: String,
    val cuenca: String,
    val avenidas: String,
    val avenidaVuelta: String,
    val etiqueta: String = "",
    val frecuencia: String = "",
    val version: Long = 0L
)
