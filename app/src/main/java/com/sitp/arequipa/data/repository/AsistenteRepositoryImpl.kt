package com.sitp.arequipa.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.sitp.arequipa.data.remote.ClienteChatIA
import com.sitp.arequipa.data.remote.InstruccionesIA
import com.sitp.arequipa.domain.model.ConsultaInterpretada
import com.sitp.arequipa.domain.repository.AsistenteNoDisponibleException
import com.sitp.arequipa.domain.repository.AsistenteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Adaptador del puerto [AsistenteRepository] con un modelo de lenguaje por API de chat (ADR-011).
 * Para cambiar de modelo o proveedor se cambia [com.sitp.arequipa.data.remote.ConfigIA]; esta clase no cambia.
 */
class AsistenteRepositoryImpl(
    private val cliente: ClienteChatIA = ClienteChatIA()
) : AsistenteRepository {

    /** Contexto de la ciudad que el administrador puede agregar en Firestore (opcional); se lee una vez. */
    @Volatile private var contextoCiudad: String? = null
    @Volatile private var contextoLeido = false

    override suspend fun interpretar(frase: String): ConsultaInterpretada {
        val respuesta = cliente.completar(InstruccionesIA.sistemaInterpretar(leerContexto()), frase.trim())
        return InstruccionesIA.leerInterpretacion(respuesta)
            ?: throw AsistenteNoDisponibleException("No entendí la consulta. Prueba con «de [lugar] a [lugar]».")
    }

    override suspend fun elegirLugar(textoUsuario: String, candidatos: List<String>): Int? = try {
        val respuesta = cliente.completar(InstruccionesIA.ELEGIR_LUGAR, InstruccionesIA.usuarioElegir(textoUsuario, candidatos))
        InstruccionesIA.leerIndice(respuesta, candidatos.size)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private suspend fun leerContexto(): String? {
        if (contextoLeido) return contextoCiudad
        contextoCiudad = try {
            withTimeoutOrNull(3_000) {
                FirebaseFirestore.getInstance().collection("prompt_ia").document("config").get().await()
                    .getString(CAMPO_CONTEXTO)?.takeIf { it.isNotBlank() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        contextoLeido = true
        return contextoCiudad
    }

    private companion object {
        const val CAMPO_CONTEXTO = "contexto_interprete"
    }
}
