"""Genera el catálogo de OpenStreetMap (provincia de Arequipa) que usa la app sin internet.

Uso:
    python -I -X utf8 scripts/catalogo/construir_catalogo.py <carpeta_json> <carpeta_salida> [--descargar]

    <carpeta_json>   Carpeta con calles.json, lugares.json y distritos.json descargados de Overpass
                     (ver el manual del informe de ADR, sección 3).
    <carpeta_salida> Donde se escribe catalogo.db; normalmente app/src/main/assets
    --descargar      Descarga primero los tres JSON desde Overpass (con reintentos) en <carpeta_json>.

La estructura de las tablas NO se escribe a mano: se toma del esquema que exporta Room en
app/schemas/com.sitp.arequipa.data.local.catalogo.CatalogoDatabase/<version>.json.
Así la base siempre coincide con las entidades Kotlin y Room la acepta al copiarla.
Para actualizar el catálogo: subir la versión de CatalogoDatabase, compilar (genera el nuevo
<version>.json) y volver a correr este script.

Datos © OpenStreetMap contributors, licencia ODbL (ver LICENCIA_DATOS.md).
"""
import argparse
import json
import math
import os
import sqlite3
import sys
import time
import urllib.parse
import urllib.request

RAIZ_REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DIR_ESQUEMA = os.path.join(RAIZ_REPO, "app", "schemas", "com.sitp.arequipa.data.local.catalogo.CatalogoDatabase")
NOMBRE_DB = "catalogo.db"

# Área de la provincia de Arequipa en Overpass = 3 600 000 000 + id de la relación 1887794
AREA_PROVINCIA = 3601887794
CONSULTAS = {
    "calles.json": f'[out:json][timeout:300];area({AREA_PROVINCIA})->.a;way(area.a)["highway"]["name"];out tags geom;',
    "lugares.json": f'[out:json][timeout:300];area({AREA_PROVINCIA})->.a;'
                    f'nwr(area.a)["name"][~"^(amenity|shop|leisure|tourism|place|healthcare|office|public_transport|historic|landuse)$"~"."];'
                    f'out tags center;',
    "distritos.json": f'[out:json][timeout:300];area({AREA_PROVINCIA})->.a;'
                      f'rel(area.a)["boundary"="administrative"]["admin_level"="8"];out geom;',
}
OVERPASS = "https://overpass-api.de/api/interpreter"

# Misma proyección que GeoUtils.kt: metros por grado de latitud (R = 6 371 000 m)
M_POR_GRADO = 111_195.0
TOLERANCIA_CALLE_M = 5.0       # simplificación de recorridos de calles
TOLERANCIA_DISTRITO_M = 20.0   # simplificación de límites de distritos
MAX_SEGMENTO_M = 50.0          # ningún tramo de calle guardado supera esta longitud (la app usa 55 m de margen)
CLAVES_TIPO = ("amenity", "shop", "leisure", "tourism", "place", "healthcare", "office",
               "public_transport", "historic", "landuse")
# Lugares demasiado amplios para servir de referencia ("cerca de Arequipa")
PLACE_EXCLUIDOS = {"city", "town", "state", "region", "province", "county", "country", "municipality"}

# Datos de la fuente que se guardan en la tabla catalogo_info. La app los muestra tal cual
# (crédito exigido por la licencia) sin conocer al proveedor: si cambia la fuente, se cambian aquí.
FUENTE = "OpenStreetMap"
ATRIBUCION = "© OpenStreetMap contributors"
ENLACE_ATRIBUCION = "https://www.openstreetmap.org/copyright"
LICENCIA = "ODbL 1.0"


def e6(grados):
    """Grados → millonésimas de grado (entero), el formato de coordenadas del catálogo (~0,1 m)."""
    return int(round(grados * 1_000_000))


