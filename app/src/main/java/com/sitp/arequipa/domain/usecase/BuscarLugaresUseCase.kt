package com.sitp.arequipa.domain.usecase

import com.sitp.arequipa.domain.model.Coordenada
import com.sitp.arequipa.domain.model.LugarEncontrado
import com.sitp.arequipa.domain.repository.LugaresRepository

/** Barra «Buscar lugar»: busca por nombre en el catálogo local, sin IA y sin internet (ADR-011). */
class BuscarLugaresUseCase(private val lugaresRepository: LugaresRepository) {

    suspend operator fun invoke(texto: String, cerca: Coordenada? = null, limite: Int = 8): List<LugarEncontrado> {
        if (texto.trim().length < MINIMO_LETRAS) return emptyList()
        return lugaresRepository.buscarPorNombre(texto, cerca, limite)
    }

    companion object {
        const val MINIMO_LETRAS = 3
    }
}
