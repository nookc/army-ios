#!/usr/bin/env python3
"""Bieu tuong rieng cua ban "Avatar 250 HD" tu 1 anh vuong (chu server dua): lam sach 4 goc bo tron san co trong anh (keo mau
tu trong ra) roi ra cac co cho iOS (platform/avatar-ios/res-hd) va Android (platform/avatar-android/res-hd: bieu tuong thuong
bo goc + bieu tuong "adaptive" cho Android 8+).

  python3 mkicon_hd.py <anh nguon> [ban kinh goc / canh anh, mac dinh 0.177] [vien cat, mac dinh 3]
"""
import math, os, sys
from PIL import Image, ImageDraw, ImageFilter

N = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def lam_sach(im, ti_le, vien):
    """cat vien (mep khu rang cua) + to 4 goc ngoai cung tron bang mau lay tu ben trong theo huong tam goc"""
    im = im.convert('RGB')
    w, h = im.size
    rho = ti_le * w
    im = im.crop((vien, vien, w - vien, h - vien))
    w, h = im.size
    px = im.load()
    c = rho - vien                      # tam cung tron cach 2 canh c diem
    r_ngoai, r_lay = rho - 4.0, rho - 9.0
    n = int(math.ceil(c)) + 1
    for (ox, oy, sx, sy) in ((0, 0, 1, 1), (w - 1, 0, -1, 1), (0, h - 1, 1, -1), (w - 1, h - 1, -1, -1)):
        cx, cy = ox + sx * c, oy + sy * c
        for j in range(n):
            for i in range(n):
                x, y = ox + sx * i, oy + sy * j
                dx, dy = x - cx, y - cy
                d = math.hypot(dx, dy)
                if d <= r_ngoai or dx * sx > 0 or dy * sy > 0:
                    continue
                qx = min(w - 1, max(0, int(round(cx + dx / d * r_lay))))
                qy = min(h - 1, max(0, int(round(cy + dy / d * r_lay))))
                px[x, y] = px[qx, qy]
    # lam min vung vua to (cac tia mau)
    mo = im.filter(ImageFilter.GaussianBlur(3))
    m = Image.new('L', im.size, 0)
    d = ImageDraw.Draw(m)
    d.rectangle([0, 0, w, h], fill=255)
    d.rounded_rectangle([0, 0, w - 1, h - 1], radius=rho - vien - 5, fill=0)
    m = m.filter(ImageFilter.GaussianBlur(1.5))
    return Image.composite(mo, im, m)


def bo_goc(im, ti_le=0.19):
    """bieu tuong Android kieu cu: goc bo tron trong suot (khu rang cua x4)"""
    w = im.size[0]
    m = Image.new('L', (w * 4, w * 4), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, w * 4 - 1, w * 4 - 1], radius=ti_le * w * 4, fill=255)
    out = im.convert('RGBA')
    out.putalpha(m.resize((w, w), Image.LANCZOS))
    return out


def nen_adaptive(im, co):
    """lop nen 108dp: anh nam o 72dp giua (vung launcher hien), phan ria keo mau mep ra + lam mo"""
    trong = int(round(co * 2 / 3.0))
    nho = im.resize((trong, trong), Image.LANCZOS)
    le = (co - trong) // 2
    nen = Image.new('RGB', (co, co))
    nen.paste(nho, (le, le))
    px, q = nen.load(), nho.load()
    for y in range(co):
        for x in range(co):
            if le <= x < le + trong and le <= y < le + trong:
                continue
            px[x, y] = q[min(trong - 1, max(0, x - le)), min(trong - 1, max(0, y - le))]
    mo = nen.filter(ImageFilter.GaussianBlur(co / 60.0))
    mo.paste(nho, (le, le))
    return mo


if __name__ == '__main__':
    src = sys.argv[1]
    ti_le = float(sys.argv[2]) if len(sys.argv) > 2 else 0.177
    vien = int(sys.argv[3]) if len(sys.argv) > 3 else 3
    sach = lam_sach(Image.open(src), ti_le, vien)
    io = os.path.join(N, 'platform', 'avatar-ios', 'res-hd')
    os.makedirs(io, exist_ok=True)
    sach.resize((1024, 1024), Image.LANCZOS).save(os.path.join(io, 'AppIcon-1024.png'), optimize=True)
    sach.resize((180, 180), Image.LANCZOS).save(os.path.join(io, 'AppIcon60x60@3x.png'), optimize=True)
    sach.resize((120, 120), Image.LANCZOS).save(os.path.join(io, 'AppIcon60x60@2x.png'), optimize=True)
    an = os.path.join(N, 'platform', 'avatar-android', 'res-hd')
    for ten, co in (('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)):
        d = os.path.join(an, 'mipmap-' + ten)
        os.makedirs(d, exist_ok=True)
        bo_goc(sach.resize((co, co), Image.LANCZOS)).save(os.path.join(d, 'ic_launcher.png'), optimize=True)
        nen_adaptive(sach, co * 108 // 48).save(os.path.join(d, 'ic_launcher_bg.png'), optimize=True)
    d = os.path.join(an, 'mipmap-mdpi')
    Image.new('RGBA', (108, 108), (0, 0, 0, 0)).save(os.path.join(d, 'ic_launcher_fg.png'), optimize=True)
    d = os.path.join(an, 'mipmap-anydpi-v26')
    os.makedirs(d, exist_ok=True)
    open(os.path.join(d, 'ic_launcher.xml'), 'w').write(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@mipmap/ic_launcher_bg" />\n'
        '    <foreground android:drawable="@mipmap/ic_launcher_fg" />\n'
        '</adaptive-icon>\n')
    sach.save('/tmp/icon_hd/sach.png')
    print('xong', sach.size)