# ---------------------------------------------------------------- descarga
def descargar(carpeta):
    os.makedirs(carpeta, exist_ok=True)
    for archivo, consulta in CONSULTAS.items():
        destino = os.path.join(carpeta, archivo)
        for intento, espera in enumerate((0, 30, 60, 120, 240), start=1):
            if espera:
                print(f"  servidor ocupado; reintento {intento} en {espera} s...")
                time.sleep(espera)
            try:
                datos = urllib.parse.urlencode({"data": consulta}).encode()
                req = urllib.request.Request(OVERPASS, data=datos, headers={"User-Agent": "AppSITP-catalogo/1.0"})
                with urllib.request.urlopen(req, timeout=400) as r:
                    cuerpo = r.read()
                elementos = json.loads(cuerpo)["elements"]
                if not elementos:
                    raise ValueError("respuesta sin elementos")
                with open(destino, "wb") as f:
                    f.write(cuerpo)
                print(f"  {archivo}: {len(elementos)} elementos ({len(cuerpo) / 1e6:.1f} MB)")
                break
            except Exception as e:  # 429/502/504 del servidor público, JSON inválido, etc.
                print(f"  {archivo}: intento {intento} falló ({e})")
        else:
            sys.exit(f"No se pudo descargar {archivo}. Intentar más tarde.")


# ---------------------------------------------------------------- geometría
def a_metros(lat, lng, lat0, lng0):
    return ((lng - lng0) * M_POR_GRADO * math.cos(math.radians(lat0)), (lat - lat0) * M_POR_GRADO)


def dist_punto_segmento(p, a, b):
    (px, py), (ax, ay), (bx, by) = p, a, b
    dx, dy = bx - ax, by - ay
    largo2 = dx * dx + dy * dy
    t = 0.0 if largo2 == 0 else max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / largo2))
    return math.hypot(px - (ax + t * dx), py - (ay + t * dy))


def simplificar(pts, tol_m):
    """Douglas-Peucker en metros. pts: lista de (lat, lng)."""
    if len(pts) < 3:
        return pts
    lat0, lng0 = pts[0]
    m = [a_metros(la, ln, lat0, lng0) for la, ln in pts]
    conservar = [False] * len(pts)
    conservar[0] = conservar[-1] = True
    pila = [(0, len(pts) - 1)]
    while pila:
        i, j = pila.pop()
        dmax, k = 0.0, -1
        for x in range(i + 1, j):
            d = dist_punto_segmento(m[x], m[i], m[j])
            if d > dmax:
                dmax, k = d, x
        if dmax > tol_m:
            conservar[k] = True
            pila += [(i, k), (k, j)]
    return [p for p, c in zip(pts, conservar) if c]


def tramos_cortos(pts, max_m):
    """Divide cada tramo en partes de como máximo max_m metros. Devuelve [(lat1, lng1, lat2, lng2)]."""
    out = []
    for (la1, ln1), (la2, ln2) in zip(pts, pts[1:]):
        x, y = a_metros(la2, ln2, la1, ln1)
        partes = max(1, math.ceil(math.hypot(x, y) / max_m))
        for k in range(partes):
            t0, t1 = k / partes, (k + 1) / partes
            out.append((la1 + (la2 - la1) * t0, ln1 + (ln2 - ln1) * t0,
                        la1 + (la2 - la1) * t1, ln1 + (ln2 - ln1) * t1))
    return out


# ---------------------------------------------------------------- esquema de Room
def cargar_esquema():
    if not os.path.isdir(DIR_ESQUEMA):
        sys.exit(f"No existe {DIR_ESQUEMA}. Compila la app primero (genera el esquema de Room).")
    versiones = sorted(int(f[:-5]) for f in os.listdir(DIR_ESQUEMA) if f.endswith(".json") and f[:-5].isdigit())
    if not versiones:
        sys.exit(f"No hay archivos <version>.json en {DIR_ESQUEMA}.")
    with open(os.path.join(DIR_ESQUEMA, f"{versiones[-1]}.json"), encoding="utf-8") as f:
        db = json.load(f)["database"]
    sentencias, columnas = [], {}
    for e in db["entities"]:
        tabla = e["tableName"]
        sentencias.append(e["createSql"].replace("${TABLE_NAME}", tabla))
        for idx in e.get("indices", []):
            sentencias.append(idx["createSql"].replace("${TABLE_NAME}", tabla))
        columnas[tabla] = [c["columnName"] for c in e["fields"]]
    return db["version"], sentencias, columnas


