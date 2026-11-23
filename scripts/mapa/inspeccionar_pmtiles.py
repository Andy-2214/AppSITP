"""Cuenta elementos por capa (y por grupo/clase de lugares) en un archivo .pmtiles (PMTiles v3, teselas MVT).

Uso: python -I -X utf8 inspeccionar_pmtiles.py <archivo.pmtiles> [zoom] [--nombres]
Solo usa la biblioteca estándar de Python (sin dependencias).
"""
import gzip
import struct
import sys
from collections import Counter


def varint(b, i):
    r = s = 0
    while True:
        x = b[i]; i += 1
        r |= (x & 0x7F) << s; s += 7
        if x < 0x80:
            return r, i


def campos(b):
    """Recorre un mensaje protobuf: devuelve (número de campo, tipo, valor)."""
    i = 0
    while i < len(b):
        k, i = varint(b, i)
        f, t = k >> 3, k & 7
        if t == 0:
            v, i = varint(b, i)
        elif t == 2:
            n, i = varint(b, i); v = b[i:i + n]; i += n
        elif t == 1:
            v = b[i:i + 8]; i += 8
        elif t == 5:
            v = b[i:i + 4]; i += 4
        else:
            raise ValueError(t)
        yield f, t, v


def descomprimir(b, tipo):
    return gzip.decompress(b) if tipo == 2 else b


def leer_directorio(b):
    n, i = varint(b, 0)
    ids, largos, longs, offs = [], [], [], []
    last = 0
    for _ in range(n):
        d, i = varint(b, i); last += d; ids.append(last)
    for _ in range(n):
        v, i = varint(b, i); largos.append(v)
    for _ in range(n):
        v, i = varint(b, i); longs.append(v)
    for k in range(n):
        v, i = varint(b, i)
        offs.append(offs[-1] + longs[k - 1] if v == 0 and k > 0 else v - 1)
    return list(zip(ids, largos, offs, longs))


def zoom_de_id(tid):
    z, acc = 0, 0
    while acc + 4 ** z <= tid:
        acc += 4 ** z; z += 1
    return z


def teselas(f):
    h = f.read(127)
    assert h[:7] == b"PMTiles", "no es PMTiles"
    (root_off, root_len, meta_off, meta_len, leaf_off, leaf_len, data_off, data_len) = struct.unpack("<8Q", h[8:72])
    comp_int, comp_tile = h[97], h[98]

    def directorio(off, ln):
        f.seek(off); return leer_directorio(descomprimir(f.read(ln), comp_int))

    pendientes = [directorio(root_off, root_len)]
    while pendientes:
        for tid, run, off, ln in pendientes.pop():
            if run == 0:
                pendientes.append(directorio(leaf_off + off, ln))
            else:
                f.seek(data_off + off)
                yield tid, run, descomprimir(f.read(ln), comp_tile)


def valor(b):
    for f, t, v in campos(b):
        if f == 1:
            return v.decode("utf-8")
        return v


def main():
    ruta = sys.argv[1]
    zoom = int(sys.argv[2]) if len(sys.argv) > 2 and sys.argv[2].isdigit() else 14
    por_capa = Counter(); por_grupo = Counter(); por_clase = Counter(); nombres = set()
    with open(ruta, "rb") as f:
        for tid, run, datos in teselas(f):
            if zoom_de_id(tid) != zoom:
                continue
            for fc, _, capa in campos(datos):
                if fc != 3:
                    continue
                nombre_capa = None; claves = []; valores = []; elementos = []
                for c, _, v in campos(capa):
                    if c == 1: nombre_capa = v.decode()
                    elif c == 3: claves.append(v.decode())
                    elif c == 4: valores.append(valor(v))
                    elif c == 2: elementos.append(v)
                por_capa[nombre_capa] += len(elementos) * run
                if nombre_capa == "lugares_nombres":
                    for e in elementos:
                        tags = {}
                        for c, t, v in campos(e):
                            if c == 2:
                                j = 0; idx = []
                                while j < len(v):
                                    x, j = varint(v, j); idx.append(x)
                                tags = {claves[idx[k]]: valores[idx[k + 1]] for k in range(0, len(idx), 2)}
                        por_grupo[tags.get("grupo")] += 1
                        por_clase[(tags.get("grupo"), tags.get("clase"))] += 1
                        nombres.add(tags.get("name"))
    print(f"z{zoom} elementos por capa:", dict(por_capa))
    if por_grupo:
        print("lugares_nombres por grupo:", dict(por_grupo))
        print("clases más frecuentes:", por_clase.most_common(25))
        if "--nombres" in sys.argv:
            print(sorted(n for n in nombres if n and any(k in n.lower() for k in ("oxxo", "tambo", "mass", "inkafarma", "mifarma")))[:40])


if __name__ == "__main__":
    main()
