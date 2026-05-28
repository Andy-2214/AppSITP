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

data class Comentario(
    val id: String = "",
    val usuarioId: String = "",
    val nombreUsuario: String = "",
    val rutaId: String = "",
    val rutaNombre: String = "",
    val texto: String = "",
    val fecha: Timestamp? = null,
    val estado: String = "pendiente",
    val destacado: Boolean = false
)

class ComentarioViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _comentarios = MutableStateFlow<List<Comentario>>(emptyList())
    val comentarios: StateFlow<List<Comentario>> = _comentarios

    private val _comentarioState = MutableStateFlow<ComentarioState>(ComentarioState.Idle)
    val comentarioState: StateFlow<ComentarioState> = _comentarioState

    // HU-21: Publicar comentario
    fun publicarComentario(rutaId: String, rutaNombre: String, rutaCodigo: String, texto: String) {
        val user = auth.currentUser ?: return
        if (texto.length < 10) {
            _comentarioState.value = ComentarioState.Error(
                "Tu comentario es muy corto. Agrega más detalles para ayudar a otros ciudadanos."
            )
            return
        }

        viewModelScope.launch {
            try {
                _comentarioState.value = ComentarioState.Loading

                // Obtener nombre del usuario desde Firestore
                val userDoc = db.collection("usuarios")
                    .document(user.uid)
                    .get()
                    .await()
                val nombreUsuario = userDoc.getString("nombre") ?: "Usuario"

                val comentario = hashMapOf(
                    "usuarioId" to user.uid,
                    "nombreUsuario" to nombreUsuario,
                    "rutaId" to rutaId,
                    "rutaNombre" to rutaNombre,
                    "texto" to texto,
                    "fecha" to Timestamp.now(),
                    "estado" to "aprobado",
                    "rutaCodigo" to rutaCodigo,
                    "destacado" to false
                )

                db.collection("comentarios")
                    .add(comentario)
                    .await()

                _comentarioState.value = ComentarioState.Success

            } catch (e: Exception) {
                _comentarioState.value = ComentarioState.Error("Error al publicar: ${e.message}")
            }
        }
    }

    // HU-21: Cargar comentarios aprobados de una ruta
    fun cargarComentarios(rutaId: String) {
        viewModelScope.launch {
            try {
                val snapshot = db.collection("comentarios")
                    .whereEqualTo("rutaId", rutaId)
                    .whereEqualTo("estado", "aprobado")
                    .get()
                    .await()

                val lista = snapshot.documents.map { doc ->
                    Comentario(
                        id = doc.id,
                        usuarioId = doc.getString("usuarioId") ?: "",
                        nombreUsuario = doc.getString("nombreUsuario") ?: "",
                        rutaId = doc.getString("rutaId") ?: "",
                        rutaNombre = doc.getString("rutaNombre") ?: "",
                        texto = doc.getString("texto") ?: "",
                        fecha = doc.getTimestamp("fecha"),
                        estado = doc.getString("estado") ?: "pendiente",
                        destacado = doc.getBoolean("destacado") ?: false
                    )
                }.sortedWith(
                    compareByDescending<Comentario> { it.destacado }
                        .thenByDescending { it.fecha?.seconds }
                )

                _comentarios.value = lista
            } catch (e: Exception) {
                println("DEBUG comentarios error: ${e.message}")
            }
        }
    }

    fun resetState() {
        _comentarioState.value = ComentarioState.Idle
    }
}

sealed class ComentarioState {
    object Idle : ComentarioState()
    object Loading : ComentarioState()
    object Success : ComentarioState()
    data class Error(val mensaje: String) : ComentarioState()
}