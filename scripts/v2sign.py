#!/usr/bin/env python3
"""Minimal APK Signature Scheme v2 signer (RSA-PKCS1v15-SHA256, algo 0x0103)."""
import hashlib, struct, sys
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.serialization import pkcs12

def lp(b): return struct.pack("<I", len(b)) + b

def find_eocd(d):
    i = d.rfind(b"PK\x05\x06")
    if i < 0: raise SystemExit("no EOCD")
    return i

def digest_sections(secs):
    chunks = []
    for s in secs:
        for o in range(0, len(s), 1 << 20):
            c = s[o:o + (1 << 20)]
            chunks.append(hashlib.sha256(b"\xa5" + struct.pack("<I", len(c)) + c).digest())
    return hashlib.sha256(b"\x5a" + struct.pack("<I", len(chunks)) + b"".join(chunks)).digest()

def main(inp, out, p12, pw):
    key, cert, _ = pkcs12.load_key_and_certificates(open(p12, "rb").read(), pw.encode())
    d = open(inp, "rb").read()
    e = find_eocd(d)
    cd_off = struct.unpack("<I", d[e + 16:e + 20])[0]
    A, C, D = d[:cd_off], d[cd_off:e], d[e:]
    dig = digest_sections([A, C, D])
    ALG = 0x0103
    cert_der = cert.public_bytes(serialization.Encoding.DER)
    signed = lp(lp(struct.pack("<I", ALG) + lp(dig))) + lp(lp(cert_der)) + lp(b"")
    sig = key.sign(signed, padding.PKCS1v15(), hashes.SHA256())
    pub = key.public_key().public_bytes(serialization.Encoding.DER,
                                        serialization.PublicFormat.SubjectPublicKeyInfo)
    signer = lp(signed) + lp(lp(struct.pack("<I", ALG) + lp(sig))) + lp(pub)
    value = lp(lp(signer))
    pair = struct.pack("<Q", 4 + len(value)) + struct.pack("<I", 0x7109871a) + value
    size = len(pair) + 8 + 16
    block = struct.pack("<Q", size) + pair + struct.pack("<Q", size) + b"APK Sig Block 42"
    D2 = D[:16] + struct.pack("<I", cd_off + len(block)) + D[20:]
    open(out, "wb").write(A + block + C + D2)

if __name__ == "__main__":
    main(*sys.argv[1:5])
