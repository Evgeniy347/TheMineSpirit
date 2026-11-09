#!/usr/bin/env python3
"""Срез мира из файлов регионов: слои XZ вокруг точки, без зависимостей и без правок мира.

Запускать на сервере после `save-all flush`, иначе файлы отстают от игры:
    world-slice.py <x> <y> <z> [радиус=4] [вверх=3] [вниз=2] [--world путь]
Каждый слой — карта блоков; X растёт вправо, Z вниз, «@» — точка запроса, легенда под слоем.
"""
import argparse
import gzip
import io
import os
import struct
import zlib

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTES, STRING, LIST, COMPOUND, INTS, LONGS = range(13)


def read_payload(s, tag):
    if tag == BYTE:
        return s.read(1)[0]
    if tag == SHORT:
        return struct.unpack(">h", s.read(2))[0]
    if tag == INT:
        return struct.unpack(">i", s.read(4))[0]
    if tag == LONG:
        return struct.unpack(">q", s.read(8))[0]
    if tag == FLOAT:
        return struct.unpack(">f", s.read(4))[0]
    if tag == DOUBLE:
        return struct.unpack(">d", s.read(8))[0]
    if tag == BYTES:
        return s.read(struct.unpack(">i", s.read(4))[0])
    if tag == STRING:
        return s.read(struct.unpack(">H", s.read(2))[0]).decode("utf-8", "replace")
    if tag == LIST:
        inner = s.read(1)[0]
        return [read_payload(s, inner) for _ in range(struct.unpack(">i", s.read(4))[0])]
    if tag == COMPOUND:
        out = {}
        while True:
            kind = s.read(1)[0]
            if kind == END:
                return out
            name = read_payload(s, STRING)
            out[name] = read_payload(s, kind)
    if tag == INTS:
        n = struct.unpack(">i", s.read(4))[0]
        return struct.unpack(">%di" % n, s.read(4 * n))
    if tag == LONGS:
        n = struct.unpack(">i", s.read(4))[0]
        return struct.unpack(">%dq" % n, s.read(8 * n))
    raise ValueError("неизвестный тег NBT %d" % tag)


def read_chunk(world, cx, cz):
    path = os.path.join(world, "region", "r.%d.%d.mca" % (cx >> 5, cz >> 5))
    if not os.path.exists(path):
        return None
    with open(path, "rb") as f:
        f.seek(4 * ((cx & 31) + (cz & 31) * 32))
        entry = struct.unpack(">I", f.read(4))[0]
        if entry == 0:
            return None
        f.seek((entry >> 8) * 4096)
        length, kind = struct.unpack(">IB", f.read(5))
        data = f.read(length - 1)
    raw = gzip.decompress(data) if kind == 1 else zlib.decompress(data) if kind == 2 else data
    s = io.BytesIO(raw)
    s.read(1)
    read_payload(s, STRING)
    return read_payload(s, COMPOUND)


def block_at(chunk, x, y, z):
    if chunk is None:
        return "?"
    for section in chunk.get("sections", []):
        if section.get("Y") != y >> 4:
            continue
        states = section.get("block_states")
        if not states:
            return "minecraft:air"
        palette = states["palette"]
        if len(palette) == 1:
            return palette[0]["Name"]
        bits = max(4, len(bin(len(palette) - 1)) - 2)
        per_long = 64 // bits
        index = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15)
        word = states["data"][index // per_long] & 0xFFFFFFFFFFFFFFFF
        return palette[(word >> ((index % per_long) * bits)) & ((1 << bits) - 1)]["Name"]
    return "minecraft:air"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("x", type=int)
    parser.add_argument("y", type=int)
    parser.add_argument("z", type=int)
    parser.add_argument("radius", type=int, nargs="?", default=4)
    parser.add_argument("up", type=int, nargs="?", default=3)
    parser.add_argument("down", type=int, nargs="?", default=2)
    parser.add_argument("--world", default="/opt/minecraft/server/world")
    args = parser.parse_args()
    chunks = {}
    names = {"minecraft:air": ".", "minecraft:cave_air": "."}
    symbols = iter("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789#$%&*+=~")
    for y in range(args.y + args.up, args.y - args.down - 1, -1):
        print("y=%d  x от %d, z от %d" % (y, args.x - args.radius, args.z - args.radius))
        used = {}
        for z in range(args.z - args.radius, args.z + args.radius + 1):
            row = []
            for x in range(args.x - args.radius, args.x + args.radius + 1):
                key = (x >> 4, z >> 4)
                if key not in chunks:
                    chunks[key] = read_chunk(args.world, *key)
                name = block_at(chunks[key], x, y, z)
                if name not in names:
                    names[name] = next(symbols, "?")
                used[names[name]] = name
                mark = names[name]
                row.append("@" if (x, y, z) == (args.x, args.y, args.z) and mark == "." else mark)
            print("  " + "".join(row))
        print("  " + " ".join("%s=%s" % (k, v.replace("minecraft:", "")) for k, v in sorted(used.items()) if k != "."))


if __name__ == "__main__":
    main()
