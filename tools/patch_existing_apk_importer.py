#!/usr/bin/env python3
"""Corrige os dois aliases de cabeçalho na APK 0.4 sem deslocar o DEX."""

import hashlib
import struct
import sys
import zlib
import zipfile
from pathlib import Path


def patch_dex(data: bytes) -> bytes:
    patched = bytearray(data)
    replacements = {
        b"\x07cliente\x00": b"\x07sequenc\x00",
        b"\x0alogradouro\x00": b"\x0adestinatio\x00",
    }
    for old, new in replacements.items():
        count = patched.count(old)
        if count != 1:
            raise RuntimeError(f"Esperava exatamente uma ocorrência de {old!r}; encontrei {count}")
        patched[patched.index(old):patched.index(old) + len(old)] = new

    patched[12:32] = hashlib.sha1(patched[32:]).digest()
    struct.pack_into("<I", patched, 8, zlib.adler32(patched[12:]) & 0xFFFFFFFF)
    return bytes(patched)


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("uso: patch_existing_apk_importer.py entrada.apk saida-sem-assinar.apk")
    source = Path(sys.argv[1])
    output = Path(sys.argv[2])
    with zipfile.ZipFile(source, "r") as incoming, zipfile.ZipFile(
        output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9
    ) as outgoing:
        for info in incoming.infolist():
            if info.filename.startswith("META-INF/"):
                continue
            payload = incoming.read(info.filename)
            if info.filename == "classes.dex":
                payload = patch_dex(payload)
            outgoing.writestr(info.filename, payload)


if __name__ == "__main__":
    main()
