package com.sitp.arequipa.domain.usecase

import com.sitp.arequipa.domain.model.AtribucionDatos
import com.sitp.arequipa.domain.repository.LugaresRepository

/** Crédito que la app debe mostrar por la fuente de datos de los nombres de lugares (si su licencia lo exige). */
class ObtenerAtribucionDatosUseCase(private val lugaresRepository: LugaresRepository) {
    suspend operator fun invoke(): AtribucionDatos? = lugaresRepository.atribucion()
}
