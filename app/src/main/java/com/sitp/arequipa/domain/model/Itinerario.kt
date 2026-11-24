package com.sitp.arequipa.domain.model

/** Qué prioriza el usuario al buscar (ADR-010). Con pasaje fijo, menos costo = menos combis. */
enum class ModoBusqueda {
    TIEMPO, COSTO;

    companion object {
        /** Lee la preferencia guardada como texto ("tiempo" / "costo"); cualquier otro valor es TIEMPO. */
        fun desdeTexto(texto: String?): ModoBusqueda =
            if (texto.equals("costo", ignoreCase = true)) COSTO else TIEMPO
    }

    /** Texto con el que la app guarda la preferencia en el historial. */
    fun comoTexto(): String = if (this == COSTO) "costo" else "tiempo"
}

enum class TipoTramo { CAMINATA, COMBI }

/** Sentido de la combi en el tramo: IDA o VUELTA del recorrido cargado en el panel. */
enum class Sentido { IDA, VUELTA }

/**
 * Una parte del viaje: caminar o ir en una combi.
 *
 * @property recorrido puntos por donde pasa la combi entre la subida y la bajada (para dibujar la guía).
 * @property alternativas otras rutas que sirven igual en ese tramo («sube a la que pase primero»).
 * @property intervaloMin cada cuántos minutos pasa la combi (o el grupo de rutas alternativas juntas).
 */
data class Tramo(
    val tipo: TipoTramo,
    val desde: Coordenada,
    val hasta: Coordenada,
    val minutos: Double,
    val metros: Double = 0.0,
    val codigoRuta: String? = null,
    val sentido: Sentido? = null,
    val esperaMin: Double = 0.0,
    val intervaloMin: Double = 0.0,
    val alternativas: List<String> = emptyList(),
    val recorrido: List<Coordenada> = emptyList()
)

/**
 * Una opción completa de viaje calculada por el motor de rutas.
 * Los tiempos están en minutos: [minutosEstimado] usa la espera esperada; [minutosMinimo] supone que la
 * combi llega justo al llegar al paradero y [minutosMaximo] que se acaba de ir (espera = intervalo completo).
 */
data class Itinerario(
    val tramos: List<Tramo>,
    val minutosEstimado: Double,
    val minutosMinimo: Double,
    val minutosMaximo: Double,
    val costo: Double
) {
    val tramosCombi: List<Tramo> get() = tramos.filter { it.tipo == TipoTramo.COMBI }
    val numeroCombis: Int get() = tramosCombi.size
    val codigos: List<String> get() = tramosCombi.mapNotNull { it.codigoRuta }

    /** Identifica la combinación de rutas (y sentidos) para no mostrar dos veces la misma. */
    val clave: String get() = tramosCombi.joinToString(">") { "${it.codigoRuta}:${it.sentido}" }
}

/**
 * Opciones de una búsqueda, ordenadas por cada modo (de 3 a 5 cuando existen).
 * [completa] es false mientras el motor sigue buscando más opciones en segundo plano.
 */
data class OpcionesBusqueda(
    val porTiempo: List<Itinerario>,
    val porCosto: List<Itinerario>,
    val completa: Boolean,
    val desdeCache: Boolean = false
) {
    fun de(modo: ModoBusqueda): List<Itinerario> = if (modo == ModoBusqueda.COSTO) porCosto else porTiempo
    val vacia: Boolean get() = porTiempo.isEmpty() && porCosto.isEmpty()

    companion object {
        val VACIA_COMPLETA = OpcionesBusqueda(emptyList(), emptyList(), completa = true)
    }
}
