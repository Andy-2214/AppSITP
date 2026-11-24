package com.sitp.arequipa.domain.repository

import com.sitp.arequipa.domain.model.OpcionesBusqueda

/**
 * Puerto de la caché de búsquedas (ADR-010): la misma búsqueda responde al instante sin recalcular.
 * La clave ya incluye la versión de las rutas y de los parámetros, así que nunca devuelve datos viejos.
 */
interface CacheBusquedasRepository {
    suspend fun leer(clave: String): OpcionesBusqueda?
    suspend fun guardar(clave: String, opciones: OpcionesBusqueda)
}
