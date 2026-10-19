#!/usr/bin/env python3
"""Ky APK phat hanh bang khoa rieng (ngoai CI): v1 (jarsigner) + zipalign + v2 (APK Signature Scheme v2).

  python3 ky_apk.py <apk chua ky> <apk ra> <khoa.p12> <alias> <tep mat khau>

- v1 (JAR) cho Android 5-6, v2 cho Android 7+ (bat buoc khi targetSdk >= 30).
- zipalign 4 byte cho tep khong nen (resources.arsc, jar game) - bang truong "extra" 0xD935 nhu zipalign cua Android.
"""
import hashlib
import os
import struct
import subprocess
import sys
import tempfile
import zipfile

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.serialization import pkcs12

APK_SIG_V2 = 0x7109871A
ALGO_RSA_PKCS1_SHA256 = 0x0103
CHUNK = 1 << 20


# ------------------------------------------------------------------ zip tho
def find_eocd(d):
    i = d.rfind(b'PK\x05\x06', max(0, len(d) - 65557))
    if i < 0:
        raise SystemExit('khong thay EOCD')
    cd_size, cd_off = struct.unpack_from('<II', d, i + 12)
    return i, cd_off, cd_size


def zipalign(src, dst, align=4):
    """chep zip, chen truong extra 0xD935 de du lieu tep KHONG nen bat dau o boi so `align`"""
    d = open(src, 'rb').read()
    eocd, cd_off, cd_size = find_eocd(d)
    # doc thu muc trung tam
    ents = []
    p = cd_off
    while p < cd_off + cd_size:
        if d[p:p + 4] != b'PK\x01\x02':
            raise SystemExit('CD hong')
        (method,) = struct.unpack_from('<H', d, p + 10)
        nl, xl, cl = struct.unpack_from('<HHH', d, p + 28)
        (loff,) = struct.unpack_from('<I', d, p + 42)
        rec = bytearray(d[p:p + 46 + nl + xl + cl])
        ents.append({'rec': rec, 'loff': loff, 'method': method})
        p += 46 + nl + xl + cl
    order = sorted(range(len(ents)), key=lambda k: ents[k]['loff'])
    bounds = [ents[k]['loff'] for k in order] + [cd_off]
    out = bytearray()
    for n, k in enumerate(order):
        e = ents[k]
        lo = e['loff']
        if d[lo:lo + 4] != b'PK\x03\x04':
            raise SystemExit('local header hong')
        lnl, lxl = struct.unpack_from('<HH', d, lo + 26)
        name = d[lo + 30:lo + 30 + lnl]
        extra = bytes(d[lo + 30 + lnl:lo + 30 + lnl + lxl])
        rest = d[lo + 30 + lnl + lxl:bounds[n + 1]]          # du lieu (+ data descriptor neu co)
        # bo truong can le cu (neu da zipalign truoc do)
        clean = bytearray()
        q = 0
        while q + 4 <= len(extra):
            hid, hl = struct.unpack_from('<HH', extra, q)
            if q + 4 + hl > len(extra):
                break
            if hid != 0xD935:
                clean += extra[q:q + 4 + hl]
            q += 4 + hl
        extra = bytes(clean)
        new_off = len(out)
        if e['method'] == 0:
            data_at = new_off + 30 + lnl + len(extra)
            if data_at % align:
                pad = (align - (data_at + 6) % align) % align
                extra += struct.pack('<HHH', 0xD935, 2 + pad, align) + b'\0' * pad
        hdr = bytearray(d[lo:lo + 30])
        struct.pack_into('<H', hdr, 28, len(extra))
        out += hdr + name + extra + rest
        struct.pack_into('<I', e['rec'], 42, new_off)
    new_cd = len(out)
    for e in ents:
        out += e['rec']
    end = bytearray(d[eocd:])
    struct.pack_into('<II', end, 12, len(out) - new_cd, new_cd)
    out += end
    open(dst, 'wb').write(out)


# ------------------------------------------------------------------ v2
def lp(b):
    return struct.pack('<I', len(b)) + b


def chunked_digest(sections):
    digests = []
    for s in sections:
        for i in range(0, len(s), CHUNK):
            c = s[i:i + CHUNK]
            digests.append(hashlib.sha256(b'\xa5' + struct.pack('<I', len(c)) + c).digest())
    return hashlib.sha256(b'\x5a' + struct.pack('<I', len(digests)) + b''.join(digests)).digest()


def sign_v2(src, dst, key, cert):
    d = open(src, 'rb').read()
    eocd, cd_off, cd_size = find_eocd(d)
    if d[cd_off - 16:cd_off] == b'APK Sig Block 42':
        raise SystemExit('apk da co khoi chu ky v2')
    digest = chunked_digest([d[:cd_off], d[cd_off:cd_off + cd_size], d[eocd:]])
    cert_der = cert.public_bytes(serialization.Encoding.DER)
    pub_der = key.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
    digests = lp(lp(struct.pack('<I', ALGO_RSA_PKCS1_SHA256) + lp(digest)))
    certs = lp(lp(cert_der))
    attrs = lp(b'')
    signed_data = digests + certs + attrs
    sig = key.sign(signed_data, padding.PKCS1v15(), hashes.SHA256())
    signer = lp(signed_data) + lp(lp(struct.pack('<I', ALGO_RSA_PKCS1_SHA256) + lp(sig))) + lp(pub_der)
    v2 = lp(lp(signer))
    pair = struct.pack('<Q', 4 + len(v2)) + struct.pack('<I', APK_SIG_V2) + v2
    size = len(pair) + 8 + 16
    block = struct.pack('<Q', size) + pair + struct.pack('<Q', size) + b'APK Sig Block 42'
    end = bytearray(d[eocd:])
    struct.pack_into('<I', end, 16, cd_off + len(block))
    open(dst, 'wb').write(d[:cd_off] + block + d[cd_off:cd_off + cd_size] + bytes(end))


def main():
    src, dst, ks, alias, pwfile = sys.argv[1:6]
    pw = open(pwfile).read().strip()
    key, cert, _ = pkcs12.load_key_and_certificates(open(ks, 'rb').read(), pw.encode())
    with tempfile.TemporaryDirectory() as t:
        # v1: bo chu ky cu (neu co) roi jarsigner
        clean = os.path.join(t, 'clean.apk')
        with zipfile.ZipFile(src) as zi, zipfile.ZipFile(clean, 'w') as zo:
            for info in zi.infolist():
                if info.filename.startswith('META-INF/') and info.filename.upper().endswith(('.SF', '.RSA', '.DSA', '.EC', 'MANIFEST.MF')):
                    continue
                zo.writestr(info, zi.read(info.filename), compress_type=info.compress_type)
        v1 = os.path.join(t, 'v1.apk')
        subprocess.run(['jarsigner', '-J-Duser.language=en', '-keystore', ks, '-storetype', 'PKCS12', '-storepass', pw,
                        '-digestalg', 'SHA-256', '-sigalg', 'SHA256withRSA', '-signedjar', v1, clean, alias],
                       check=True, stdout=subprocess.DEVNULL)
        al = os.path.join(t, 'al.apk')
        zipalign(v1, al, 4)
        sign_v2(al, dst, key, cert)
    print('da ky', dst, os.path.getsize(dst))


if __name__ == '__main__':
    main()
