"""Genera el estilo de MapLibre (style.json) a partir de la paleta aprobada (estilo_mapa.json).

Uso (desde la raíz del repositorio):
    python -I -X utf8 scripts/mapa/generar_estilo.py app/src/main/assets/mapa/estilo.json --mapa mapa/arequipa.pmtiles

    --mapa     Ruta del .pmtiles dentro de assets (por defecto arequipa.pmtiles).
    --fuente   sistema (por defecto) | glifos.

- Capas del mapa: las del diseño arequipa.yml (calles, agua, rios, lugares_areas, lugares_nombres, zonas).
- Solo datos de OpenStreetMap: no se dibuja nada que no esté en los datos (p. ej. no hay "zona beige" inventada).
- Medidas: estilo_mapa.json se midió en capturas de un Pixel 8 Pro (densidad 3), así que los píxeles se
  dividen entre 3 para pasarlos a las unidades de MapLibre (píxeles independientes de la densidad).
- Letras: --fuente sistema usa la Roboto que ya trae Android (font-faces, 0 KB extra en el APK);
  --fuente glifos usa archivos de glifos .pbf incluidos en assets/glyphs. En ambos casos sin internet.
- Íconos de lugares: círculos de color por grupo ("punto-<grupo>"), que la app dibuja al cargar el estilo.
"""
import json
import os
import sys

AQUI = os.path.dirname(os.path.abspath(__file__))
E = json.load(open(os.path.join(AQUI, "estilo_mapa.json"), encoding="utf-8"))
DENSIDAD = 3.0
FUENTE = ["Roboto Regular"]
ATRIBUCION = "© OpenStreetMap contributors"

GRUPOS = {g["id"]: g for g in E["grupos_lugares"]}
ORDEN_VIAS = ["path", "footway", "steps", "cycleway", "track", "service", "pedestrian", "living_street",
              "residential", "unclassified", "tertiary", "secondary", "primary", "trunk", "motorway"]

# Lugares que se ven desde lejos; el resto aparece al acercarse (zoom 15 y 16).
LUGARES_PRINCIPALES = ["hospital", "university", "college", "marketplace", "mall", "department_store", "park",
                       "stadium", "museum", "attraction", "place_of_worship", "bus_station", "station", "townhall",
                       "courthouse", "monument", "monastery", "church", "cathedral"]
LUGARES_MEDIANOS = ["school", "clinic", "pharmacy", "bank", "hotel", "fuel", "sports_centre", "library", "cinema",
                    "theatre", "fire_station", "police", "post_office", "community_centre", "supermarket",
                    "convenience", "garden", "gallery", "viewpoint", "hostel"]


def px(v):
    return round(v / DENSIDAD, 2)


def ancho_calle():
    """Ancho por clase y zoom: medido a zoom 14 y 16, extrapolado hacia afuera (base 2, como el mapa)."""
    a14, a16 = E["anchos_px"]["14"], E["anchos_px"]["16"]

    def por_clase(tabla, factor=1.0, minimo=0.0):
        casos = []
        for clase in ORDEN_VIAS:
            casos += [clase, max(minimo, px(tabla.get(clase, tabla["otro"]) * factor))]
        return ["match", ["get", "clase"], *casos, max(minimo, px(tabla["otro"] * factor))]

    return ["interpolate", ["exponential", 2], ["zoom"],
            8, por_clase(a14, 1 / 16, 0.4),
            12, por_clase(a14, 1 / 4, 0.5),
            14, por_clase(a14, 1, 0.3),
            16, por_clase(a16),
            18, por_clase(a16, 4)]


def color_calle():
    return ["match", ["get", "clase"],
            ["footway", "path", "steps", "cycleway"], E["parque_linea"],
            ["motorway", "trunk"], E["via_rapida"],
            E["calle"]]


def nombre_abreviado():
    """'Avenida Ejército' → 'Av. Ejército' (solo al inicio del nombre), como en el mapa aprobado."""
    n = ["get", "name"]
    expr = n
    for largo, corto in reversed([("Avenida ", "Av. "), ("Calle ", "C. "), ("Jirón ", "Jr. "),
                                  ("Pasaje ", "Pje. "), ("Prolongación ", "Prol. ")]):
        expr = ["case",
                ["==", ["slice", n, 0, len(largo)], largo], ["concat", corto, ["slice", n, len(largo)]],
                expr]
    return expr


def por_grupo(campo, defecto):
    casos = []
    for gid, g in GRUPOS.items():
        casos += [gid, g[campo]]
    return ["match", ["get", "grupo"], *casos, defecto]


def tam_texto(base14, base16):
    return ["interpolate", ["linear"], ["zoom"], 14, px(base14), 16, px(base16), 18, px(base16) * 1.15]


