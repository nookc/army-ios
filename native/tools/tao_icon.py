#!/usr/bin/env python3
"""v8h-75i: sinh lai MOI bieu tuong app (iOS / Android / Windows) tu anh goc 32x32 pixel art (native/platform/icon32.png).
Truoc: iOS chi co 1 anh 1024 -> actool tu thu nho bang loc min (1024 / 180 = 5,69 lan, khong tron) -> o diem bi nhoe, vien loang mau
("nhieu"); 120 / 180 / mipmap Android cung thu nho kieu min nhu vay. Nay moi co anh deu phong bang lay diem gan nhat (nearest) tu anh goc:
o diem sac canh, giu dung 30 mau goc. Rieng anh nho hon 32 (16 / 24 cua .ico) moi thu nho min (nho hon goc thi nearest lam mat net).
    python3 native/tools/tao_icon.py      (chay tu thu muc goc repo)"""
import os
import sys

from PIL import Image

N = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
GOC = os.path.join(N, 'platform', 'icon32.png')


def phong(src, n):
    if n >= src.width:
        return src.resize((n, n), Image.NEAREST)
    return src.resize((n, n), Image.LANCZOS)


def main():
    src = Image.open(GOC).convert('RGB')
    assert src.size == (32, 32), src.size
    ra = []

    def luu(im, *duong):
        p = os.path.join(N, 'platform', *duong)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        im.save(p, optimize=True)
        ra.append((os.path.relpath(p, N), im.size[0]))

    # iOS: 60@2x / 60@3x de trong goi .app (CFBundleIconFiles) + bo day du cho Assets.car (actool dung nguyen anh, khong tu thu nho)
    for pt, sc in ((20, 2), (20, 3), (29, 2), (29, 3), (40, 2), (40, 3), (60, 2), (60, 3)):
        luu(phong(src, pt * sc), 'ios', 'res', 'AppIcon%dx%d@%dx.png' % (pt, pt, sc))
    luu(phong(src, 1024), 'ios', 'res', 'AppIcon-1024.png')
    # anh lon o goc tep .ipa (trang cai dat / iTunes doc) - 512 = 16 lan, 1024 = 32 lan: o vuong deu tuyet doi
    luu(phong(src, 512), 'ios', 'res', 'iTunesArtwork.png')
    luu(phong(src, 1024), 'ios', 'res', 'iTunesArtwork@2x.png')
    # Android (bieu tuong cu: vuong, khong trong suot nhu ban truoc)
    for d, n in (('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)):
        luu(phong(src, n).convert('RGBA'), 'android', 'res', 'mipmap-' + d, 'ic_launcher.png')
    # Windows .ico
    co = [16, 24, 32, 48, 64, 128, 256]
    big = phong(src, 256).convert('RGBA')
    p = os.path.join(N, 'platform', 'win', 'army2.ico')
    big.save(p, format='ICO', sizes=[(n, n) for n in co], append_images=[phong(src, n).convert('RGBA') for n in co if n != 256])
    ra.append((os.path.relpath(p, N), co))
    for r in ra:
        print('  %-48s %s' % r)


if __name__ == '__main__':
    sys.exit(main())