def insertar(cur, columnas, tabla, filas):
    """Inserta dicts validando que las columnas sean exactamente las de la entidad Room."""
    cols = columnas[tabla]
    for fila in filas:
        if set(fila) != set(cols) - ({"id"} if "id" not in fila else set()):
            sys.exit(f"Columnas de '{tabla}' no coinciden con la entidad Room: {sorted(fila)} vs {cols}")
        nombres = [c for c in cols if c in fila]
        cur.execute(f"INSERT INTO {tabla} ({','.join(nombres)}) VALUES ({','.join('?' * len(nombres))})",
                    [fila[c] for c in nombres])


# ---------------------------------------------------------------- construcción
def cargar(carpeta, archivo):
    """Devuelve (elementos, fecha de los datos) de una respuesta de Overpass."""
    ruta = os.path.join(carpeta, archivo)
    with open(ruta, encoding="utf-8") as f:
        try:
            datos = json.load(f)
            return datos["elements"], (datos.get("osm3s") or {}).get("timestamp_osm_base", "")
        except (json.JSONDecodeError, KeyError):
            sys.exit(f"{ruta} no es una respuesta válida de Overpass (¿servidor ocupado?). Descárgalo de nuevo.")


def llenar(ruta_db, version, sentencias, columnas, calles, lugares, distritos, fecha_datos):
    """Crea la base en ruta_db con la estructura de Room y la llena. Devuelve estadísticas."""
    db = sqlite3.connect(ruta_db)
    cur = db.cursor()
    for s in sentencias:
        cur.execute(s)

    st = {"calles": 0, "segmentos_calle": 0, "lugares": 0, "lugares_excluidos": 0, "distritos": 0, "segmentos_distrito": 0}

    # Calles: un registro por vía con nombre + sus tramos cortos (≤ 50 m), indexados por su punto inicial
    for i, w in enumerate(calles, start=1):
        geom = [(g["lat"], g["lon"]) for g in w.get("geometry", []) if g]
        if len(geom) < 2:
            continue
        insertar(cur, columnas, "calles", [{"id": i, "idOrigen": w["id"], "nombre": w["tags"]["name"].strip(),
                                            "tipo": w["tags"].get("highway", "")}])
        tramos = tramos_cortos(simplificar(geom, TOLERANCIA_CALLE_M), MAX_SEGMENTO_M)
        insertar(cur, columnas, "calle_segmentos", [
            {"calleId": i, "lat1E6": e6(a), "lng1E6": e6(b), "lat2E6": e6(c), "lng2E6": e6(d)}
            for a, b, c, d in tramos])
        st["calles"] += 1
        st["segmentos_calle"] += len(tramos)

    # Lugares de referencia (sin los demasiado amplios)
    for i, e in enumerate(lugares, start=1):
        t = e["tags"]
        lat = e.get("lat", (e.get("center") or {}).get("lat"))
        lng = e.get("lon", (e.get("center") or {}).get("lon"))
        if lat is None or lng is None or not t.get("name", "").strip():
            continue
        if t.get("place") in PLACE_EXCLUIDOS:
            st["lugares_excluidos"] += 1
            continue
        tipo = next((f"{k}={t[k]}" for k in CLAVES_TIPO if k in t), "")
        insertar(cur, columnas, "lugares", [{"id": i, "idOrigen": e["id"], "nombre": t["name"].strip(),
                                             "tipo": tipo, "latE6": e6(lat), "lngE6": e6(lng)}])
        st["lugares"] += 1

    # Distritos: todos los tramos del borde (outer e inner). La app usa la regla par-impar,
    # que no necesita que los tramos estén ordenados ni unidos.
    for i, r in enumerate(distritos, start=1):
        segs = []
        for m in r.get("members", []):
            if m.get("type") == "way" and m.get("geometry"):
                pts = simplificar([(g["lat"], g["lon"]) for g in m["geometry"] if g], TOLERANCIA_DISTRITO_M)
                segs += [(a[0], a[1], b[0], b[1]) for a, b in zip(pts, pts[1:])]
        if not segs:
            continue
        segs = [(e6(a), e6(b), e6(c), e6(d)) for a, b, c, d in segs]
        lats = [s[0] for s in segs] + [s[2] for s in segs]
        lngs = [s[1] for s in segs] + [s[3] for s in segs]
        insertar(cur, columnas, "distritos", [{"id": i, "nombre": r["tags"].get("name", "").strip(),
                                               "minLatE6": min(lats), "maxLatE6": max(lats),
                                               "minLngE6": min(lngs), "maxLngE6": max(lngs)}])
        insertar(cur, columnas, "distrito_segmentos", [
            {"distritoId": i, "lat1E6": a, "lng1E6": b, "lat2E6": c, "lng2E6": d} for a, b, c, d in segs])
        st["distritos"] += 1
        st["segmentos_distrito"] += len(segs)

    # Datos del propio catálogo: la app lee "atribucion" y "enlace_atribucion" para mostrar el crédito
    insertar(cur, columnas, "catalogo_info", [{"clave": k, "valor": v} for k, v in (
        ("fuente", FUENTE), ("atribucion", ATRIBUCION), ("enlace_atribucion", ENLACE_ATRIBUCION),
        ("licencia", LICENCIA), ("fecha_datos", fecha_datos))])

    db.commit()
    cur.execute(f"PRAGMA user_version = {int(version)}")  # Room compara esta versión con la de CatalogoDatabase
    db.commit()
    cur.execute("VACUUM")
    db.close()
    return st


