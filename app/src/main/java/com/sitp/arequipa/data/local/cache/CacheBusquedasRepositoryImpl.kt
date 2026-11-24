package com.sitp.arequipa.data.local.cache

import com.sitp.arequipa.data.json.Json
import com.sitp.arequipa.domain.model.OpcionesBusqueda
import com.sitp.arequipa.domain.repository.CacheBusquedasRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Caché de búsquedas (ADR-010): las últimas [MAXIMO] búsquedas en memoria y en un archivo JSON de la app,
 * para que sigan disponibles al volver a abrirla. La más antigua sale primero (LRU).
 * La clave ya incluye la versión de las rutas y de los parámetros: si algo cambia, la clave es otra.
 */
class CacheBusquedasRepositoryImpl(private val archivo: File) : CacheBusquedasRepository {

    private val mutex = Mutex()
    private var entradas: LinkedHashMap<String, OpcionesBusqueda>? = null

    override suspend fun leer(clave: String): OpcionesBusqueda? = mutex.withLock {
        val mapa = cargar()
        mapa[clave]?.also {
            // Marcar como usada recientemente
            mapa.remove(clave); mapa[clave] = it
        }
    }

    override suspend fun guardar(clave: String, opciones: OpcionesBusqueda) {
        mutex.withLock {
            val mapa = cargar()
            mapa.remove(clave)
            mapa[clave] = opciones
            while (mapa.size > MAXIMO) mapa.remove(mapa.keys.first())
            escribir(mapa)
        }
    }

    private suspend fun cargar(): LinkedHashMap<String, OpcionesBusqueda> {
        entradas?.let { return it }
        val leidas = withContext(Dispatchers.IO) {
            val resultado = LinkedHashMap<String, OpcionesBusqueda>()
            try {
                if (archivo.exists()) {
                    val raiz = Json.leer(archivo.readText(Charsets.UTF_8)) as? Map<*, *>
                    raiz?.forEach { (k, v) ->
                        val o = (v as? Map<*, *>)?.let(SerializadorOpciones::desdeMapa)
                        if (k is String && o != null) resultado[k] = o
                    }
                }
            } catch (e: Exception) {
                println("DEBUG CacheBusquedas: archivo de caché inválido, se empieza de cero (${e.message})")
            }
            resultado
        }
        entradas = leidas
        return leidas
    }

    private suspend fun escribir(mapa: Map<String, OpcionesBusqueda>) = withContext(Dispatchers.IO) {
        try {
            val texto = Json.escribir(mapa.mapValues { SerializadorOpciones.aMapa(it.value) })
            val temporal = File(archivo.parentFile, archivo.name + ".tmp")
            temporal.writeText(texto, Charsets.UTF_8)
            if (!temporal.renameTo(archivo)) {
                archivo.delete(); temporal.renameTo(archivo)
            }
        } catch (e: Exception) {
            println("DEBUG CacheBusquedas: no se pudo guardar la caché (${e.message})")
        }
    }

    private companion object {
        const val MAXIMO = 40
    }
}
