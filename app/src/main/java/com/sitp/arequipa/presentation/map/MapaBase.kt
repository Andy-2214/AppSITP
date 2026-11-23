package com.sitp.arequipa.presentation.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import androidx.core.text.HtmlCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.sitp.arequipa.domain.model.Coordenada
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/*
 * Mapa base de la app (ADR-004): MapLibre dibuja el mapa de Arequipa desde los archivos de assets/mapa
 * (mapa .pmtiles + estilo + letras del propio Android). Funciona sin internet desde la primera vez.
 * El resto de la app solo usa los tipos de este archivo y [Coordenada]: no conoce a MapLibre.
 */

/** Línea a dibujar sobre el mapa (rutas, guía del viaje, caminatas). Se dibujan en el orden de la lista. */
data class LineaMapa(
    val puntos: List<Coordenada>,
    val colorArgb: Int,
    val anchoDp: Float,
    val opacidad: Float = 1f,
    val punteada: Boolean = false
)

/** Dónde se apoya el ícono sobre el punto: un pin con la punta abajo o una insignia centrada. */
enum class AnclaIcono(internal val valor: String) { ABAJO(Property.ICON_ANCHOR_BOTTOM), CENTRO(Property.ICON_ANCHOR_CENTER) }

/** Ícono sobre el mapa. [icono] es el nombre de una imagen del mapa de íconos que recibe [MapaBase]. */
data class MarcadorMapa(val posicion: Coordenada, val icono: String, val ancla: AnclaIcono = AnclaIcono.CENTRO)

data class PosicionCamara(val centro: Coordenada, val zoom: Double)

/** Rectángulo en el que debe quedar el centro de la cámara. */
data class LimitesMapa(val suroeste: Coordenada, val noreste: Coordenada)

/**
 * Permite mover la cámara desde fuera del mapa (botón "Mi ubicación", etc.). Si el mapa todavía no está
 * listo, el centrado queda pendiente y se aplica al cargar. Recuerda la última posición para que la
 * cámara no se reinicie al volver de otra pestaña.
 */
class ControlCamara {
    internal var mapa: MapLibreMap? = null
        set(valor) {
            field = valor
            if (valor != null) pendiente?.let { (p, z) -> centrar(p, z, animar = false) }
            pendiente = null
        }
    private var pendiente: Pair<Coordenada, Double>? = null
    internal var ultimaPosicion: PosicionCamara? = null

    fun centrar(punto: Coordenada, zoom: Double, animar: Boolean = true) {
        val m = mapa ?: run { pendiente = punto to zoom; return }
        val actualizacion = CameraUpdateFactory.newLatLngZoom(LatLng(punto.lat, punto.lng), zoom)
        if (animar) m.animateCamera(actualizacion, 600) else m.moveCamera(actualizacion)
    }

    internal fun guardarPosicion() {
        mapa?.cameraPosition?.let { c ->
            c.target?.let { ultimaPosicion = PosicionCamara(Coordenada(it.latitude, it.longitude), c.zoom) }
        }
    }
}