def capa_lugares(id_capa, minzoom, filtro, tam14, tam16, orden):
    return {
        "id": id_capa, "type": "symbol", "source": "arequipa", "source-layer": "lugares_nombres",
        "minzoom": minzoom, "filter": filtro,
        "layout": {
            "icon-image": ["concat", "punto-", ["get", "grupo"]],
            "icon-size": 1,
            "text-field": ["get", "name"],
            "text-font": FUENTE,
            "text-size": tam_texto(tam14, tam16),
            "text-anchor": "left",
            "text-offset": [0.7, 0],
            "text-max-width": 9,
            "text-justify": "left",
            "symbol-sort-key": orden,
            "text-padding": 4,
        },
        "paint": {
            "text-color": por_grupo("texto", "#5F6368"),
            "text-halo-color": E["halo_texto"],
            "text-halo-width": 1.3,
        },
    }


# Los anchos y tamaños se midieron en Google a sus zooms 14 y 16 (teselas de 256 px). MapLibre usa teselas de
# 512 px: el mismo nivel de detalle está un zoom más abajo (zoom 16 de Google = zoom 15 de MapLibre).
# Por eso los zooms de este archivo están escritos "en zoom de Google" y se convierten al final.
ZOOM_GOOGLE_A_MAPLIBRE = -1


def desplazar_zoom(nodo, d):
    """Resta 1 a las paradas de ["interpolate"/"step", ..., ["zoom"], ...] y a los "minzoom" de las capas."""
    if isinstance(nodo, list):
        if len(nodo) > 3 and nodo[0] in ("interpolate", "step") and nodo[2 if nodo[0] == "interpolate" else 1] == ["zoom"]:
            ini = 3 if nodo[0] == "interpolate" else 3
            for i in range(ini, len(nodo), 2):
                if isinstance(nodo[i], (int, float)):
                    nodo[i] = nodo[i] + d
        for x in nodo:
            desplazar_zoom(x, d)
    elif isinstance(nodo, dict):
        if "minzoom" in nodo and isinstance(nodo["minzoom"], (int, float)):
            nodo["minzoom"] = max(0, nodo["minzoom"] + d)
        for v in nodo.values():
            desplazar_zoom(v, d)


