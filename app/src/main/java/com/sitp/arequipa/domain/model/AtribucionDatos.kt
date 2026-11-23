package com.sitp.arequipa.domain.model

/**
 * Crédito que exige la licencia de una fuente de datos (texto visible y, opcionalmente, un enlace).
 * Lo define la fuente de datos, no la UI: si se cambia de proveedor, el crédito cambia solo.
 */
data class AtribucionDatos(
    val texto: String,
    val enlace: String?
)
