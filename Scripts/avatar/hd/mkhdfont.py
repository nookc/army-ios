#!/usr/bin/env python3
"""Sinh bo font HD (x4) cho ban "Avatar 250 HD" tu font TTF (Baloo 2, giay phep SIL OFL).

Font cua game (lop dh): moi font = 1 dai anh doc (moi chu 1 o cao d, rong ca anh), ve bang drawRegion(anh, 0, idx*d, W, d)
va tinh be rong chu theo bang rong trong normal/font/<ten>. Ban HD thay DUNG dai anh do bang dai x4 ve tu TTF:
giu nguyen o chu + be rong (bo cuc game khong doi), chi net chu la moi. May HD (runtime) tim anh thay theo CRC32 cua anh goc
(hdx/<crc>.png, chunk apHd = 4).

  python3 mkhdfont.py <jar client> <thu muc ra hdx> [--xem anh_xem.png]
"""
import io, os, struct, sys, zipfile, zlib
from PIL import Image, ImageDraw, ImageFont, ImageFilter, ImageChops

S = 4
KEY = b'NguyenVanMinh'
NAMES = ["normal", "border", "arial", "black", "number", "smallRed", "smallYellow", "big"]
HERE = os.path.dirname(os.path.abspath(__file__))
TTF = os.environ.get('HD_TTF', os.path.join(HERE, 'Baloo2.ttf'))


def dec(b):
    return bytes(x ^ KEY[i % len(KEY)] for i, x in enumerate(b))


def doc_goi(d):
    n = d[0]; p = 1; ents = []
    for _ in range(n):
        ln = d[p]; p += 1
        name = dec(d[p:p + ln]).decode('latin1'); p += ln
        sz = struct.unpack('>H', d[p:p + 2])[0]; p += 2
        ents.append((name, sz))
    blob = dec(d[p:p + sum(s for _, s in ents)])
    out = {}; off = 0
    for name, sz in ents:
        out[name] = blob[off:off + sz]; off += sz
    return out


def font(size, wght):
    f = ImageFont.truetype(TTF, size)
    try:
        f.set_variation_by_axes([wght])
    except Exception:
        pass
    return f


def png_hd(im, he_so):
    """PNG co chunk rieng apHd (1 byte he so) ngay sau IHDR - runtime doc de biet kich thuoc logic = diem anh / he so"""
    bo = io.BytesIO(); im.save(bo, 'PNG', optimize=True)
    b = bo.getvalue()
    sau = 8 + 4 + 4 + 13 + 4
    ch = b'apHd' + bytes([he_so])
    return b[:sau] + struct.pack('>I', 1) + ch + struct.pack('>I', zlib.crc32(ch) & 0xFFFFFFFF) + b[sau:]


# kieu tung font: co chu (diem anh x4), do dam, duong chan chu (diem logic tu dinh o), mau, vien (diem anh x4, mau), nen
KIEU = {
    'normal':      dict(size=46, wght=700, base=11.0, fill=(6, 81, 94)),
    'border':      dict(size=46, wght=700, base=12.0, fill=(255, 255, 255), vien=(3.4, (10, 22, 30)), dx=1.0),
    'arial':       dict(size=45, wght=540, base=11.0, fill=(255, 255, 255), dx=0.5),
    'black':       dict(size=45, wght=540, base=11.0, fill=(18, 22, 26), dx=0.5),
    'number':      dict(size=50, wght=800, base=11.3, fill=((255, 246, 60), (255, 190, 0)), vien=(4.0, (40, 26, 0)), rong=1.0, dx=1.0),
    'smallRed':    dict(size=27, wght=800, base=6.7, fill=(255, 255, 255), vien=(2.6, (8, 12, 16)), hoa=True, dx=0.5, deu=True),
    'smallYellow': dict(size=27, wght=800, base=6.7, fill=(255, 226, 60), vien=(2.6, (8, 12, 16)), hoa=True, dx=0.5, deu=True),
    'big':         dict(size=62, wght=800, base=16.0, fill=(255, 255, 255), vien=(4.0, (12, 30, 60)), dx=0.5),
}


