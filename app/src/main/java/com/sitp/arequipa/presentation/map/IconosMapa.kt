package com.sitp.arequipa.presentation.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.DrawableCompat
import com.sitp.arequipa.R

/** Nombres de los íconos fijos que la pantalla del mapa registra en [MapaBase]. */
object IconosMapa {
    const val PIN_ORIGEN = "pin-origen"
    const val PIN_DESTINO = "pin-destino"
    const val TRANSBORDO = "icono-transbordo"

    fun insigniaRuta(codigoCombi: String, colorHex: String) = "insignia-$codigoCombi-$colorHex"
}

/**
 * Pin nativo de Android (vector drawable ic_pin_mapa: borde y centro blancos, relleno negro) pintado del
 * color indicado: se dibuja el pin teñido y encima el original con mezcla LIGHTEN, así lo blanco sigue
 * blanco y lo negro toma el color.
 */
fun crearBitmapPin(context: Context, colorArgb: Int): Bitmap {
    fun dibujar(tinte: Int?): Bitmap {
        val d = ContextCompat.getDrawable(context, R.drawable.ic_pin_mapa)!!.mutate()
        tinte?.let { DrawableCompat.setTint(d, it) }
        val bmp = createBitmap(d.intrinsicWidth, d.intrinsicHeight)
        d.setBounds(0, 0, bmp.width, bmp.height)
        d.draw(Canvas(bmp))
        return bmp
    }
    val resultado = dibujar(colorArgb)
    Canvas(resultado).drawBitmap(dibujar(null), 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.LIGHTEN) })
    return resultado
}
