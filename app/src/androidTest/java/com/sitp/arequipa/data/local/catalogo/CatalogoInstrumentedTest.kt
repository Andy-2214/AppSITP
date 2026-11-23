package com.sitp.arequipa.data.local.catalogo

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sitp.arequipa.SITPApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pruebas en dispositivo con el catálogo REAL (assets/catalogo.db copiado por Room).
 *
 * Los casos esperados (assets de androidTest: casos_catalogo.json) los genera
 * scripts/catalogo/generar_casos_prueba.py, una implementación independiente en Python de las
 * mismas reglas. Si la app y la referencia coinciden en todos los casos, están bien la lógica,
 * las consultas de Room y la copia del asset.
 */
@RunWith(AndroidJUnit4::class)
class CatalogoInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val appContext = instrumentation.targetContext.applicationContext
    private val database = CatalogoDatabase.getDatabase(appContext)
    private val fuente = CatalogoLugaresSource(database.catalogoDao())

    private data class Caso(val grupo: String, val lat: Double, val lng: Double, val esperado: String)

    private fun casos(): List<Caso> {
        val json = instrumentation.context.assets.open("casos_catalogo.json").bufferedReader().use { it.readText() }
        val arreglo = JSONObject(json).getJSONArray("casos")
        return (0 until arreglo.length()).map {
            val c = arreglo.getJSONObject(it)
            Caso(c.getString("grupo"), c.getDouble("lat"), c.getDouble("lng"), c.getString("esperado"))
        }
    }

    private fun informar(clave: String, valor: String) {
        instrumentation.sendStatus(0, Bundle().apply { putString(clave, valor) })
    }

    private fun contar(tabla: String): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $tabla").use { c ->
            c.moveToFirst(); c.getInt(0)
        }

    @Test
    fun roomCopiaElAssetYAceptaSuEsquema() = runBlocking {
        // Si la estructura de catalogo.db no coincidiera con las entidades, Room lanzaría
        // IllegalStateException ("Pre-packaged database has an invalid schema") en esta primera consulta.
        val distritos = withContext(Dispatchers.IO) { database.catalogoDao().distritos() }
        assertEquals("distritos de la provincia", 29, distritos.size)
        withContext(Dispatchers.IO) {
            assertTrue("calles", contar("calles") > 5000)
            assertTrue("tramos de calle", contar("calle_segmentos") > 20000)
            assertTrue("lugares", contar("lugares") > 5000)
            assertTrue("bordes de distritos", contar("distrito_segmentos") > 1000)
            assertTrue("información del catálogo", contar("catalogo_info") >= 1)
            val version = database.openHelper.readableDatabase.version
            assertEquals("versión del catálogo", CatalogoDatabase.VERSION, version)
            informar("catalogo", "distritos=29 calles=${contar("calles")} lugares=${contar("lugares")} version=$version")
        }
    }

    @Test
    fun coincideConLaImplementacionDeReferenciaEnTodosLosCasos() = runBlocking {
        val lista = casos()
        assertTrue("debe haber casos de prueba", lista.size > 300)
        val errores = withContext(Dispatchers.IO) {
            lista.mapNotNull { caso ->
                val real = fuente.coordenadasANombre(caso.lat, caso.lng)
                if (real == caso.esperado) null else "(${caso.lat}, ${caso.lng}) [${caso.grupo}] esperado='${caso.esperado}' real='$real'"
            }
        }
        informar("referencia", "casos=${lista.size} coincidencias=${lista.size - errores.size} diferencias=${errores.size}")
        assertTrue(
            "${errores.size} de ${lista.size} casos no coinciden:\n" + errores.take(15).joinToString("\n"),
            errores.isEmpty()
        )
    }

    @Test
    fun integracionCompletaDesdeElContenedorDeLaApp() = runBlocking {
        // Recorre la cadena real de la app: caso de uso (dominio) → puerto LugaresRepository → catálogo → Room.
        val contenedor = (appContext as SITPApplication).container
        val muestra = casos().filter { it.grupo == "urbano" }.take(25)
        for (caso in muestra) {
            assertEquals(
                "(${caso.lat}, ${caso.lng})",
                caso.esperado,
                contenedor.obtenerNombreUbicacionUseCase(caso.lat, caso.lng)
            )
            assertEquals(caso.esperado, contenedor.lugaresRepository.coordenadasANombre(caso.lat, caso.lng))
        }
        informar("integracion", "casos=${muestra.size} OK por ObtenerNombreUbicacionUseCase y LugaresRepository")

        // El crédito de la fuente sale del catálogo (tabla catalogo_info), no del código de la app
        val atribucion = contenedor.obtenerAtribucionDatosUseCase()
        assertTrue("debe haber crédito de la fuente", atribucion != null && atribucion.texto.isNotBlank())
        informar("atribucion", "texto='${atribucion?.texto}' enlace='${atribucion?.enlace}'")
    }

    @Test
    fun respondeRapidoEnElDispositivo() = runBlocking {
        val lista = casos()
        withContext(Dispatchers.IO) {
            fuente.coordenadasANombre(lista[0].lat, lista[0].lng) // calentar: carga de distritos y caché de SQLite
            val tiempos = lista.map { caso ->
                val t0 = System.nanoTime()
                fuente.coordenadasANombre(caso.lat, caso.lng)
                (System.nanoTime() - t0) / 1_000_000.0
            }.sorted()
            val promedio = tiempos.average()
            val p95 = tiempos[(tiempos.size * 0.95).toInt().coerceAtMost(tiempos.size - 1)]
            val maximo = tiempos.last()
            informar("rendimiento", "consultas=${tiempos.size} promedio=%.2f ms p95=%.2f ms max=%.2f ms".format(promedio, p95, maximo))
            // Antes: 2 llamadas HTTP a Google por búsqueda (cientos de ms). Margen amplio para emuladores lentos.
            assertTrue("promedio $promedio ms", promedio < 50.0)
            assertTrue("p95 $p95 ms", p95 < 150.0)
        }
    }
}
