package com.sitp.arequipa.domain.repository

import com.sitp.arequipa.domain.model.ConsultaInterpretada

/**
 * Puerto del asistente en lenguaje natural (ADR-011). El dominio no sabe qué proveedor ni qué modelo
 * lo implementa: cambiar de IA es cambiar el adaptador o su configuración, no este contrato.
 *
 * El asistente **solo interpreta**: nunca calcula rutas, tiempos ni costos.
 */
interface AsistenteRepository {

    /**
     * Convierte una frase («de la UNSA al mercado San Camilo») en origen, destino y preferencia.
     * @throws AsistenteNoDisponibleException si no hay conexión o la respuesta no sirve.
     */
    suspend fun interpretar(frase: String): ConsultaInterpretada

    /**
     * Elige cuál de los [candidatos] corresponde mejor a lo que escribió el usuario.
     * Devuelve el índice elegido, o null si ninguno corresponde o no se pudo consultar.
     */
    suspend fun elegirLugar(textoUsuario: String, candidatos: List<String>): Int?
}

/** El asistente no pudo responder (sin conexión, saturado o respuesta inválida). [message] es para el usuario. */
class AsistenteNoDisponibleException(mensaje: String) : Exception(mensaje)
