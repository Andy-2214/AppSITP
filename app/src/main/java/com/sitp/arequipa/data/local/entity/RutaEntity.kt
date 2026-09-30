package com.sitp.arequipa.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rutas")
data class RutaEntity(
    @PrimaryKey val codigo: String,
    val nombre: String,
    val empresa: String,
    val avenidas: String,
    val avenidaVuelta: String,
    val color: String,
    val etiqueta: String = "",
    val frecuencia: String = "",
    val version: Long = 0L
)
