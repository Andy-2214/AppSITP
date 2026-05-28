package com.sitp.arequipa.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class BusquedaHistorial(
    val id: String = "",
    val origen: String = "",
    val destino: String = "",
    val preferencia: String = "",
    val respuestaIA: String = "",
    val fecha: Timestamp? = null
)

data class RutaFavorita(
    val id: String = "",
    val origen: String = "",
    val destino: String = "",
    val nombre: String = "",
    val frecuencia: Int = 0,
    val fechaUltimoUso: Timestamp? = null
)

class HistorialViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _historial = MutableStateFlow<List<BusquedaHistorial>>(emptyList())
    val historial: StateFlow<List<BusquedaHistorial>> = _historial

    private val _favoritos = MutableStateFlow<List<RutaFavorita>>(emptyList())
    val favoritos: StateFlow<List<RutaFavorita>> = _favoritos

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    // HU-22: Guardar búsqueda en historial
    fun guardarBusqueda(
        origen: String,
        destino: String,
        preferencia: String,
        respuestaIA: String
    ) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val busqueda = hashMapOf(
                    "origen" to origen,
                    "destino" to destino,
                    "preferencia" to preferencia,
                    "respuestaIA" to respuestaIA,
                    "fecha" to Timestamp.now()
                )
                db.collection("usuarios")
                    .document(uid)
                    .collection("busquedas")
                    .add(busqueda)
                    .await()

                // ── AGREGAR ESTO para el dashboard ──
                db.collection("busquedas_global")
                    .add(hashMapOf(
                        "origen" to origen,
                        "destino" to destino,
                        "preferencia" to preferencia,
                        "uid" to uid,
                        "fecha" to Timestamp.now()
                    ))
                    .await()
                // ────────────────────────────────────

                verificarFavorito(uid, origen, destino)

            } catch (e: Exception) {
                println("DEBUG historial error: ${e.message}")
            }
        }
    }

    // HU-22: Cargar historial del usuario
    fun cargarHistorial() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _loading.value = true
            try {
                val snapshot = db.collection("usuarios")
                    .document(uid)
                    .collection("busquedas")
                    .orderBy("fecha", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .get()
                    .await()

                _historial.value = snapshot.documents.map { doc ->
                    BusquedaHistorial(
                        id = doc.id,
                        origen = doc.getString("origen") ?: "",
                        destino = doc.getString("destino") ?: "",
                        preferencia = doc.getString("preferencia") ?: "",
                        respuestaIA = doc.getString("respuestaIA") ?: "",
                        fecha = doc.getTimestamp("fecha")
                    )
                }
            } catch (e: Exception) {
                println("DEBUG historial error: ${e.message}")
            } finally {
                _loading.value = false
            }
        }
    }

    // HU-22: Eliminar búsqueda del historial
    fun eliminarBusqueda(busquedaId: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("usuarios")
                    .document(uid)
                    .collection("busquedas")
                    .document(busquedaId)
                    .delete()
                    .await()
                _historial.value = _historial.value.filter { it.id != busquedaId }
            } catch (e: Exception) {
                println("DEBUG eliminar historial error: ${e.message}")
            }
        }
    }

    // HU-20: Verificar si la ruta debe guardarse como favorito automático
    private suspend fun verificarFavorito(uid: String, origen: String, destino: String) {
        try {
            val busquedasSnapshot = db.collection("usuarios")
                .document(uid)
                .collection("busquedas")
                .whereEqualTo("origen", origen)
                .whereEqualTo("destino", destino)
                .get()
                .await()

            val frecuencia = busquedasSnapshot.size()

            // Si tiene 3 o más búsquedas iguales, guardar como favorito automático
            if (frecuencia >= 3) {
                val favoritosSnapshot = db.collection("usuarios")
                    .document(uid)
                    .collection("favoritos")
                    .whereEqualTo("origen", origen)
                    .whereEqualTo("destino", destino)
                    .get()
                    .await()

                if (favoritosSnapshot.isEmpty) {
                    val favorito = hashMapOf(
                        "origen" to origen,
                        "destino" to destino,
                        "nombre" to "$origen → $destino",
                        "frecuencia" to frecuencia,
                        "fechaUltimoUso" to Timestamp.now(),
                        "automatico" to true
                    )
                    db.collection("usuarios")
                        .document(uid)
                        .collection("favoritos")
                        .add(favorito)
                        .await()
                    _favoritoAutoGuardado.value = true
                } else {
                    // Actualizar frecuencia
                    val docId = favoritosSnapshot.documents[0].id
                    db.collection("usuarios")
                        .document(uid)
                        .collection("favoritos")
                        .document(docId)
                        .update(
                            "frecuencia", frecuencia,
                            "fechaUltimoUso", Timestamp.now()
                        )
                        .await()
                }
            }
        } catch (e: Exception) {
            println("DEBUG favorito auto error: ${e.message}")
        }
    }

    // HU-20: Notificación de favorito guardado automáticamente
    private val _favoritoAutoGuardado = MutableStateFlow(false)
    val favoritoAutoGuardado: StateFlow<Boolean> = _favoritoAutoGuardado

    fun resetFavoritoAutoGuardado() {
        _favoritoAutoGuardado.value = false
    }

    // HU-20: Cargar favoritos
    fun cargarFavoritos() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _loading.value = true
            try {
                val snapshot = db.collection("usuarios")
                    .document(uid)
                    .collection("favoritos")
                    .orderBy("frecuencia", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .get()
                    .await()

                _favoritos.value = snapshot.documents.map { doc ->
                    RutaFavorita(
                        id = doc.id,
                        origen = doc.getString("origen") ?: "",
                        destino = doc.getString("destino") ?: "",
                        nombre = doc.getString("nombre") ?: "",
                        frecuencia = doc.getLong("frecuencia")?.toInt() ?: 0,
                        fechaUltimoUso = doc.getTimestamp("fechaUltimoUso")
                    )
                }
            } catch (e: Exception) {
                println("DEBUG favoritos error: ${e.message}")
            } finally {
                _loading.value = false
            }
        }
    }

    // HU-20: Guardar favorito manualmente
    fun guardarFavoritoManual(origen: String, destino: String, nombre: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val favorito = hashMapOf(
                    "origen" to origen,
                    "destino" to destino,
                    "nombre" to nombre,
                    "frecuencia" to 1,
                    "fechaUltimoUso" to Timestamp.now(),
                    "automatico" to false
                )
                db.collection("usuarios")
                    .document(uid)
                    .collection("favoritos")
                    .add(favorito)
                    .await()
                cargarFavoritos()
            } catch (e: Exception) {
                println("DEBUG guardar favorito error: ${e.message}")
            }
        }
    }

    // HU-20: Eliminar favorito
    fun eliminarFavorito(favoritoId: String) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("usuarios")
                    .document(uid)
                    .collection("favoritos")
                    .document(favoritoId)
                    .delete()
                    .await()
                _favoritos.value = _favoritos.value.filter { it.id != favoritoId }
            } catch (e: Exception) {
                println("DEBUG eliminar favorito error: ${e.message}")
            }
        }
    }
}