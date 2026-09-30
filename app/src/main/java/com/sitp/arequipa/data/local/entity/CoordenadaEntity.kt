package com.sitp.arequipa.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "coordenadas",
    foreignKeys = [
        ForeignKey(
            entity = RutaEntity::class,
            parentColumns = ["codigo"],
            childColumns = ["rutaCodigo"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["rutaCodigo"])]
)
data class CoordenadaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val rutaCodigo: String,
    val lat: Double,
    val lng: Double,
    val tipo: String, // "IDA" o "VUELTA"
    val orden: Int
)