def estilo(fuente, ruta_mapa="arequipa.pmtiles"):
    s = {
        "version": 8,
        "name": "SITP Arequipa (datos OpenStreetMap)",
        "metadata": {"generado_por": "scripts/mapa/generar_estilo.py", "fuente_letras": fuente},
        "sources": {
            "arequipa": {
                "type": "vector",
                "url": f"pmtiles://asset://{ruta_mapa}",
                "attribution": ATRIBUCION,
            }
        },
        "glyphs": "asset://mapa/glifos/{fontstack}/{range}.pbf",
        "layers": [],
    }
    if fuente == "sistema":
        # Roboto del propio Android: no suma peso al APK. Lo que no cubra cae a "glyphs".
        s["font-faces"] = {FUENTE[0]: [{"url": "file:///system/fonts/Roboto-Regular.ttf",
                                        "unicode-range": ["U+0000-FFFF"]}]}
    L = s["layers"]
    L.append({"id": "fondo", "type": "background", "paint": {"background-color": E["fondo"]}})

    # Áreas de lugares (un color por grupo); parques encima, como en el mapa aprobado
    L.append({"id": "lugares_areas", "type": "fill", "source": "arequipa", "source-layer": "lugares_areas",
              "filter": ["!=", ["get", "grupo"], "parques"],
              "paint": {"fill-color": por_grupo("relleno", E["fondo"]), "fill-antialias": True}})
    L.append({"id": "parques", "type": "fill", "source": "arequipa", "source-layer": "lugares_areas",
              "filter": ["==", ["get", "grupo"], "parques"],
              "paint": {"fill-color": E["parque_relleno"]}})

    # Agua y ríos (el río Chili se ve en todos los zooms)
    L.append({"id": "agua", "type": "fill", "source": "arequipa", "source-layer": "agua",
              "paint": {"fill-color": E["agua"]}})
    r14, r16 = E["rio_ancho_px"], E["quebrada_ancho_px"]
    L.append({"id": "rios", "type": "line", "source": "arequipa", "source-layer": "rios",
              "layout": {"line-cap": "round", "line-join": "round"},
              "paint": {"line-color": E["agua"],
                        "line-width": ["interpolate", ["exponential", 2], ["zoom"],
                                       8, ["match", ["get", "clase"], "river", 1.2, 0.3],
                                       14, ["match", ["get", "clase"], "river", px(r14["14"]), px(r16["14"])],
                                       16, ["match", ["get", "clase"], "river", px(r14["16"]), px(r16["16"])],
                                       18, ["match", ["get", "clase"], "river", px(r14["16"]) * 4, px(r16["16"]) * 4]]}})

    # Calles: una sola capa, ordenada para que las principales queden encima
    L.append({"id": "calles", "type": "line", "source": "arequipa", "source-layer": "calles",
              "layout": {"line-cap": "round", "line-join": "round",
                         "line-sort-key": ["index-of", ["get", "clase"], ["literal", ORDEN_VIAS]]},
              "paint": {"line-color": color_calle(), "line-width": ancho_calle()}})

    # Nombres. MapLibre ubica primero las capas de ARRIBA (las últimas de la lista):
    # calles y río > zonas > lugares principales > medianos > menores (estos llenan el espacio libre).
    principales = ["match", ["get", "clase"], LUGARES_PRINCIPALES, True, False]
    medianos = ["match", ["get", "clase"], LUGARES_MEDIANOS, True, False]
    L.append(capa_lugares("lugares_menores", 16, ["all", ["!", principales], ["!", medianos]], 27, 30, 3))
    L.append(capa_lugares("lugares_medianos", 15, medianos, 27, 33, 2))
    L.append(capa_lugares("lugares_principales", 13, principales, 27, 33, 1))

    L.append({"id": "zonas", "type": "symbol", "source": "arequipa", "source-layer": "zonas",
              "layout": {"text-field": ["upcase", ["get", "name"]], "text-font": FUENTE,
                         "text-size": ["match", ["get", "clase"], "city", 16, ["town", "village"], 13, 11],
                         "text-letter-spacing": 0.08, "text-max-width": 8,
                         "symbol-sort-key": ["match", ["get", "clase"], "city", 0, ["town", "suburb"], 1, 2]},
              "paint": {"text-color": E["texto_zona"], "text-halo-color": E["halo_texto"], "text-halo-width": 1.5}})
    L.append({"id": "rios_nombres", "type": "symbol", "source": "arequipa", "source-layer": "rios",
              "minzoom": 12, "filter": ["==", ["get", "clase"], "river"],
              "layout": {"symbol-placement": "line", "text-field": ["get", "name"], "text-font": FUENTE,
                         "text-size": tam_texto(E["tamano_texto_px"]["14"], E["tamano_texto_px"]["16"]),
                         "symbol-spacing": 400},
              "paint": {"text-color": E["texto_agua"], "text-halo-color": E["agua"], "text-halo-width": 1}})
    principales_via = ["match", ["get", "clase"], ["tertiary", "secondary", "primary", "trunk", "motorway"], True, False]
    for id_capa, minzoom, filtro in (("calles_nombres", 15, ["!", principales_via]),
                                     ("calles_nombres_principales", 13, principales_via)):
        L.append({"id": id_capa, "type": "symbol", "source": "arequipa", "source-layer": "calles",
                  "minzoom": minzoom, "filter": filtro,
                  "layout": {"symbol-placement": "line", "text-field": nombre_abreviado(), "text-font": FUENTE,
                             "text-size": tam_texto(E["tamano_texto_px"]["14"], E["tamano_texto_px"]["16"]),
                             "symbol-spacing": 300, "text-max-angle": 30, "text-padding": 2,
                             "symbol-sort-key": ["-", 20, ["index-of", ["get", "clase"], ["literal", ORDEN_VIAS]]]},
                  "paint": {"text-color": E["texto_calle"], "text-halo-color": E["halo_texto"], "text-halo-width": 1.5}})

    desplazar_zoom(L, ZOOM_GOOGLE_A_MAPLIBRE)
    return s


def main():
    salida = sys.argv[1]
    fuente = sys.argv[sys.argv.index("--fuente") + 1] if "--fuente" in sys.argv else "sistema"
    ruta_mapa = sys.argv[sys.argv.index("--mapa") + 1] if "--mapa" in sys.argv else "arequipa.pmtiles"
    os.makedirs(os.path.dirname(os.path.abspath(salida)), exist_ok=True)
    with open(salida, "w", encoding="utf-8") as f:
        json.dump(estilo(fuente, ruta_mapa), f, ensure_ascii=False, indent=1)
    # Colores de los círculos de cada grupo, para que la app dibuje los íconos "punto-<grupo>"
    with open(os.path.join(os.path.dirname(os.path.abspath(salida)), "grupos_lugares.json"), "w", encoding="utf-8") as f:
        json.dump({gid: g["texto"] for gid, g in GRUPOS.items()}, f, ensure_ascii=False, indent=1)
    print(f"Estilo escrito en {salida} (letras: {fuente})")


if __name__ == "__main__":
    main()
