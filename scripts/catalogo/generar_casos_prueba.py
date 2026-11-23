"""Genera los casos de la prueba instrumentada del catálogo (app/src/androidTest/assets/casos_catalogo.json).

Es una IMPLEMENTACIÓN DE REFERENCIA, independiente de la app, de las reglas de
CatalogoLugaresSource.kt: lee el mismo catalogo.db y calcula qué texto debe devolver la app
para cientos de puntos. La prueba CatalogoInstrumentedTest compara, caso por caso, el resultado
real de la app (Room + asset + lógica Kotlin) con estos valores esperados.

Si cambias una regla en CatalogoLugaresSource.kt, cámbiala aquí también.
Cada vez que se regenera catalogo.db (actualización trimestral), vuelve a correr este script.

Uso:
    python -I -X utf8 scripts/catalogo/generar_casos_prueba.py [ruta_catalogo.db] [ruta_salida.json]
"""
import json
import math
import os
import random
import re
import sqlite3
import sys

RAIZ = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DB_POR_DEFECTO = os.path.join(RAIZ, "app", "src", "main", "assets", "catalogo.db")
SALIDA_POR_DEFECTO = os.path.join(RAIZ, "app", "src", "androidTest", "assets", "casos_catalogo.json")

# --- Mismas constantes que GeoUtils.kt y CatalogoLugaresSource.kt ---
M_POR_GRADO = 111_195.0
RADIO_CALLE_M = 100.0
RADIO_LUGAR_M = 200.0
MARGEN_TRAMO_M = 55.0
ZONA_POR_DEFECTO = "zona de Arequipa"
NOMBRE_GENERICO = re.compile(r"^(calle|pasaje|psje\.?|pje\.?|jir[oó]n|jr\.?|avenida|av\.?) +([0-9]+[a-z]?|[a-z])$",
                             re.IGNORECASE)
TIPOS_REFERENCIA = {
    "amenity=marketplace", "shop=mall", "shop=supermarket", "amenity=university", "amenity=college",
    "amenity=hospital", "amenity=clinic", "amenity=school", "amenity=bus_station", "amenity=townhall",
    "amenity=place_of_worship", "amenity=police", "amenity=fire_station", "amenity=library",
    "amenity=cinema", "amenity=theatre", "leisure=park", "leisure=stadium", "leisure=sports_centre",
    "place=square", "place=neighbourhood", "place=suburb", "place=quarter", "tourism=museum",
    "tourism=attraction", "tourism=viewpoint", "public_transport=station", "historic=monument",
}
UMBRAL_DUDOSO_M = 0.01  # casos a menos de 1 cm de un umbral o de un empate se descartan


def desde_e6(v):
    return v / 1_000_000.0


def area(lat, lng, radio):
    dlat = radio / M_POR_GRADO
    dlng = radio / (M_POR_GRADO * math.cos(math.radians(lat)))
    return (math.floor((lat - dlat) * 1_000_000), math.ceil((lat + dlat) * 1_000_000),
            math.floor((lng - dlng) * 1_000_000), math.ceil((lng + dlng) * 1_000_000))


def dist_punto_segmento(lat, lng, la1, ln1, la2, ln2):
    kx = M_POR_GRADO * math.cos(math.radians(lat))
    ax, ay = (ln1 - lng) * kx, (la1 - lat) * M_POR_GRADO
    bx, by = (ln2 - lng) * kx, (la2 - lat) * M_POR_GRADO
    dx, dy = bx - ax, by - ay
    l2 = dx * dx + dy * dy
    t = 0.0 if l2 == 0.0 else max(0.0, min(1.0, -(ax * dx + ay * dy) / l2))
    return math.hypot(ax + t * dx, ay + t * dy)


def dist_metros(lat, lng, lat2, lng2):
    kx = M_POR_GRADO * math.cos(math.radians(lat))
    return math.hypot((lng2 - lng) * kx, (lat2 - lat) * M_POR_GRADO)


def dentro(lat, lng, borde):
    d = False
    for la1, ln1, la2, ln2 in borde:
        if (la1 > lat) != (la2 > lat):
            if lng < ln1 + (lat - la1) * (ln2 - ln1) / (la2 - la1):
                d = not d
    return d


