package com.sitp.arequipa.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.sitp.arequipa.domain.model.BusquedaHistorial
import com.sitp.arequipa.domain.model.RutaFavorita
import kotlinx.coroutines.tasks.await

/**
 * Fuente de datos remota para historial de búsquedas y favoritos del usuario en Firestore.
 */
class FirestoreHistorialSource {

    private val db = FirebaseFirestore.getInstance()

    // ── Historial ────────────────────────────────────────────────────────────────

    suspend fun guardarBusqueda(
        uid: String,
        origen: String,
        destino: String,
        preferencia: String,
        respuestaIA: String
    ) {
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

        // Registro global para el dashboard de administración
        db.collection("busquedas_global")
            .add(
                hashMapOf(
                    "origen" to origen,
                    "destino" to destino,
                    "preferencia" to preferencia,
                    "uid" to uid,
                    "fecha" to Timestamp.now()
                )
            )
            .await()
    }

    suspend fun cargarHistorial(uid: String): List<BusquedaHistorial> {
        val snapshot = db.collection("usuarios")
            .document(uid)
            .collection("busquedas")
            .orderBy("fecha", Query.Direction.DESCENDING)
            .get()
            .await()

        return snapshot.documents.map { doc ->
            BusquedaHistorial(
                id = doc.id,
                origen = doc.getString("origen") ?: "",
                destino = doc.getString("destino") ?: "",
                preferencia = doc.getString("preferencia") ?: "",
                respuestaIA = doc.getString("respuestaIA") ?: "",
                fecha = doc.getTimestamp("fecha")?.toDate()?.time
            )
        }
    }

    suspend fun eliminarBusqueda(uid: String, busquedaId: String) {
        db.collection("usuarios")
            .document(uid)
            .collection("busquedas")
            .document(busquedaId)
            .delete()
            .await()
    }

    // ── Favoritos ────────────────────────────────────────────────────────────────

    suspend fun verificarYActualizarFavorito(uid: String, origen: String, destino: String): Boolean {
        val busquedasSnapshot = db.collection("usuarios")
            .document(uid)
            .collection("busquedas")
            .whereEqualTo("origen", origen)
            .whereEqualTo("destino", destino)
            .get()
            .await()

        val frecuencia = busquedasSnapshot.size()

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
                return true // favorito nuevo guardado automáticamente
            } else {
                val docId = favoritosSnapshot.documents[0].id
                db.collection("usuarios")
                    .document(uid)
                    .collection("favoritos")
                    .document(docId)
                    .update("frecuencia", frecuencia, "fechaUltimoUso", Timestamp.now())
                    .await()
            }
        }
        return false
    }

    suspend fun cargarFavoritos(uid: String): List<RutaFavorita> {
        val snapshot = db.collection("usuarios")
            .document(uid)
            .collection("favoritos")
            .orderBy("frecuencia", Query.Direction.DESCENDING)
            .get()
            .await()

        return snapshot.documents.map { doc ->
            RutaFavorita(
                id = doc.id,
                origen = doc.getString("origen") ?: "",
                destino = doc.getString("destino") ?: "",
                nombre = doc.getString("nombre") ?: "",
                frecuencia = doc.getLong("frecuencia")?.toInt() ?: 0,
                fechaUltimoUso = doc.getTimestamp("fechaUltimoUso")?.toDate()?.time
            )
        }
    }

    suspend fun guardarFavoritoManual(uid: String, origen: String, destino: String, nombre: String) {
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
    }

    suspend fun eliminarFavorito(uid: String, favoritoId: String) {
        db.collection("usuarios")
            .document(uid)
            .collection("favoritos")
            .document(favoritoId)
            .delete()
            .await()
    }
}
