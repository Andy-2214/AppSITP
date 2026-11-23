package com.sitp.arequipa.presentation.historial

import com.sitp.arequipa.domain.model.RutaFavorita

/*
 * Textos que "Mi Actividad" muestra. El historial y los favoritos GUARDAN coordenadas ("-16.3971, -71.5371")
 * porque "Repetir búsqueda" las vuelve a leer; aquí solo se cambia lo que se ve por el nombre de la calle.
 */

/** Nombre del punto si ya se calculó; si no (o si el texto no es una coordenada), el texto guardado. */
fun textoPunto(guardado: String, nombres: Map<String, String>): String = nombres[guardado] ?: guardado

/**
 * Título del favorito. Los favoritos automáticos se guardan como "<origen> → <destino>" (coordenadas):
 * en ese caso se muestran los nombres. Un nombre puesto por el usuario se respeta tal cual.
 */
fun tituloFavorito(favorito: RutaFavorita, nombres: Map<String, String>): String =
    if (favorito.nombre == "${favorito.origen} → ${favorito.destino}") {
        "${textoPunto(favorito.origen, nombres)} → ${textoPunto(favorito.destino, nombres)}"
    } else {
        favorito.nombre
    }
