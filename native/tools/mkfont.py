"""Sinh font bitmap (alpha 8 bit) cho Graphics.drawString: cao dong 14, duong chan chu 11 (giong bo chay cu)."""
import sys
from PIL import Image, ImageDraw, ImageFont
TTF = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
H, BASE, SIZE = 14, 11, 11
cps = list(range(0x20, 0x7F)) + list(range(0xA0, 0x180)) + [0x1A0, 0x1A1, 0x1AF, 0x1B0] + \
    list(range(0x1EA0, 0x1EFA)) + [0x300, 0x301, 0x303, 0x309, 0x323, 0x20AB, 0x2022, 0x2026, 0x2018, 0x2019, 0x201C,
                                   0x201D, 0x2013, 0x2014, 0xFFFD]
f = ImageFont.truetype(TTF, SIZE)
asc, desc = f.getmetrics()
out_cp, out_adv, out_w, out_off, bits = [], [], [], [], []
for cp in sorted(set(cps)):
    ch = chr(cp)
    adv = int(round(f.getlength(ch)))
    if adv <= 0:
        adv = 0
    l, t, r, b = f.getbbox(ch)
    w = max(1, r - min(0, l)) if r > 0 else max(1, adv)
    w = min(max(w, adv, 1), 24)
    im = Image.new('L', (w, H), 0)
    d = ImageDraw.Draw(im)
    d.text((0, BASE - asc), ch, font=f, fill=255)
    out_cp.append(cp)
    out_adv.append(adv)
    out_w.append(w)
    out_off.append(len(bits))
    bits.extend(im.tobytes())
o = ['/* sinh boi tools/mkfont.py tu DejaVuSans (giay phep Bitstream Vera / DejaVu: tu do) */',
     '#define FONT_H %d' % H, '#define FONT_BASE %d' % BASE, '#define FONT_N %d' % len(out_cp),
     'static const uint16_t FONT_CP[] = {%s};' % ','.join(map(str, out_cp)),
     'static const uint8_t FONT_ADV[] = {%s};' % ','.join(map(str, out_adv)),
     'static const uint8_t FONT_W[] = {%s};' % ','.join(map(str, out_w)),
     'static const uint32_t FONT_OFF[] = {%s};' % ','.join(map(str, out_off))]
rows = []
for i in range(0, len(bits), 64):
    rows.append(','.join(map(str, bits[i:i + 64])))
o.append('static const uint8_t FONT_BITS[] = {\n%s};' % ',\n'.join(rows))
open(sys.argv[1], 'w').write('\n'.join(o) + '\n')
print(len(out_cp), 'glyphs', len(bits), 'bytes')