class Referencia:
    def __init__(self, ruta_db):
        self.db = sqlite3.connect(ruta_db)
        bordes = {}
        for did, a, b, c, d in self.db.execute("SELECT distritoId, lat1E6, lng1E6, lat2E6, lng2E6 FROM distrito_segmentos"):
            bordes.setdefault(did, []).append((desde_e6(a), desde_e6(b), desde_e6(c), desde_e6(d)))
        self.distritos = [(nom, desde_e6(a), desde_e6(b), desde_e6(c), desde_e6(d), bordes.get(i, []))
                          for i, nom, a, b, c, d in self.db.execute(
                              "SELECT id, nombre, minLatE6, maxLatE6, minLngE6, maxLngE6 FROM distritos ORDER BY id")]

    def nombrar(self, lat, lng):
        """Devuelve (texto, dudoso). dudoso=True si el caso está pegado a un umbral o a un empate."""
        dudoso = False
        distrito = next((n for n, a, b, c, d, borde in self.distritos
                         if a <= lat <= b and c <= lng <= d and dentro(lat, lng, borde)), None)

        a = area(lat, lng, RADIO_CALLE_M + MARGEN_TRAMO_M)
        calles = []
        for nom, x1, y1, x2, y2 in self.db.execute(
                "SELECT c.nombre, s.lat1E6, s.lng1E6, s.lat2E6, s.lng2E6 FROM calle_segmentos s JOIN calles c ON c.id = s.calleId "
                "WHERE s.lat1E6 BETWEEN ? AND ? AND s.lng1E6 BETWEEN ? AND ?", a):
            dd = dist_punto_segmento(lat, lng, desde_e6(x1), desde_e6(y1), desde_e6(x2), desde_e6(y2))
            if abs(dd - RADIO_CALLE_M) < UMBRAL_DUDOSO_M:
                dudoso = True
            if dd <= RADIO_CALLE_M:
                calles.append((dd, nom))
        calles.sort()
        if len(calles) > 1 and calles[0][1] != calles[1][1] and calles[1][0] - calles[0][0] < UMBRAL_DUDOSO_M:
            dudoso = True
        calle = calles[0][1] if calles else None

        texto = calle
        if calle is None or NOMBRE_GENERICO.fullmatch(calle.strip()):
            a = area(lat, lng, RADIO_LUGAR_M)
            lugares = []
            for nom, tipo, la, ln in self.db.execute(
                    "SELECT nombre, tipo, latE6, lngE6 FROM lugares WHERE latE6 BETWEEN ? AND ? AND lngE6 BETWEEN ? AND ?", a):
                dd = dist_metros(lat, lng, desde_e6(la), desde_e6(ln))
                if abs(dd - RADIO_LUGAR_M) < UMBRAL_DUDOSO_M:
                    dudoso = True
                if dd <= RADIO_LUGAR_M:
                    lugares.append((dd, nom, tipo in TIPOS_REFERENCIA))
            ref = sorted((d, n) for d, n, r in lugares if r)
            todos = sorted((d, n) for d, n, r in lugares)
            elegidos = ref or todos
            if len(elegidos) > 1 and elegidos[0][1] != elegidos[1][1] and elegidos[1][0] - elegidos[0][0] < UMBRAL_DUDOSO_M:
                dudoso = True
            if elegidos:
                texto = f"cerca de {elegidos[0][1]}"

        if texto is not None and distrito is not None:
            return f"{texto}, {distrito}", dudoso
        if texto is not None:
            return texto, dudoso
        return (distrito or ZONA_POR_DEFECTO), dudoso


def main():
    ruta_db = sys.argv[1] if len(sys.argv) > 1 else DB_POR_DEFECTO
    salida = sys.argv[2] if len(sys.argv) > 2 else SALIDA_POR_DEFECTO
    ref = Referencia(ruta_db)
    rnd = random.Random(2026)
    puntos = []

    # 1) Alrededor de lugares de referencia (donde la gente suele poner pines), zona urbana
    base = ref.db.execute("SELECT latE6, lngE6 FROM lugares WHERE latE6 BETWEEN -16500000 AND -16300000 "
                          "AND lngE6 BETWEEN -71640000 AND -71450000 ORDER BY id").fetchall()
    for la, ln in rnd.sample(base, 220):
        puntos.append(("urbano", desde_e6(la) + rnd.uniform(-0.002, 0.002), desde_e6(ln) + rnd.uniform(-0.002, 0.002)))
    # 2) Uniformes en el rectángulo de Arequipa metropolitana (incluye zonas sin calles)
    for _ in range(120):
        puntos.append(("metropolitano", rnd.uniform(-16.52, -16.30), rnd.uniform(-71.66, -71.42)))
    # 3) Provincia rural (desierto, quebradas)
    for _ in range(40):
        puntos.append(("rural", rnd.uniform(-16.80, -16.10), rnd.uniform(-72.20, -71.20)))
    # 4) Fuera de la provincia
    puntos += [("fuera", -12.0464, -77.0428), ("fuera", -13.5320, -71.9675), ("fuera", -15.8402, -70.0219),
               ("fuera", -17.0, -73.5)]

    casos, descartados = [], 0
    conteo = {"calle": 0, "cerca de": 0, "solo distrito": 0, ZONA_POR_DEFECTO: 0}
    for grupo, lat, lng in puntos:
        texto, dudoso = ref.nombrar(lat, lng)
        if dudoso:
            descartados += 1
            continue
        casos.append({"grupo": grupo, "lat": round(lat, 7), "lng": round(lng, 7), "esperado": texto})
    # La prueba usa las coordenadas redondeadas: recalcular sobre ellas para que coincidan exactamente
    finales = []
    for c in casos:
        texto, dudoso = ref.nombrar(c["lat"], c["lng"])
        if dudoso:
            descartados += 1
            continue
        c["esperado"] = texto
        finales.append(c)
        if texto == ZONA_POR_DEFECTO:
            conteo[ZONA_POR_DEFECTO] += 1
        elif texto.startswith("cerca de "):
            conteo["cerca de"] += 1
        elif "," in texto or texto in {d[0] for d in ref.distritos}:
            conteo["solo distrito" if texto in {d[0] for d in ref.distritos} else "calle"] += 1
        else:
            conteo["calle"] += 1

    os.makedirs(os.path.dirname(salida), exist_ok=True)
    with open(salida, "w", encoding="utf-8") as f:
        json.dump({"catalogo_bytes": os.path.getsize(ruta_db), "casos": finales}, f, ensure_ascii=False, indent=1)
    print(f"Casos generados: {len(finales)} (descartados por estar en un umbral o empate: {descartados})")
    print("Tipos de resultado:", conteo)
    print("Ejemplos:")
    for c in finales[:8]:
        print(f"  ({c['lat']}, {c['lng']}) [{c['grupo']}] -> {c['esperado']}")
    print(f"Archivo: {salida}")


if __name__ == "__main__":
    main()
