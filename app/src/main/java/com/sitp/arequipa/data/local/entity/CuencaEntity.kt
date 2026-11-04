package com.sitp.arequipa.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cuencas")
data class CuencaEntity(
    @PrimaryKey val cuenca: String,
    val empresa: String,
    val color: String
)
