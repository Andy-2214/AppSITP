package com.sitp.arequipa.domain.model

/**
 * Lo que la IA entendió de la frase del usuario (ADR-011). La IA no calcula nada: solo extrae
 * los nombres; la app los busca en el catálogo y el usuario confirma los pines.
 */
data class ConsultaInterpretada(
    val origen: LugarPedido?,
    val destino: LugarPedido?,
    val preferencia: ModoBusqueda?,
    /** Pedidos que la app no puede cumplir («pasando por…», «evitando…»): se avisan al usuario. */
    val restriccionesNoSoportadas: List<String> = emptyList()
)

/** Un lugar tal como lo escribió el usuario, con una referencia opcional («cerca de…»). */
data class LugarPedido(val texto: String, val referencia: String? = null) {
    val esMiUbicacion: Boolean get() = texto.equals(MI_UBICACION, ignoreCase = true)

    companion object {
        /** Valor que usa la IA cuando el usuario sale desde donde está («desde aquí»). */
        const val MI_UBICACION = "MI_UBICACION"
    }
}

/** Resultado de interpretar una frase: qué poner en cada pin y qué avisar al usuario. */
data class ResultadoInterpretacion(
    val origen: PuntoInterpretado,
    val destino: PuntoInterpretado,
    val preferencia: ModoBusqueda?,
    val avisos: List<String>
)

/** Qué entendió la app para un pin. El usuario siempre confirma el pin antes de calcular. */
sealed class PuntoInterpretado {
    /** El usuario no lo mencionó: lo ubica moviendo el mapa. */
    object NoIndicado : PuntoInterpretado()
    /** «Desde aquí»: se usa el GPS. */
    object MiUbicacion : PuntoInterpretado()
    /** Candidatos del catálogo para lo que escribió (vacío si no se encontró nada). */
    data class Candidatos(val texto: String, val lugares: List<LugarEncontrado>) : PuntoInterpretado()
}

/** Un candidato del catálogo para un nombre buscado. */
data class LugarEncontrado(
    val nombre: String,
    /** Texto corto para distinguir candidatos: tipo de lugar y distrito («Mercado · Arequipa»). */
    val detalle: String,
    val coordenada: Coordenada,
    val puntaje: Int,
    val esCalle: Boolean = false
)