def ve_chu(ch, k, adv, W, d):
    """1 o (W*S) x (d*S) RGBA"""
    cell = Image.new('RGBA', (W * S, d * S), (0, 0, 0, 0))
    if ch == ' ':
        return cell
    t = ch.upper() if k.get('hoa') else ch
    f = font(k['size'], k['wght'])
    g = f.getlength(t)
    A = adv * S
    vien = k.get('vien')
    pad = int(vien[0] + 3) if vien else 3
    # ve chu ra anh xam rong du (co le), neo duong chan chu
    asc, desc = f.getmetrics()
    tw = int(g + 2 * pad + 8); th = int(asc + desc + 2 * pad + 8)
    m = Image.new('L', (tw, th), 0)
    ImageDraw.Draw(m).text((pad + 2, pad + 2 + asc), t, font=f, fill=255, anchor='ls')
    by = pad + 2 + asc                      # hang duong chan chu trong m
    # ep ngang cho vua be rong o (chu rong hon o) hoac keo rong (font "number")
    sx = 1.0
    if k.get('rong'):
        sx = max(1.0, (A - 1.5 * S) / max(1.0, g)) * k['rong']
    elif k.get('deu'):
        # font nho cua game gan nhu deu o: keo / ep chu cho vua be rong o -> khoang cach chu deu
        sx = min(1.3, max(0.72, (A - 0.4 * S) / max(1.0, g)))
    elif g > A:
        sx = max(0.72, A / g)
    if abs(sx - 1.0) > 0.01:
        nw = max(1, int(round(tw * sx)))
        m = m.resize((nw, th), Image.LANCZOS)
        g *= sx; pad_x = (pad + 2) * sx
    else:
        pad_x = pad + 2
    lop = Image.new('RGBA', m.size, (0, 0, 0, 0))
    if vien:
        r, mau = vien
        # vien: gian mat na chu (bo loc cuc dai lap) roi lam min mep
        o = m
        for _ in range(int(r)):
            o = o.filter(ImageFilter.MaxFilter(3))
        fr = r - int(r)
        if fr > 0.05:
            o2 = o.filter(ImageFilter.MaxFilter(3))
            o = Image.blend(o, o2, fr)
        o = o.filter(ImageFilter.GaussianBlur(0.6))
        lop.paste(Image.new('RGBA', m.size, mau + (255,)), (0, 0), o)
    fill = k['fill']
    if isinstance(fill[0], tuple):
        # chuyen mau doc (tren -> duoi) trong chieu cao chu
        grad = Image.new('RGBA', m.size)
        top, bot = fill
        y0 = by - int(k['size'] * 0.72); y1 = by
        px = grad.load()
        for y in range(m.size[1]):
            tt = min(1.0, max(0.0, (y - y0) / max(1, y1 - y0)))
            c = tuple(int(top[i] + (bot[i] - top[i]) * tt) for i in range(3)) + (255,)
            for x in range(m.size[0]):
                px[x, y] = c
        lop.paste(grad, (0, 0), m)
    else:
        lop.paste(Image.new('RGBA', m.size, fill + (255,)), (0, 0), m)
    # vi tri: can giua khung tien cua chu trong be rong o; duong chan chu theo font
    x = (A - g) / 2.0 - pad_x + k.get('dx', 0) * S
    y = k['base'] * S - by
    cell.alpha_composite(lop, (int(round(x)), int(round(y))))
    return cell


def lam(jar, out, xem=None):
    z = zipfile.ZipFile(jar)
    goi = doc_goi(z.read('normal/font.av'))
    os.makedirs(out, exist_ok=True)
    ds = []
    mau = []
    for i, n in enumerate(NAMES):
        d = z.read('normal/font/' + n)
        ln = struct.unpack('>H', d[:2])[0]
        chars = d[2:2 + ln].decode('utf-8')
        ws = list(d[2 + ln:2 + ln + len(chars)])
        cao = d[2 + ln + len(chars)]
        goc = goi['%d.png' % i]
        W = Image.open(io.BytesIO(goc)).width
        k = KIEU[n]
        strip = Image.new('RGBA', (W * S, len(chars) * cao * S), (0, 0, 0, 0))
        for idx, ch in enumerate(chars):
            strip.alpha_composite(ve_chu(ch, k, ws[idx], W, cao), (0, idx * cao * S))
        crc = zlib.crc32(goc) & 0xFFFFFFFF
        open(os.path.join(out, '%08x.png' % crc), 'wb').write(png_hd(strip, S))
        ds.append(crc)
        mau.append((n, chars, ws, cao, W, strip))
        print('font %d %-12s %3d chu, o %2dx%2d -> %s %08x' % (i, n, len(chars), W, cao, strip.size, crc))
    if xem:
        ve_xem(mau, xem)
    return ds


def ve_xem(mau, path):
    txt = {'normal': 'Chơi tiếp Đổi tài khoản 10.000$ gyjq', 'border': 'Menu Chọn Đóng 25% Quay về', 'arial': 'bạn đang chơi tài khoản khách ạ',
           'black': 'xin chào 123, đặt tên đăng nhập', 'number': '0123456789$+-', 'smallRed': 'khach938004 100xu',
           'smallYellow': 'hung22 minhthugirl', 'big': 'AVATAR 250 CHÚC MỪNG'}
    rows = []
    for n, chars, ws, cao, W, strip in mau:
        s = txt[n]; x = 4
        bg = (232, 228, 200, 255) if n in ('black', 'normal') else (70, 120, 150, 255)
        R = Image.new('RGBA', (1600, (cao + 4) * S), bg)
        for ch in s:
            if ch == ' ':
                x += (ws[0] >> 1) * S; continue
            kx = chars.find(ch)
            if kx < 0: kx = chars.find(ch.lower())
            if kx < 0: kx = 0
            R.alpha_composite(strip.crop((0, kx * cao * S, W * S, (kx + 1) * cao * S)), (x, 2 * S))
            x += ws[kx] * S
        rows.append(R.crop((0, 0, x + 8, R.height)))
    H = sum(r.height + 6 for r in rows); Wd = max(r.width for r in rows)
    Sx = Image.new('RGBA', (Wd, H), (30, 30, 30, 255)); y = 0
    for r in rows:
        Sx.alpha_composite(r, (0, y)); y += r.height + 6
    Sx.convert('RGB').save(path)


if __name__ == '__main__':
    xem = sys.argv[sys.argv.index('--xem') + 1] if '--xem' in sys.argv else None
    ds = lam(sys.argv[1], sys.argv[2], xem)
    print(len(ds), 'font')