@Composable
fun MapaBase(
    modifier: Modifier = Modifier,
    camaraInicial: PosicionCamara,
    limites: LimitesMapa? = null,
    lineas: List<LineaMapa> = emptyList(),
    marcadores: List<MarcadorMapa> = emptyList(),
    iconos: Map<String, Bitmap> = emptyMap(),
    ubicacionUsuario: Coordenada? = null,
    controlCamara: ControlCamara = remember { ControlCamara() },
    onToqueMapa: (Coordenada) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val alTocar by rememberUpdatedState(onToqueMapa)
    var estilo by remember { mutableStateOf<Style?>(null) }
    var credito by remember { mutableStateOf<String?>(null) }

    val mapView = remember {
        MapLibre.getInstance(context)
        val inicio = controlCamara.ultimaPosicion ?: camaraInicial
        val opciones = MapLibreMapOptions.createFromAttributes(context)
            .camera(
                CameraPosition.Builder()
                    .target(LatLng(inicio.centro.lat, inicio.centro.lng))
                    .zoom(inicio.zoom)
                    .build()
            )
            .logoEnabled(false)
            .attributionEnabled(false) // el crédito se muestra como texto visible (abajo)
            .minZoomPreference(ZOOM_MINIMO)
        MapView(context, opciones).apply {
            onCreate(null)
            getMapAsync { mapa ->
                controlCamara.mapa = mapa
                limites?.let {
                    mapa.setLatLngBoundsForCameraTarget(
                        LatLngBounds.Builder()
                            .include(LatLng(it.suroeste.lat, it.suroeste.lng))
                            .include(LatLng(it.noreste.lat, it.noreste.lng))
                            .build()
                    )
                }
                mapa.addOnMapClickListener { p -> alTocar(Coordenada(p.latitude, p.longitude)); true }
                mapa.setStyle(Style.Builder().fromUri(ESTILO)) { s ->
                    prepararEstilo(context, s)
                    credito = leerCredito(s)
                    estilo = s
                }
            }
            // El crédito de la licencia lo declara la propia fuente de datos (ADR-007); se conoce cuando carga
            addOnSourceChangedListener {
                if (credito == null) estilo?.let { s -> credito = leerCredito(s) }
            }
        }
    }

    // El mapa sigue el ciclo de vida de la pantalla (libera la GPU al salir)
    DisposableEffect(lifecycle) {
        val observador = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observador)
        onDispose {
            lifecycle.removeObserver(observador)
            controlCamara.guardarPosicion()
            controlCamara.mapa = null
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(estilo, iconos) {
        val s = estilo ?: return@LaunchedEffect
        iconos.forEach { (nombre, bitmap) -> if (s.getImage(nombre) == null) s.addImage(nombre, bitmap) }
    }
    LaunchedEffect(estilo, lineas) {
        estilo?.getSourceAs<GeoJsonSource>(FUENTE_LINEAS)?.setGeoJson(coleccionLineas(lineas))
    }
    LaunchedEffect(estilo, marcadores, iconos) {
        val s = estilo ?: return@LaunchedEffect
        // Solo los íconos ya registrados (evita avisos de imagen faltante)
        val visibles = marcadores.filter { s.getImage(it.icono) != null }
        s.getSourceAs<GeoJsonSource>(FUENTE_MARCADORES)?.setGeoJson(coleccionMarcadores(visibles))
    }
    LaunchedEffect(estilo, ubicacionUsuario) {
        val fuente = estilo?.getSourceAs<GeoJsonSource>(FUENTE_UBICACION) ?: return@LaunchedEffect
        fuente.setGeoJson(
            FeatureCollection.fromFeatures(
                listOfNotNull(ubicacionUsuario?.let { Feature.fromGeometry(Point.fromLngLat(it.lng, it.lat)) })
            )
        )
    }

    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.matchParentSize())
        credito?.let {
            Text(
                text = it,
                fontSize = 10.sp,
                color = Color(0xFF44566D),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(Color.White.copy(alpha = 0.8f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

// ── Capas propias de la app, encima del mapa base ─────────────────────────────

private const val ESTILO = "asset://mapa/estilo.json"
private const val ZOOM_MINIMO = 9.0
private const val FUENTE_LINEAS = "app-lineas"
private const val FUENTE_MARCADORES = "app-marcadores"
private const val FUENTE_UBICACION = "app-ubicacion"

private fun prepararEstilo(context: Context, s: Style) {
    registrarPuntosDeLugares(context, s)
    s.addSource(GeoJsonSource(FUENTE_LINEAS))
    s.addSource(GeoJsonSource(FUENTE_MARCADORES))
    s.addSource(GeoJsonSource(FUENTE_UBICACION))

    val comunes = arrayOf(
        PropertyFactory.lineColor(Expression.toColor(Expression.get("color"))),
        PropertyFactory.lineWidth(Expression.get("ancho")),
        PropertyFactory.lineOpacity(Expression.get("opacidad")),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        PropertyFactory.lineSortKey(Expression.get("orden"))
    )
    s.addLayer(
        LineLayer("app-lineas", FUENTE_LINEAS)
            .withFilter(Expression.eq(Expression.get("punteada"), Expression.literal(false)))
            .withProperties(*comunes)
    )
    s.addLayer(
        LineLayer("app-lineas-punteadas", FUENTE_LINEAS)
            .withFilter(Expression.eq(Expression.get("punteada"), Expression.literal(true)))
            .withProperties(*comunes, PropertyFactory.lineDasharray(arrayOf(0f, 2f)))
    )
    // Punto azul de "mi ubicación" (GPS del teléfono)
    s.addLayer(
        CircleLayer("app-ubicacion-halo", FUENTE_UBICACION).withProperties(
            PropertyFactory.circleRadius(16f),
            PropertyFactory.circleColor("#1A73E8"),
            PropertyFactory.circleOpacity(0.18f)
        )
    )
    s.addLayer(
        CircleLayer("app-ubicacion", FUENTE_UBICACION).withProperties(
            PropertyFactory.circleRadius(7f),
            PropertyFactory.circleColor("#1A73E8"),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(2.5f)
        )
    )
    s.addLayer(
        SymbolLayer("app-marcadores", FUENTE_MARCADORES).withProperties(
            PropertyFactory.iconImage(Expression.get("icono")),
            PropertyFactory.iconAnchor(Expression.get("ancla")),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            PropertyFactory.symbolSortKey(Expression.get("orden"))
        )
    )
}

/** Círculos de color de los 7 grupos de lugares ("punto-salud", ...), que usa el estilo del mapa base. */
private fun registrarPuntosDeLugares(context: Context, s: Style) {
    val colores = runCatching {
        JSONObject(context.assets.open("mapa/grupos_lugares.json").bufferedReader().use { it.readText() })
    }.getOrNull() ?: return
    val d = context.resources.displayMetrics.density
    val lado = (11 * d).toInt()
    val pintura = Paint(Paint.ANTI_ALIAS_FLAG)
    for (grupo in colores.keys()) {
        val bmp = createBitmap(lado, lado)
        val c = Canvas(bmp)
        pintura.color = android.graphics.Color.WHITE
        c.drawCircle(lado / 2f, lado / 2f, lado / 2f, pintura)
        pintura.color = colores.getString(grupo).toColorInt()
        c.drawCircle(lado / 2f, lado / 2f, lado / 2f - 1.6f * d, pintura)
        s.addImage("punto-$grupo", bmp)
    }
}

/** Créditos que declaran las fuentes del mapa (texto visible que exige su licencia), sin HTML. */
private fun leerCredito(s: Style): String? =
    s.sources.mapNotNull { it.attribution?.takeIf(String::isNotBlank) }
        .map { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim() }
        .distinct()
        .joinToString(" · ")
        .ifBlank { null }

private fun colorHex(argb: Int) = String.format("#%06X", argb and 0xFFFFFF)

private fun coleccionLineas(lineas: List<LineaMapa>): FeatureCollection =
    FeatureCollection.fromFeatures(lineas.mapIndexedNotNull { i, l ->
        if (l.puntos.size < 2) return@mapIndexedNotNull null
        Feature.fromGeometry(LineString.fromLngLats(l.puntos.map { Point.fromLngLat(it.lng, it.lat) })).apply {
            addStringProperty("color", colorHex(l.colorArgb))
            addNumberProperty("ancho", l.anchoDp)
            addNumberProperty("opacidad", l.opacidad)
            addBooleanProperty("punteada", l.punteada)
            addNumberProperty("orden", i)
        }
    })

private fun coleccionMarcadores(marcadores: List<MarcadorMapa>): FeatureCollection =
    FeatureCollection.fromFeatures(marcadores.mapIndexed { i, m ->
        Feature.fromGeometry(Point.fromLngLat(m.posicion.lng, m.posicion.lat)).apply {
            addStringProperty("icono", m.icono)
            addStringProperty("ancla", m.ancla.valor)
            addNumberProperty("orden", i)
        }
    })
