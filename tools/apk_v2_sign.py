#!/usr/bin/env python3
"""Adiciona assinatura APK Signature Scheme v2 a um APK já assinado em v1."""

import hashlib
import struct
import sys
from pathlib import Path

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.serialization import pkcs12


APK_SIG_BLOCK_MAGIC = b"APK Sig Block 42"
V2_BLOCK_ID = 0x7109871A
RSA_PKCS1_SHA256_ID = 0x0103
CHUNK_SIZE = 1024 * 1024


def u32(value: int) -> bytes:
    return struct.pack("<I", value)


def u64(value: int) -> bytes:
    return struct.pack("<Q", value)


def length_prefixed(value: bytes) -> bytes:
    return u32(len(value)) + value


def find_eocd(apk: bytes) -> int:
    start = max(0, len(apk) - 65557)
    offset = apk.rfind(b"PK\x05\x06", start)
    if offset < 0:
        raise ValueError("EOCD do ZIP não encontrado")
    comment_length = struct.unpack_from("<H", apk, offset + 20)[0]
    if offset + 22 + comment_length != len(apk):
        raise ValueError("EOCD inválido ou ZIP concatenado")
    return offset


def content_digest(sections: list[bytes]) -> bytes:
    chunk_hashes = []
    for section in sections:
        for start in range(0, len(section), CHUNK_SIZE):
            chunk = section[start:start + CHUNK_SIZE]
            chunk_hashes.append(hashlib.sha256(b"\xA5" + u32(len(chunk)) + chunk).digest())
    return hashlib.sha256(b"\x5A" + u32(len(chunk_hashes)) + b"".join(chunk_hashes)).digest()


def build_signing_block(apk: bytes, keystore: bytes, password: bytes) -> tuple[bytes, int, int]:
    eocd_offset = find_eocd(apk)
    central_directory_offset = struct.unpack_from("<I", apk, eocd_offset + 16)[0]
    if central_directory_offset >= eocd_offset:
        raise ValueError("Diretório central inválido")
    if APK_SIG_BLOCK_MAGIC in apk[max(0, central_directory_offset - 65536):central_directory_offset]:
        raise ValueError("O APK já contém bloco de assinatura")

    # Para o digest v2, o EOCD aponta para o início do bloco de assinatura.
    digest_eocd = bytearray(apk[eocd_offset:])
    struct.pack_into("<I", digest_eocd, 16, central_directory_offset)
    digest = content_digest([
        apk[:central_directory_offset],
        apk[central_directory_offset:eocd_offset],
        bytes(digest_eocd),
    ])

    private_key, certificate, _ = pkcs12.load_key_and_certificates(keystore, password)
    if private_key is None or certificate is None:
        raise ValueError("Chave privada ou certificado ausente")
    certificate_der = certificate.public_bytes(serialization.Encoding.DER)
    public_key_der = certificate.public_key().public_bytes(
        serialization.Encoding.DER,
        serialization.PublicFormat.SubjectPublicKeyInfo,
    )

    digest_record = u32(RSA_PKCS1_SHA256_ID) + length_prefixed(digest)
    digests = length_prefixed(digest_record)
    certificates = length_prefixed(certificate_der)
    additional_attributes = b""
    signed_data = (
        length_prefixed(digests)
        + length_prefixed(certificates)
        + length_prefixed(additional_attributes)
    )
    signature = private_key.sign(signed_data, padding.PKCS1v15(), hashes.SHA256())
    signature_record = u32(RSA_PKCS1_SHA256_ID) + length_prefixed(signature)
    signatures = length_prefixed(signature_record)
    signer = (
        length_prefixed(signed_data)
        + length_prefixed(signatures)
        + length_prefixed(public_key_der)
    )
    v2_value = length_prefixed(signer)
    pair = u64(4 + len(v2_value)) + u32(V2_BLOCK_ID) + v2_value
    block_size = len(pair) + 24
    signing_block = u64(block_size) + pair + u64(block_size) + APK_SIG_BLOCK_MAGIC
    return signing_block, central_directory_offset, eocd_offset


def main() -> None:
    if len(sys.argv) != 5:
        raise SystemExit("uso: apk_v2_sign.py entrada-v1.apk chave.p12 senha saida.apk")
    source, keystore_path, password, output = sys.argv[1:]
    apk = Path(source).read_bytes()
    signing_block, central_directory_offset, eocd_offset = build_signing_block(
        apk, Path(keystore_path).read_bytes(), password.encode()
    )
    tail = bytearray(apk[central_directory_offset:])
    new_eocd_offset = eocd_offset - central_directory_offset
    struct.pack_into("<I", tail, new_eocd_offset + 16,
                     central_directory_offset + len(signing_block))
    result = apk[:central_directory_offset] + signing_block + bytes(tail)
    Path(output).write_bytes(result)


if __name__ == "__main__":
    main()
