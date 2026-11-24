package com.sitp.arequipa.data.local.catalogo.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Tablas del catálogo local de calles, lugares y distritos (base catalogo.db, de solo lectura).
 * Las llena scripts/catalogo/construir_catalogo.py; la app nunca escribe en ellas.
 * De qué fuente vienen los datos (y qué crédito exige su licencia) está en la tabla catalogo_info.
 * Cualquier cambio aquí exige subir CatalogoDatabase.VERSION y regenerar catalogo.db (ver ADR-001).
 *
 * Las coordenadas se guardan en MILLONÉSIMAS DE GRADO (sufijo E6, p. ej. -16.391475 → -16391475):
 * precisión de ~0,1 m y casi la mitad de espacio que un REAL de 8 bytes. Convertir con GeoUtils.
 */

/** Una vía con nombre (calle, avenida, pasaje...). `idOrigen` = su identificador en la fuente de datos. */
@Entity(tableName = "calles", indices = [Index(value = ["nombre"])])
data class CalleEntity(
    @PrimaryKey val id: Int,
    val idOrigen: Long,
    val nombre: String,
    val tipo: String
)

/**
 * Tramo corto (≤ 50 m) del recorrido de una calle. Se indexa su punto inicial: como el tramo es corto,
 * basta buscar por área con 50 m de margen para no perder calles cuyos vértices originales estén lejos.
 */
@Entity(tableName = "calle_segmentos", indices = [Index(value = ["lat1E6", "lng1E6"])])
data class CalleSegmentoEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val calleId: Int,
    val lat1E6: Int,
    val lng1E6: Int,
    val lat2E6: Int,
    val lng2E6: Int
)

/** Lugar de referencia con nombre (mercado, colegio, parque, barrio...). `tipo` = "clave=valor" de la fuente. */
@Entity(
    tableName = "lugares",
    indices = [Index(value = ["latE6", "lngE6"]), Index(value = ["nombre"])]
)
data class LugarEntity(
    @PrimaryKey val id: Int,
    val idOrigen: Long,
    val nombre: String,
    val tipo: String,
    val latE6: Int,
    val lngE6: Int
)

/** Distrito con el rectángulo que lo contiene (para descartar rápido los que no aplican). */
@Entity(tableName = "distritos")
data class DistritoEntity(
    @PrimaryKey val id: Int,
    val nombre: String,
    val minLatE6: Int,
    val maxLatE6: Int,
    val minLngE6: Int,
    val maxLngE6: Int
)

/** Tramo del borde de un distrito. No necesitan estar ordenados: se usa la regla par-impar. */
@Entity(tableName = "distrito_segmentos", indices = [Index(value = ["distritoId"])])
data class DistritoSegmentoEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val distritoId: Int,
    val lat1E6: Int,
    val lng1E6: Int,
    val lat2E6: Int,
    val lng2E6: Int
)

/**
 * Datos sobre el propio catálogo, en formato clave → valor. Claves que usa la app:
 * - "atribucion": crédito que exige la licencia de la fuente (se muestra en la UI).
 * - "enlace_atribucion": página de la licencia o del crédito (opcional).
 * Otras claves informativas: "fuente", "licencia", "fecha_datos".
 */
@Entity(tableName = "catalogo_info")
data class CatalogoInfoEntity(
    @PrimaryKey val clave: String,
    val valor: String
)

/** Resultado de consulta: tramo de calle junto con el nombre de su calle. */
data class SegmentoCalle(
    val nombre: String,
    val lat1E6: Int,
    val lng1E6: Int,
    val lat2E6: Int,
    val lng2E6: Int
)

/** Resultado de consulta: una calle con un punto representativo (promedio de sus tramos), para buscarla por nombre. */
data class CalleCentro(
    val nombre: String,
    val tipo: String,
    val latE6: Int,
    val lngE6: Int
)