def verificar(ruta_db, version, st):
    """Controles mínimos antes de publicar. Devuelve la lista de problemas (vacía si todo está bien)."""
    db = sqlite3.connect(ruta_db)
    uv = db.execute("PRAGMA user_version").fetchone()[0]
    atribucion = db.execute("SELECT valor FROM catalogo_info WHERE clave = 'atribucion'").fetchone()
    db.close()
    problemas = []
    if not atribucion or not atribucion[0].strip():
        problemas.append("falta el crédito de la fuente (catalogo_info.atribucion)")
    if uv != version:
        problemas.append(f"user_version={uv}, se esperaba {version}")
    if st["distritos"] != 29:
        problemas.append(f"se esperaban 29 distritos y hay {st['distritos']}")
    if st["calles"] < 5000 or st["lugares"] < 5000:
        problemas.append("muy pocas calles o lugares: revisar la descarga")
    return problemas


def construir(carpeta_json, carpeta_salida):
    version, sentencias, columnas = cargar_esquema()
    # Primero se validan los tres JSON: si alguno es un error del servidor, no se toca nada
    calles, fecha_datos = cargar(carpeta_json, "calles.json")
    lugares, _ = cargar(carpeta_json, "lugares.json")
    distritos, _ = cargar(carpeta_json, "distritos.json")

    os.makedirs(carpeta_salida, exist_ok=True)
    final = os.path.join(carpeta_salida, NOMBRE_DB)
    temporal = final + ".tmp"
    if os.path.exists(temporal):
        os.remove(temporal)
    # Se construye y verifica en un archivo temporal; el catálogo anterior solo se reemplaza si todo salió bien.
    # Si algo falla, se borra el temporal (si quedara en app/src/main/assets, terminaría dentro del APK).
    try:
        st = llenar(temporal, version, sentencias, columnas, calles, lugares, distritos, fecha_datos)
        problemas = verificar(temporal, version, st)
        if problemas:
            sys.exit("ERROR: " + "; ".join(problemas) + ". El catálogo anterior no se modificó.")
    except BaseException:
        if os.path.exists(temporal):
            os.remove(temporal)
        raise
    os.replace(temporal, final)

    print(f"Versión del catálogo (Room): {version} | datos de {FUENTE} al {fecha_datos or '¿?'}")
    for k, v in st.items():
        print(f"  {k}: {v}")
    print(f"Archivo: {final} ({os.path.getsize(final) / 1e6:.2f} MB)")
    print("OK: catálogo listo.")


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("carpeta_json")
    ap.add_argument("carpeta_salida")
    ap.add_argument("--descargar", action="store_true", help="descargar primero los JSON desde Overpass")
    args = ap.parse_args()
    if args.descargar:
        descargar(args.carpeta_json)
    construir(args.carpeta_json, args.carpeta_salida)
