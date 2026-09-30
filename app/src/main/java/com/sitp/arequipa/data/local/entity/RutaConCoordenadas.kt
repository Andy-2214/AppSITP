package com.sitp.arequipa.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class RutaConCoordenadas(
    @Embedded val ruta: RutaEntity,
    @Relation(
        parentColumn = "codigo",
        entityColumn = "rutaCodigo"
    )
    val coordenadas: List<CoordenadaEntity>
)
