# Licencia de los datos del catálogo

El archivo `app/src/main/assets/catalogo.db` contiene datos derivados de **OpenStreetMap**:

> © OpenStreetMap contributors — https://www.openstreetmap.org/copyright

Los datos de OpenStreetMap están disponibles bajo la **Open Database License (ODbL) 1.0**:
https://opendatacommons.org/licenses/odbl/1-0/

## Qué implica para este proyecto

1. **Atribución visible.** La app muestra «Nombres de calles: © OpenStreetMap contributors» junto a cada
   resultado de búsqueda, con enlace a la página de copyright de OSM. El texto y el enlace NO están en el código:
   `construir_catalogo.py` los guarda en la tabla `catalogo_info` y la app los lee a través del puerto
   `LugaresRepository` (`CreditoFuenteDatos` en `BusquedaSheet.kt`). Si se cambia de fuente de datos,
   basta con cambiar las constantes `FUENTE`, `ATRIBUCION` y `ENLACE_ATRIBUCION` del script.
2. **Compartir igual.** `catalogo.db` es una base de datos derivada de OSM y se distribuye dentro de la app.
   Por eso el propio `catalogo.db`, junto con los alias que el equipo le agregue, se publica bajo la misma
   licencia ODbL en este repositorio, al igual que los scripts que lo generan (`construir_catalogo.py`).
3. **El código de la app no cambia de licencia:** la ODbL aplica solo a la base de datos, no al software que la usa.

## Cómo se genera

Ver el docstring de `construir_catalogo.py` y el manual de la sección 3 del informe de ADR
(ADR-001: base separada; ADR-002: catálogo OSM en lugar de la Geocoding API de Google).
