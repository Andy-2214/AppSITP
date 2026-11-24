package com.sitp.arequipa.domain.model

/**
 * Parámetros del motor de rutas (ADR-010). Los valores por defecto son los aprobados por el Arquitecto;
 * si cambia alguno, cambia también la clave de la caché (las búsquedas guardadas se recalculan).
 */
data class ParametrosBusqueda(
    /** Espera = α × intervalo. 0,5 = combis parejas; 1 = llegadas al azar (Nguyen y Pallottino, 1988). */
    val alfaEspera: Double = 0.75,
    /** Intervalo entre combis cuando el campo `frecuencia` de la ruta está vacío. */
    val intervaloPorDefectoMin: Double = 10.0,
    /** Penalización por cada combi después de la primera (bajar, cruzar, volver a esperar). */
    val penalidadTransbordoMin: Double = 5.0,
    /** Minutos por kilómetro a bordo (el mismo factor que usaba el motor anterior). */
    val minutosPorKmEnCombi: Double = 3.5,
    val metrosPorMinutoCaminando: Double = 75.0,
    /** Las calles no van en línea recta: la caminata real es ~30 % más larga. */
    val factorRodeo: Double = 1.3,
    /** Distancia entre paraderos virtuales sobre el recorrido de cada combi. */
    val separacionParaderosM: Double = 200.0,
    /** Distancia máxima para caminar de una combi a otra en un transbordo. */
    val radioTransbordoM: Double = 300.0,
    /** Distancia máxima a pie desde el origen o hasta el destino (y la ampliada si no hay nada). */
    val radioAccesoM: Double = 1000.0,
    val radioAccesoAmpliadoM: Double = 2000.0,
    /** Si la vuelta empieza a más de esta distancia del final de la ida, se tratan como dos líneas. */
    val saltoMaximoIdaVueltaM: Double = 500.0,
    val maxCombis: Int = 3,
    val minOpciones: Int = 3,
    val maxOpciones: Int = 5,
    /** Opciones del modo elegido que se muestran antes de seguir buscando en segundo plano. */
    val primerasOpciones: Int = 2,
    /** Se descartan las opciones que tardan más de este factor × la mejor. */
    val factorMaximoTiempo: Double = 1.5,
    /** Una ruta es «atractiva» en un tramo si su viaje tarda como mucho este porcentaje más. */
    val toleranciaLineasAtractivas: Double = 0.10,
    /** Distancia máxima entre paraderos para considerar que dos rutas comparten subida y bajada. */
    val radioLineasAtractivasM: Double = 150.0,
    /** Tope de corridas de la búsqueda de alternativas (cada corrida prohíbe rutas ya usadas). */
    val maxCorridas: Int = 30,
    val pasajeSoles: Double = 1.30
) {
    companion object {
        /** Sube cuando cambia la forma de calcular, para invalidar la caché aunque los parámetros sean iguales. */
        const val VERSION_ALGORITMO = 1
    }
}
