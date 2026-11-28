#!/usr/bin/env python3
"""Ve vat trang tri / cho chup anh cho ban "Avatar 250 HD" (tu ve bang hinh hoc, khong dung anh cua ai).
Ra hdx/p_<ten>.png: anh x4 (chunk apHd = 4), nen trong suot. May ve HD tu them khoi noi + bong do luc chay.

  python3 mkprops.py <thu muc ra hdx> [--xem tong_hop.png]
"""
import io, math, os, struct, sys, zlib, random
from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageChops

S, SS = 4, 2
U = S * SS                                      # diem ve / 1 diem logic
HERE = os.path.dirname(os.path.abspath(__file__))
TTF = os.path.join(HERE, 'Baloo2.ttf')


def toi(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3])


def tron(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


class Cv:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.im = Image.new('RGBA', (w * U, h * U), (0, 0, 0, 0))

    def _mask(self):
        return Image.new('L', self.im.size, 0)

    def _to(self, m, fill, fill2=None, vien=None, dv=0.55, sang=None):
        """to mat na m: mau fill (-> fill2 tu tren xuong), vien mau 'vien' day dv diem logic, sang = (cx, cy, rx, ry, do).
        Chi lam trong khung bao cua hinh (nhanh)."""
        bb = m.getbbox()
        if not bb:
            return
        r = max(1, int(dv * U)) if vien is not None else 0
        x0, y0 = max(0, bb[0] - r - 2), max(0, bb[1] - r - 2)
        x1, y1 = min(m.size[0], bb[2] + r + 2), min(m.size[1], bb[3] + r + 2)
        mc = m.crop((x0, y0, x1, y1))
        if vien is not None:
            o = mc.filter(ImageFilter.MaxFilter(2 * r + 1))
            self.im.paste(Image.new('RGBA', mc.size, tuple(vien) + (255,)), (x0, y0), o)
        lop = Image.new('RGBA', mc.size, tuple(fill) + (255,))
        if fill2 is not None:
            g = Image.linear_gradient('L').resize((bb[2] - bb[0], bb[3] - bb[1]))
            a = Image.new('RGB', g.size, tuple(fill)); b = Image.new('RGB', g.size, tuple(fill2))
            lop.paste(Image.composite(b, a, g), (bb[0] - x0, bb[1] - y0))
        if sang:
            cx, cy, rx, ry, do = sang
            h = Image.new('L', mc.size, 0)
            ImageDraw.Draw(h).ellipse([(cx - rx) * U - x0, (cy - ry) * U - y0, (cx + rx) * U - x0, (cy + ry) * U - y0], fill=int(255 * do))
            h = h.filter(ImageFilter.GaussianBlur(max(rx, ry) * U * 0.35))
            lop = Image.composite(Image.new('RGBA', mc.size, (255, 255, 255, 255)), lop, h)
        self.im.paste(lop, (x0, y0), mc)

    def ell(self, cx, cy, rx, ry, fill, fill2=None, vien='auto', **k):
        m = self._mask()
        ImageDraw.Draw(m).ellipse([(cx - rx) * U, (cy - ry) * U, (cx + rx) * U, (cy + ry) * U], fill=255)
        self._to(m, fill, fill2, toi(fill2 or fill, 0.5) if vien == 'auto' else vien, **k)

    def poly(self, pts, fill, fill2=None, vien='auto', **k):
        m = self._mask()
        ImageDraw.Draw(m).polygon([(x * U, y * U) for x, y in pts], fill=255)
        self._to(m, fill, fill2, toi(fill2 or fill, 0.5) if vien == 'auto' else vien, **k)

    def rrect(self, x0, y0, x1, y1, r, fill, fill2=None, vien='auto', **k):
        m = self._mask()
        ImageDraw.Draw(m).rounded_rectangle([x0 * U, y0 * U, x1 * U, y1 * U], radius=r * U, fill=255)
        self._to(m, fill, fill2, toi(fill2 or fill, 0.5) if vien == 'auto' else vien, **k)

    def line(self, pts, w, fill, vien=None, dv=0.55, **k):
        m = self._mask()
        d = ImageDraw.Draw(m)
        P = [(x * U, y * U) for x, y in pts]
        d.line(P, fill=255, width=max(1, int(w * U)), joint='curve')
        r = w * U / 2
        for x, y in (P[0], P[-1]):
            d.ellipse([x - r, y - r, x + r, y + r], fill=255)
        self._to(m, fill, None, vien, dv, **k)

    def arc(self, cx, cy, rx, ry, a0, a1, w, fill, vien=None, n=40, dv=0.55, **k):
        pts = [(cx + rx * math.cos(math.radians(a0 + (a1 - a0) * i / n)), cy + ry * math.sin(math.radians(a0 + (a1 - a0) * i / n))) for i in range(n + 1)]
        self.line(pts, w, fill, vien, dv, **k)

    def chu(self, s, cx, cy, co, fill, vien=None, dv=0.6, wght=800):
        f = ImageFont.truetype(TTF, int(co * U))
        try:
            f.set_variation_by_axes([wght])
        except Exception:
            pass
        m = self._mask()
        ImageDraw.Draw(m).text((cx * U, cy * U), s, font=f, fill=255, anchor='mm')
        self._to(m, fill, None, vien, dv)

    def tim(self, cx, cy, r, fill, fill2=None, vien='auto', **k):
        pts = []
        for i in range(60):
            t = 2 * math.pi * i / 60
            x = 16 * math.sin(t) ** 3
            y = -(13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t))
            pts.append((cx + x * r / 16.0, cy + y * r / 16.0))
        self.poly(pts, fill, fill2, vien, sang=(cx - r * 0.38, cy - r * 0.42, r * 0.3, r * 0.22, 0.75), **k)

    def sao(self, cx, cy, r, fill, fill2=None, vien='auto', n=5, trong=0.45, goc=-90, **k):
        pts = []
        for i in range(n * 2):
            a = math.radians(goc + i * 180.0 / n)
            rr = r if i % 2 == 0 else r * trong
            pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
        self.poly(pts, fill, fill2, vien, **k)

    def hoa(self, cx, cy, r, mau, nhuy=(255, 214, 60), canh=5):
        for i in range(canh):
            a = 2 * math.pi * i / canh - math.pi / 2
            self.ell(cx + r * 0.62 * math.cos(a), cy + r * 0.62 * math.sin(a), r * 0.52, r * 0.52, mau, toi(mau, 0.82), dv=0.35)
        self.ell(cx, cy, r * 0.36, r * 0.36, nhuy, toi(nhuy, 0.8), dv=0.3)

    def la(self, cx, cy, r, goc, mau=(70, 170, 70)):
        a = math.radians(goc)
        pts = []
        for i in range(24):
            t = 2 * math.pi * i / 24
            x, y = r * math.cos(t), r * 0.48 * math.sin(t) * (1 - 0.3 * math.cos(t))
            pts.append((cx + x * math.cos(a) - y * math.sin(a), cy + x * math.sin(a) + y * math.cos(a)))
        self.poly(pts, tron(mau, (255, 255, 255), 0.12), toi(mau, 0.8), dv=0.35)

    def xong(self):
        return self.im.resize((self.w * S, self.h * S), Image.LANCZOS)


# ------------------------------------------------------------------ cac vat
GO = (176, 120, 70); GO2 = (128, 82, 46)
XANH = (64, 168, 72); XANH2 = (36, 120, 52)


def cong_hoa():
    c = Cv(80, 74)
    rnd = random.Random(3)
    c.rrect(8, 26, 14, 72, 2, GO, GO2); c.rrect(66, 26, 72, 72, 2, GO, GO2)            # 2 cot
    c.arc(40, 30, 30, 24, 180, 360, 6.5, XANH2, toi(XANH2, 0.55))
    for i in range(34):                                                                 # la quanh vom + cot
        a = math.radians(180 + i * 180 / 33)
        c.la(40 + 30 * math.cos(a) + rnd.uniform(-2, 2), 30 + 24 * math.sin(a) + rnd.uniform(-2, 2), 4.6, rnd.uniform(0, 360), rnd.choice([XANH, (90, 190, 80), (50, 150, 70)]))
    for x in (11, 69):
        for y in range(32, 70, 5):
            c.la(x + rnd.uniform(-2.5, 2.5), y, 4.2, rnd.uniform(0, 360), rnd.choice([XANH, (90, 190, 80)]))
    mau = [(255, 120, 170), (255, 220, 80), (255, 255, 255), (190, 130, 255), (255, 150, 90), (255, 90, 110)]
    for i in range(15):
        a = math.radians(184 + i * 172 / 14)
        c.hoa(40 + 30 * math.cos(a), 30 + 24 * math.sin(a), 3.4 + rnd.uniform(0, 1.2), mau[i % len(mau)])
    for x in (11, 69):
        for j, y in enumerate((38, 50, 62)):
            c.hoa(x + (1.5 if j % 2 else -1.5), y, 3.2, mau[(j + x) % len(mau)])
    return c.xong()


def khung_anh():
    c = Cv(78, 66)
    c.rrect(6, 14, 12, 64, 1.5, GO, GO2); c.rrect(66, 14, 72, 64, 1.5, GO, GO2)
    c.rrect(4, 10, 74, 22, 3, (255, 196, 70), (240, 150, 40))                           # bang tren
    c.chu('CHECK-IN', 39, 15.4, 8.6, (255, 255, 255), (150, 70, 20), 0.55)
    c.rrect(10, 52, 68, 58, 1.5, GO, GO2)                                               # thanh duoi
    for i in range(9):                                                                  # bong den
        x = 9 + i * 7.6
        c.ell(x, 24.5, 1.9, 1.9, (255, 244, 170), (255, 206, 80), sang=(x - 0.5, 24, 0.9, 0.9, 0.9))
    for y in (30, 38, 46):
        for x in (9, 69):
            c.ell(x, y, 1.7, 1.7, (255, 244, 170), (255, 206, 80))
    c.sao(8, 9, 5, (255, 226, 70), (250, 170, 30)); c.sao(70, 9, 5, (255, 226, 70), (250, 170, 30))
    c.tim(39, 6.5, 4.6, (255, 90, 130), (220, 40, 90))
    return c.xong()


def _huong_duong(c, x, y0, cao, r):
    c.line([(x, y0), (x + 0.6, y0 - cao * 0.5), (x, y0 - cao)], 1.6, (70, 150, 60), (36, 96, 40), 0.4)
    c.la(x - 4.5, y0 - cao * 0.35, 4.8, 200, XANH); c.la(x + 4.8, y0 - cao * 0.55, 4.8, -20, XANH)
    cy = y0 - cao
    for i in range(14):
        a = 2 * math.pi * i / 14
        c.ell(x + r * 0.9 * math.cos(a), cy + r * 0.9 * math.sin(a), r * 0.42, r * 0.42, (255, 214, 50), (246, 160, 20), dv=0.3)
    c.ell(x, cy, r * 0.66, r * 0.66, (150, 90, 40), (96, 54, 24), sang=(x - r * 0.2, cy - r * 0.2, r * 0.25, r * 0.2, 0.35))


def huong_duong():
    c = Cv(40, 50)
    _huong_duong(c, 10, 49, 26, 6.2); _huong_duong(c, 30, 49, 30, 6.6); _huong_duong(c, 20, 49, 38, 7.4)
    return c.xong()


def _bi(c, x, y, r, mat=False, sang=False):
    for dx, f in ((-0.55, 0.78), (0.55, 0.78), (-0.28, 0.92), (0.28, 0.92), (0, 1.0)):
        c.ell(x + dx * r, y, r * 0.52, r * 0.86 * f + r * 0.08, (255, 150, 40), (226, 96, 20), dv=0.4, sang=(x + dx * r - r * 0.15, y - r * 0.4, r * 0.2, r * 0.25, 0.35))
    c.rrect(x - r * 0.12, y - r * 1.2, x + r * 0.14, y - r * 0.72, 0.6, (96, 140, 50), (60, 100, 40), dv=0.35)
    if mat:
        m = (255, 236, 120) if sang else (70, 34, 10)
        v = (120, 50, 10) if sang else None
        c.poly([(x - r * 0.55, y - r * 0.12), (x - r * 0.2, y - r * 0.12), (x - r * 0.37, y - r * 0.5)], m, vien=v, dv=0.25)
        c.poly([(x + r * 0.55, y - r * 0.12), (x + r * 0.2, y - r * 0.12), (x + r * 0.37, y - r * 0.5)], m, vien=v, dv=0.25)
        c.poly([(x - r * 0.6, y + r * 0.15), (x - r * 0.3, y + r * 0.3), (x - r * 0.1, y + r * 0.16), (x + r * 0.1, y + r * 0.3), (x + r * 0.3, y + r * 0.16), (x + r * 0.6, y + r * 0.15),
                (x + r * 0.34, y + r * 0.58), (x, y + r * 0.44), (x - r * 0.34, y + r * 0.58)], m, vien=v, dv=0.25)


def rom_bi():
    c = Cv(50, 34)
    for x0, y0, w, h in ((3, 12, 26, 20), (22, 4, 24, 15), (26, 18, 22, 14)):
        c.rrect(x0, y0, x0 + w, y0 + h, 2.2, (238, 204, 110), (204, 156, 66))
        for i in range(1, 4):
            c.line([(x0 + w * i / 4.0, y0 + 1), (x0 + w * i / 4.0, y0 + h - 1)], 0.5, (180, 130, 50))
        c.line([(x0 + 1, y0 + h * 0.5), (x0 + w - 1, y0 + h * 0.5)], 0.9, (150, 96, 40))
    _bi(c, 12, 27, 6.0); _bi(c, 40, 28, 4.6)
    return c.xong()


def bu_nhin():
    c = Cv(36, 52)
    c.rrect(17, 18, 19.4, 51, 0.8, GO, GO2)                                             # coc
    c.rrect(3, 22, 33, 25, 1, GO, GO2)                                                  # tay
    c.poly([(9, 21), (27, 21), (29, 40), (7, 40)], (80, 140, 220), (50, 96, 180))       # ao
    c.poly([(3, 21.5), (9, 21), (9, 27), (3, 26)], (80, 140, 220), (50, 96, 180)); c.poly([(33, 21.5), (27, 21), (27, 27), (33, 26)], (80, 140, 220), (50, 96, 180))
    for x in (1.5, 34.5):                                                               # rom o tay
        for dy in (-1.6, 0, 1.6):
            c.line([(x - 1.2, 23.6 + dy * 1.4), (x + 1.2, 23.6 + dy)], 0.7, (236, 200, 100), (170, 120, 40), 0.25)
    c.rrect(13, 26, 17, 30, 0.5, (236, 96, 80), (200, 60, 50), dv=0.3); c.rrect(20, 31, 24, 35, 0.5, (250, 210, 80), (220, 160, 40), dv=0.3)     # mieng va
    c.ell(18, 13, 7, 7, (246, 216, 150), (224, 180, 110), sang=(16, 10.5, 2.2, 1.8, 0.4))    # dau
    c.ell(15.4, 12.4, 1.0, 1.2, (50, 30, 20), vien=None); c.ell(20.6, 12.4, 1.0, 1.2, (50, 30, 20), vien=None)
    c.arc(18, 14.2, 3.0, 2.2, 20, 160, 0.6, (120, 60, 30))
    c.poly([(7, 8.5), (29, 8.5), (24.5, 5.5), (22, -0.2 + 1.5), (14, 1.3), (11.5, 5.5)], (214, 160, 70), (170, 112, 40))                           # non rom
    c.line([(12, 6), (24, 6)], 1.3, (200, 60, 50))
    return c.xong()


def co_day():
    c = Cv(124, 52)
    c.rrect(3, 6, 6.4, 51, 1, GO, GO2); c.rrect(117.6, 6, 121, 51, 1, GO, GO2)
    c.ell(4.7, 5, 2.6, 2.6, (255, 214, 60), (240, 160, 30)); c.ell(119.3, 5, 2.6, 2.6, (255, 214, 60), (240, 160, 30))
    f = lambda x: 9 + 9 * (1 - ((x - 62) / 57.0) ** 2)                                  # day vong
    c.line([(x, f(x)) for x in range(5, 120, 3)], 0.8, (120, 90, 60))
    mau = [(255, 90, 90), (255, 200, 50), (80, 200, 120), (80, 160, 255), (200, 120, 255), (255, 130, 200)]
    for i in range(11):
        x = 12 + i * 10
        c.poly([(x - 4, f(x - 4)), (x + 4, f(x + 4)), (x, f(x) + 11)], mau[i % 6], toi(mau[i % 6], 0.78), dv=0.4)
    return c.xong()


def cay_thong():
    c = Cv(60, 88)
    c.rrect(26, 74, 34, 87, 1.2, GO, GO2)
    for i, (y, w, h) in enumerate(((60, 27, 26), (44, 22, 24), (29, 17, 22), (15, 12, 18))):
        c.poly([(30 - w, y + 16), (30, y - h + 16), (30 + w, y + 16), (30 + w * 0.5, y + 13.5), (30, y + 17.5), (30 - w * 0.5, y + 13.5)], (60, 170, 90), (26, 110, 60))
        c.poly([(30 - w * 0.62, y + 3), (30, y - h + 17.5), (30 + w * 0.62, y + 3), (30 + w * 0.3, y + 5.6), (30, y + 3.2), (30 - w * 0.3, y + 5.6)], (246, 250, 255), (214, 230, 248), vien=None)   # tuyet
    rnd = random.Random(8)
    mau = [(255, 80, 80), (255, 210, 60), (90, 170, 255), (255, 130, 210), (255, 255, 255)]
    for i in range(16):
        y = 16 + i * 3.7
        w = 5 + (y - 10) * 0.33
        x = 30 + rnd.uniform(-w, w)
        m = mau[i % 5]
        c.ell(x, y, 2.3, 2.3, m, toi(m, 0.7), dv=0.35, sang=(x - 0.7, y - 0.8, 0.9, 0.8, 0.8))
    c.sao(30, 5.5, 6.2, (255, 232, 90), (250, 176, 30))
    return c.xong()


def nguoi_tuyet():
    c = Cv(38, 46)
    c.line([(8, 24), (2, 16)], 1.0, GO2); c.line([(30, 24), (36, 15)], 1.0, GO2)
    c.ell(19, 33, 12, 11.5, (252, 254, 255), (208, 224, 244), sang=(15, 28, 4, 3, 0.6))
    c.ell(19, 17, 8.4, 8, (252, 254, 255), (214, 228, 246), sang=(16, 13.5, 3, 2.4, 0.6))
    c.rrect(10.5, 22.4, 27.5, 25.6, 1.4, (236, 70, 70), (196, 40, 50)); c.rrect(22, 24, 25.4, 33, 1.2, (236, 70, 70), (196, 40, 50))       # khan
    c.ell(16, 15.6, 1.1, 1.3, (40, 40, 50), vien=None); c.ell(22, 15.6, 1.1, 1.3, (40, 40, 50), vien=None)
    c.poly([(19, 17.4), (26.5, 19.2), (19, 20.2)], (255, 150, 50), (236, 110, 30), dv=0.3)
    for x, y in ((19, 30), (19, 35), (19, 40)):
        c.ell(x, y, 1.2, 1.2, (50, 50, 60), vien=None)
    c.rrect(11.5, 8.4, 26.5, 10.6, 0.8, (50, 54, 70), (30, 32, 44)); c.rrect(14, 0.8, 24, 9.4, 1.2, (60, 64, 82), (34, 36, 50)); c.rrect(14, 6, 24, 8, 0.2, (236, 70, 70), vien=None)
    return c.xong()


def ong_noel():
    c = Cv(40, 50)
    DO = (228, 50, 50); DO2 = (176, 26, 36); TR = (255, 255, 255); TR2 = (220, 228, 240)
    c.poly([(26, 26), (39, 30), (38, 46), (27, 47)], (150, 100, 60), (104, 66, 36))      # bao qua
    c.rrect(11.5, 43, 18.5, 49.4, 1.6, (50, 44, 44), (24, 22, 24)); c.rrect(20.5, 43, 27.5, 49.4, 1.6, (50, 44, 44), (24, 22, 24))   # giay
    c.rrect(8, 22, 31, 45, 6, DO, DO2, sang=(14, 27, 4, 3, 0.25))                        # than
    c.rrect(8, 39.4, 31, 43.4, 1.6, TR, TR2); c.rrect(17.6, 22, 21.4, 42, 0.4, TR, TR2, vien=None)
    c.rrect(8, 31.4, 31, 35, 0.6, (50, 44, 44), (24, 22, 24), dv=0.3); c.rrect(16.8, 30.6, 22.2, 35.8, 0.8, (255, 214, 70), (236, 160, 30), dv=0.35)
    c.rrect(2.4, 24, 9.6, 36, 3, DO, DO2); c.rrect(29.4, 24, 36.6, 36, 3, DO, DO2)        # tay
    c.ell(6, 37, 3.3, 3.1, TR, TR2); c.ell(33, 37, 3.3, 3.1, TR, TR2)
    c.ell(19.5, 15, 8.2, 7.8, (255, 214, 180), (240, 180, 140))                           # mat
    c.poly([(11, 15.6), (28, 15.6), (27, 23), (19.5, 29), (12, 23)], TR, TR2)             # rau
    c.ell(19.5, 17.8, 1.9, 1.5, (246, 150, 130), (226, 110, 96), dv=0.3)
    c.ell(16.2, 14, 1.0, 1.2, (40, 30, 30), vien=None); c.ell(22.8, 14, 1.0, 1.2, (40, 30, 30), vien=None)
    c.arc(15.6, 19.6, 4.2, 2.2, 180, 350, 1.5, TR, TR2, n=12); c.arc(23.4, 19.6, 4.2, 2.2, 190, 360, 1.5, TR, TR2, n=12)       # ria
    c.poly([(11.2, 10.5), (27.8, 10.5), (31.5, 1.8), (20, 2.4)], DO, DO2)                 # mu
    c.rrect(10, 8.6, 29, 12.4, 1.8, TR, TR2); c.ell(32.2, 2.6, 2.6, 2.6, TR, TR2)
    return c.xong()


def _hop(c, x0, y0, w, h, mau, no):
    c.rrect(x0, y0, x0 + w, y0 + h, 1, mau, toi(mau, 0.72))
    c.rrect(x0 + w * 0.42, y0, x0 + w * 0.58, y0 + h, 0, no, toi(no, 0.8), vien=None)
    c.rrect(x0 - 0.8, y0 - 1.5, x0 + w + 0.8, y0 + 2.6, 0.8, tron(mau, (255, 255, 255), 0.18), mau)
    c.ell(x0 + w * 0.36, y0 - 2.6, 2.3, 1.7, no, toi(no, 0.75), dv=0.3); c.ell(x0 + w * 0.64, y0 - 2.6, 2.3, 1.7, no, toi(no, 0.75), dv=0.3)


def hop_qua():
    c = Cv(46, 30)
    _hop(c, 2, 11, 17, 18, (236, 70, 80), (255, 220, 90)); _hop(c, 27, 14, 16, 15, (80, 150, 240), (255, 255, 255)); _hop(c, 14, 6, 15, 12, (80, 190, 110), (255, 90, 100))
    return c.xong()


def cong_tim():
    c = Cv(82, 76)
    for i in range(17):
        a = math.radians(180 + i * 180 / 16)
        m = [(255, 86, 120), (255, 150, 180), (236, 50, 90)][i % 3]
        c.tim(41 + 31 * math.cos(a), 36 + 26 * math.sin(a) + 1, 6.4, m, toi(m, 0.76))
    for x in (10, 72):
        for j, y in enumerate((44, 54, 64)):
            m = [(255, 150, 180), (255, 86, 120), (236, 50, 90)][j]
            c.tim(x, y, 6.0, m, toi(m, 0.76))
    c.rrect(8, 68, 12, 75, 0.8, GO, GO2); c.rrect(70, 68, 74, 75, 0.8, GO, GO2)
    c.tim(41, 8.5, 9.5, (255, 70, 110), (214, 30, 80))
    c.poly([(26, 6), (33, 9), (33, 13), (25, 12)], (255, 255, 255), (226, 232, 246)); c.poly([(56, 6), (49, 9), (49, 13), (57, 12)], (255, 255, 255), (226, 232, 246))    # canh nho
    return c.xong()


def chu_love():
    c = Cv(90, 40)
    c.rrect(4, 31, 86, 38, 2.4, (255, 190, 210), (240, 140, 170))
    f = ImageFont.truetype(TTF, int(30 * U))
    try:
        f.set_variation_by_axes([800])
    except Exception:
        pass
    for s, x in (('L', 13), ('V', 54), ('E', 76)):
        m = c._mask(); ImageDraw.Draw(m).text((x * U, 19 * U), s, font=f, fill=255, anchor='mm')
        sh = ImageChops.offset(m, int(1.2 * U), int(1.4 * U))
        c._to(sh, (150, 20, 60), None, None)
        c._to(m, (255, 96, 130), (226, 40, 90), (120, 10, 50), 0.6)
    c.tim(33.5, 19, 10, (255, 80, 120), (220, 30, 84))
    return c.xong()


def gau_tim():
    c = Cv(36, 38)
    NAU = (206, 150, 96); NAU2 = (160, 104, 60)
    c.ell(10, 7, 4.4, 4.4, NAU, NAU2); c.ell(26, 7, 4.4, 4.4, NAU, NAU2)
    c.ell(18, 27, 11, 10.5, NAU, NAU2); c.ell(8, 34, 4.6, 3.6, NAU, NAU2); c.ell(28, 34, 4.6, 3.6, NAU, NAU2)
    c.ell(18, 13, 10.4, 9.6, NAU, NAU2, sang=(14.5, 9, 3, 2.4, 0.3))
    c.ell(18, 16, 4.6, 3.6, (246, 220, 180), (226, 190, 150), dv=0.3)
    c.ell(18, 14.6, 1.5, 1.1, (60, 36, 24), vien=None); c.ell(13.8, 11.4, 1.1, 1.3, (50, 30, 20), vien=None); c.ell(22.2, 11.4, 1.1, 1.3, (50, 30, 20), vien=None)
    c.tim(18, 27, 7.6, (255, 80, 120), (220, 30, 84))
    c.ell(9.4, 25.4, 3.4, 3.0, NAU, NAU2); c.ell(26.6, 25.4, 3.4, 3.0, NAU, NAU2)
    return c.xong()


def cong_bong():
    c = Cv(86, 66)
    rnd = random.Random(5)
    mau = [(255, 90, 90), (255, 200, 50), (80, 200, 120), (80, 160, 255), (200, 120, 255), (255, 130, 200), (70, 210, 220)]
    pts = []
    for i in range(26):
        a = math.radians(180 + i * 180 / 25)
        pts.append((43 + 33 * math.cos(a), 36 + 28 * math.sin(a)))
    pts = [(10, 62), (10, 52), (10, 44)] + pts + [(76, 44), (76, 52), (76, 62)]
    for j, (x, y) in enumerate(pts):
        for d in range(2):
            m = mau[(j * 2 + d) % 7]
            r = 5.0 + rnd.uniform(-0.6, 0.8)
            xx, yy = x + rnd.uniform(-2.4, 2.4), y + rnd.uniform(-2.4, 2.4)
            c.ell(xx, yy, r * 0.92, r, m, toi(m, 0.7), dv=0.4, sang=(xx - r * 0.3, yy - r * 0.4, r * 0.28, r * 0.24, 0.75))
    return c.xong()


def sao_vui():
    c = Cv(46, 56)
    c.rrect(21.4, 26, 24.6, 55, 1, (236, 236, 244), (180, 184, 204))
    for i, m in enumerate(((255, 90, 90), (80, 160, 255), (255, 200, 50), (80, 200, 120))):
        c.line([(23, 30), (23 + (-1) ** i * (9 + i), 40 + i * 3)], 1.2, m, toi(m, 0.6), 0.3)
    c.sao(23, 16, 15.5, (255, 226, 70), (250, 166, 30), sang=(18, 11, 4, 3, 0.5))
    c.ell(18.4, 14.4, 1.3, 1.7, (60, 40, 20), vien=None); c.ell(27.6, 14.4, 1.3, 1.7, (60, 40, 20), vien=None)
    c.arc(23, 17.4, 4.6, 3.6, 15, 165, 1.0, (150, 70, 20))
    c.ell(15.4, 18.2, 1.6, 1.1, (255, 150, 150), vien=None); c.ell(30.6, 18.2, 1.6, 1.1, (255, 150, 150), vien=None)
    return c.xong()


def bi_ngo():
    c = Cv(52, 36)
    _bi(c, 13, 25, 8.6, True, True); _bi(c, 38, 26, 7.4, True, True); _bi(c, 26, 14, 6.4, True, True)
    return c.xong()


def cay_ma():
    c = Cv(66, 86)
    T = (84, 64, 86); T2 = (44, 32, 54)
    c.poly([(24, 85), (27, 52), (22, 36), (9, 26), (4, 14), (9, 15), (14, 23), (23, 28), (20, 12), (24, 4), (27, 13), (30, 28), (36, 18), (37, 6), (41, 8), (41, 20), (37, 32),
            (48, 26), (55, 13), (59, 15), (54, 28), (61, 30), (60, 34), (50, 34), (40, 44), (40, 58), (44, 85)], T, T2)
    c.ell(29.5, 50, 2.6, 3.4, (250, 190, 60), (236, 120, 30), dv=0.4); c.ell(37, 50, 2.6, 3.4, (250, 190, 60), (236, 120, 30), dv=0.4)        # mat
    c.poly([(28, 60), (31, 63), (33.5, 60.6), (36, 63), (39, 60), (37, 68), (33.5, 66), (30, 68)], (250, 190, 60), (236, 120, 30), dv=0.4)     # mieng
    c.line([(9, 26), (9, 38)], 0.6, (60, 50, 60)); _bi(c, 9, 43, 4.6, True, True)                                                                # den bi ngo treo
    return c.xong()


def bia_mo():
    c = Cv(50, 34)
    c.ell(25, 31, 23, 3.2, (96, 120, 90), (70, 92, 70), dv=0.3)
    for x, w, h in ((13, 15, 24), (36, 13, 19)):
        c.poly([(x - w / 2, 31)] + [(x + w / 2 * math.cos(math.radians(a)), 31 - h + w / 2 + w / 2 * -math.sin(math.radians(a))) for a in range(180, -1, -12)] + [(x + w / 2, 31)], (176, 180, 196), (120, 124, 146))
        c.chu('RIP', x, 31 - h * 0.48, 5.4, (84, 86, 104))
    # ma nho ngo ra
    c.poly([(21.5, 30), (21.5, 20), (23, 16.4), (26, 15.2), (29, 16.4), (30.5, 20), (30.5, 30), (28.6, 28), (26.8, 30), (25, 28), (23.2, 30)], (250, 252, 255), (214, 224, 244))
    c.ell(24.2, 20.6, 0.9, 1.2, (40, 40, 60), vien=None); c.ell(27.8, 20.6, 0.9, 1.2, (40, 40, 60), vien=None)
    return c.xong()


def canh_tt():
    """buc tuong ve doi canh thien than - dung truoc de chup anh"""
    c = Cv(84, 70)
    c.rrect(3, 4, 81, 66, 4, (120, 200, 236), (70, 150, 214))                            # bang nen troi
    c.rrect(3, 62, 81, 69, 2, GO, GO2)
    for x, y, r in ((14, 14, 5), (70, 12, 4), (60, 54, 4.5), (22, 56, 3.6)):             # may
        c.ell(x, y, r * 1.5, r * 0.8, (255, 255, 255), (232, 242, 252), vien=None); c.ell(x - r * 0.7, y - r * 0.5, r * 0.8, r * 0.7, (255, 255, 255), vien=None)
        c.ell(x + r * 0.5, y - r * 0.6, r * 0.9, r * 0.8, (255, 255, 255), vien=None)
    for sx in (-1, 1):                                                                   # 2 canh: 3 tang long
        cx = 42 + sx * 6
        for i in range(7):
            a = math.radians(-58 + i * 15)
            L = 30 - i * 1.6
            x1, y1 = cx + sx * L * math.cos(a), 30 + L * math.sin(a) * 0.95
            c.line([(cx, 32), ((cx + x1) / 2 + sx * 1.5, (32 + y1) / 2 - 3), (x1, y1)], 6.2 - i * 0.25, (255, 255, 255), (150, 190, 226), 0.5)
        for i in range(6):
            a = math.radians(-40 + i * 16)
            L = 19 - i * 1.2
            x1, y1 = cx + sx * L * math.cos(a), 31 + L * math.sin(a)
            c.line([(cx, 32), (x1, y1)], 5.0, (240, 248, 255), (170, 200, 232), 0.4)
        c.ell(cx + sx * 3, 31, 6, 7.5, (255, 255, 255), (226, 238, 252), vien=(150, 190, 226), dv=0.4)
    c.arc(42, 13, 8, 2.6, 0, 360, 1.5, (255, 226, 80), (214, 150, 20), dv=0.4)              # vong hao quang
    c.sao(12, 40, 3, (255, 240, 150), (255, 210, 80)); c.sao(72, 36, 3.4, (255, 240, 150), (255, 210, 80)); c.sao(66, 22, 2.2, (255, 255, 255), None, vien=None)
    return c.xong()


def dai_phun():
    c = Cv(78, 64)
    DA = (214, 220, 232); DA2 = (150, 160, 182); NC = (120, 206, 246); NC2 = (60, 150, 226)
    c.ell(39, 53, 36, 9.5, DA, DA2)                                                      # be duoi
    c.ell(39, 51, 32, 7.4, NC, NC2, vien=(90, 110, 150), dv=0.4, sang=(30, 49, 9, 2, 0.5))
    c.rrect(35, 30, 43, 52, 2, DA, DA2)                                                  # tru
    c.ell(39, 31, 19, 5.2, DA, DA2); c.ell(39, 29.6, 16, 3.6, NC, NC2, vien=(90, 110, 150), dv=0.35)
    c.rrect(36.6, 14, 41.4, 30, 1.6, DA, DA2)
    c.ell(39, 14.5, 9.5, 3.0, DA, DA2); c.ell(39, 13.6, 7.6, 2.0, NC, NC2, vien=(90, 110, 150), dv=0.3)
    for sx in (-1, 1):                                                                   # tia nuoc
        for r, y0, y1, w in ((13, 13, 29, 1.6), (24, 29, 50, 1.9)):
            pts = [(39 + sx * r * t, y0 - 9 * math.sin(math.pi * min(1, t * 1.0)) * (1 - t) * 2.2 + (y1 - y0) * t * t) for t in [i / 14.0 for i in range(15)]]
            c.line(pts, w, (214, 242, 255), (110, 180, 236), 0.3)
    c.line([(39, 13), (39, 4)], 1.8, (214, 242, 255), (110, 180, 236), 0.3)
    for x, y in ((39, 3), (36, 5.5), (42, 5.5), (15, 44), (63, 44), (26, 26), (52, 26)):
        c.ell(x, y, 1.3, 1.3, (236, 250, 255), vien=(110, 180, 236), dv=0.25)
    return c.xong()


def khi_cau():
    c = Cv(58, 92)
    mau = [(255, 96, 96), (255, 206, 70), (90, 190, 120), (80, 160, 250), (255, 150, 60), (190, 120, 250)]
    for i in range(6):                                                                   # mui bong tu ngoai vao
        f = 1.0 - i * 0.2 if i < 3 else None
    for i, fx in enumerate((1.0, 0.72, 0.42, 0.14)):
        for sx in (-1, 1):
            m = mau[(i * 2 + (0 if sx < 0 else 1)) % 6]
            c.ell(29 + sx * 0.0, 30, 26 * fx, 28, m, toi(m, 0.74), vien=(96, 50, 40) if i == 0 else None, dv=0.5)
            if i == 0:
                break
    c.ell(21, 18, 5, 8, (255, 255, 255), vien=None, sang=None) if False else None
    c.poly([(16, 52), (42, 52), (36, 62), (22, 62)], (236, 120, 60), (196, 80, 40))       # vay duoi bong
    for x0, x1 in ((18, 22.5), (40, 35.5), (26, 26.5), (32, 31.5)):
        c.line([(x0, 56), (x1, 76)], 0.6, (110, 80, 60))
    c.rrect(21, 75, 37, 89, 2.2, (206, 150, 90), (150, 100, 56))                          # gio
    for y in (79, 83, 86.5):
        c.line([(21.6, y), (36.4, y)], 0.5, (130, 86, 46))
    c.rrect(20, 73.6, 38, 77, 1.4, (226, 172, 110), (170, 116, 66))
    c.ell(20, 18, 4.6, 7.5, (255, 255, 255), None, vien=None)
    c.im.putalpha(c.im.getchannel('A'))
    return c.xong()


def xich_du():
    c = Cv(70, 62)
    c.line([(8, 60), (16, 6)], 3.2, GO, GO2, 0.45); c.line([(24, 60), (16, 6)], 3.2, GO, GO2, 0.45)
    c.line([(62, 60), (54, 6)], 3.2, GO, GO2, 0.45); c.line([(46, 60), (54, 6)], 3.2, GO, GO2, 0.45)
    c.rrect(10, 3, 60, 8.4, 2, GO, GO2)
    for x in (24, 46):
        c.line([(x, 8), (x, 40)], 0.9, (236, 236, 244), (120, 124, 146), 0.3)
    c.rrect(19, 26, 51, 40, 3, (255, 150, 180), (236, 96, 140))                           # lung ghe
    c.tim(35, 33, 4.4, (255, 255, 255), (255, 226, 236), vien=(214, 80, 120))
    c.rrect(17, 39, 53, 45.6, 2.4, (255, 180, 200), (240, 120, 160))                      # mat ghe
    rnd = random.Random(11)
    for i in range(9):
        x = 12 + i * 5.8
        c.hoa(x, 5.4 + rnd.uniform(-1.2, 1.2), 2.6, [(255, 120, 170), (255, 220, 80), (255, 255, 255), (190, 130, 255)][i % 4])
    return c.xong()


def den_long():
    c = Cv(96, 56)
    c.rrect(3, 4, 6.2, 55, 1, (60, 70, 96), (34, 40, 60)); c.rrect(89.8, 4, 93, 55, 1, (60, 70, 96), (34, 40, 60))
    f = lambda x: 8 + 7 * (1 - ((x - 48) / 43.0) ** 2)
    c.line([(x, f(x)) for x in range(5, 92, 3)], 0.7, (90, 80, 70))
    mau = [(255, 80, 80), (255, 190, 60), (255, 120, 190), (120, 210, 120), (110, 170, 255)]
    for i in range(6):
        x = 13 + i * 14
        y = f(x) + 8.5
        m = mau[i % 5]
        c.rrect(x - 2, y - 7.6, x + 2, y - 5.6, 0.4, (90, 60, 40), vien=None)
        c.ell(x, y, 5.2, 6.2, m, toi(m, 0.72), dv=0.4, sang=(x - 1.4, y - 2, 1.8, 2.2, 0.75))
        c.rrect(x - 2, y + 5.6, x + 2, y + 7.2, 0.4, (90, 60, 40), vien=None)
        c.line([(x, y + 7.2), (x, y + 11.5)], 0.6, (255, 220, 90))
    return c.xong()


def chau_hoa():
    c = Cv(64, 30)
    rnd = random.Random(21)
    for i, (x, m) in enumerate(((11, (255, 120, 170)), (32, (255, 220, 80)), (53, (190, 130, 255)))):
        for j in range(5):
            c.la(x + rnd.uniform(-7, 7), 14 + rnd.uniform(-3, 2), 4.4, rnd.uniform(0, 360), XANH)
        for j in range(5):
            c.hoa(x + (j - 2) * 3.4 + rnd.uniform(-0.6, 0.6), 9.5 + (abs(j - 2)) * 1.6 + rnd.uniform(-1, 1), 3.2, m if j % 2 == 0 else (255, 255, 255))
        c.poly([(x - 8, 16), (x + 8, 16), (x + 6, 29), (x - 6, 29)], (226, 130, 80), (176, 86, 50))
        c.rrect(x - 9, 14.4, x + 9, 18, 1.2, (240, 150, 96), (196, 100, 60))
    return c.xong()


def cong_trai():
    """cong chao nong trai (dat sau cong "FARM" cua game): 2 tru da + mai go + bang ten + den + hoa"""
    c = Cv(132, 92)
    DA = (206, 196, 180); DA2 = (150, 138, 124)
    for x in (10, 122):                                                                  # tru da
        c.rrect(x - 8, 34, x + 8, 90, 2, DA, DA2)
        for yy in range(40, 90, 9):
            c.line([(x - 7.4, yy), (x + 7.4, yy)], 0.5, (128, 116, 104))
            c.line([(x + (2 if (yy // 9) % 2 else -2), yy), (x + (2 if (yy // 9) % 2 else -2), yy + 9)], 0.5, (128, 116, 104))
        c.rrect(x - 10, 30, x + 10, 36, 1.6, (226, 216, 200), (170, 158, 142))
        c.rrect(x - 3.4, 16, x + 3.4, 30, 1, (70, 60, 56), (40, 34, 34))                  # den
        c.ell(x, 23, 2.8, 4.4, (255, 240, 150), (255, 196, 70), dv=0.3, sang=(x - 0.8, 21.5, 1.0, 1.4, 0.9))
        c.poly([(x - 5.6, 16.6), (x + 5.6, 16.6), (x, 10)], (150, 60, 50), (104, 36, 34))
    c.poly([(2, 36), (66, 6), (130, 36), (126, 42), (66, 15), (6, 42)], (190, 96, 60), (136, 56, 40))    # mai
    for i in range(1, 8):
        t = i / 8.0
        c.line([(2 + 64 * t, 36 - 30 * t + 2.2), (4 + 62 * t, 42 - 27 * t - 0.5)], 0.5, (110, 44, 30))
        c.line([(130 - 64 * t, 36 - 30 * t + 2.2), (128 - 62 * t, 42 - 27 * t - 0.5)], 0.5, (110, 44, 30))
    c.rrect(26, 30, 106, 50, 4, (246, 214, 150), (214, 160, 90), vien=(110, 66, 30), dv=0.7)     # bang ten
    c.chu('NÔNG TRẠI', 66, 39.4, 13.5, (120, 60, 20), None)
    c.line([(34, 30), (34, 24)], 0.8, (110, 80, 60)); c.line([(98, 30), (98, 24)], 0.8, (110, 80, 60))
    rnd = random.Random(31)
    for x in (10, 122):                                                                  # day leo + hoa quanh tru
        for yy in range(42, 88, 6):
            c.la(x + rnd.uniform(-7, 7), yy, 4.0, rnd.uniform(0, 360), rnd.choice([XANH, (90, 190, 80)]))
        for j, yy in enumerate((46, 60, 74)):
            c.hoa(x + (4 if j % 2 else -4), yy, 3.0, [(255, 120, 170), (255, 220, 80), (255, 255, 255)][j])
    _bi(c, 24, 84, 6.0); _bi(c, 108, 85, 5.0)
    c.sao(66, 6.5, 5.6, (255, 226, 70), (250, 170, 30))
    return c.xong()


TIMM = (150, 80, 200); TIMM2 = (96, 44, 150)


def _mat_bi(c, x, y, r, m=(255, 236, 120), v=(120, 50, 10)):
    c.poly([(x - r * 0.55, y - r * 0.1), (x - r * 0.18, y - r * 0.1), (x - r * 0.36, y - r * 0.5)], m, vien=v, dv=0.25)
    c.poly([(x + r * 0.55, y - r * 0.1), (x + r * 0.18, y - r * 0.1), (x + r * 0.36, y - r * 0.5)], m, vien=v, dv=0.25)
    c.poly([(x - r * 0.6, y + r * 0.16), (x - r * 0.3, y + r * 0.3), (x - r * 0.1, y + r * 0.16), (x + r * 0.1, y + r * 0.3), (x + r * 0.3, y + r * 0.16), (x + r * 0.6, y + r * 0.16),
            (x + r * 0.34, y + r * 0.6), (x, y + r * 0.44), (x - r * 0.34, y + r * 0.6)], m, vien=v, dv=0.25)


def _mu_pt(c, x, y, r):
    """mu phu thuy: vanh + chop cong, dai cam"""
    c.ell(x, y, r * 1.5, r * 0.34, TIMM, TIMM2)
    c.poly([(x - r * 0.8, y - r * 0.05), (x - r * 0.3, y - r * 1.5), (x + r * 0.5, y - r * 2.1), (x + r * 1.0, y - r * 1.9), (x + r * 0.55, y - r * 1.6), (x + r * 0.8, y - r * 0.05)], TIMM, TIMM2)
    c.poly([(x - r * 0.72, y - r * 0.28), (x + r * 0.76, y - r * 0.28), (x + r * 0.8, y - r * 0.02), (x - r * 0.8, y - r * 0.02)], (255, 150, 40), (226, 100, 20), dv=0.3)
    c.rrect(x - r * 0.2, y - r * 0.34, x + r * 0.2, y + r * 0.02, 0.4, (255, 220, 80), (236, 170, 40), dv=0.3)


def bi_chong():
    c = Cv(26, 46)
    _bi(c, 13, 36, 8.6); _bi(c, 13, 24, 6.6)
    for dx, f in ((-0.5, 0.8), (0.5, 0.8), (0, 1.0)):                                     # bi trang tren cung
        c.ell(13 + dx * 4.6, 13.5, 2.9, 4.4 * f + 0.4, (255, 252, 240), (226, 214, 190), dv=0.35)
    c.rrect(12.4, 6.4, 13.8, 9.4, 0.4, (96, 140, 50), (60, 100, 40), dv=0.3)
    _mat_bi(c, 13, 13.4, 4.6, (250, 170, 50), (150, 70, 10))
    c.poly([(5, 40), (10, 37.6), (10, 42.4)], TIMM, TIMM2, dv=0.35); c.poly([(21, 40), (16, 37.6), (16, 42.4)], TIMM, TIMM2, dv=0.35)       # no tim
    c.ell(13, 40, 2.0, 2.0, (190, 120, 236), TIMM, dv=0.3)
    return c.xong()


def _nen(lech):
    c = Cv(22, 24)
    for x, y0, cao in ((5.5, 22, 9), (11, 22.6, 14), (16.6, 22, 7)):
        c.rrect(x - 2.2, y0 - cao, x + 2.2, y0, 1, (255, 248, 226), (232, 210, 170))
        c.ell(x - 1.2, y0 - cao + 2.2, 0.9, 1.6, (255, 255, 255), vien=None)                # sap chay
        fx = x + (lech if x != 11 else -lech) * 0.5
        c.poly([(fx, y0 - cao - 6.4), (fx + 1.9, y0 - cao - 2), (fx, y0 - cao - 0.4), (fx - 1.9, y0 - cao - 2)], (255, 226, 90), (255, 150, 40), vien=(200, 90, 20), dv=0.3)
        c.ell(fx, y0 - cao - 2, 0.8, 1.2, (255, 255, 230), vien=None)
    return c.xong()


def nen():
    return _nen(0.0)


def nen_2():
    return _nen(1.6)


def vac_thuoc():
    c = Cv(48, 54)
    c.line([(6, 52), (24, 5)], 2.4, GO, GO2, 0.4); c.line([(42, 52), (24, 5)], 2.4, GO, GO2, 0.4); c.line([(24, 46), (24, 5)], 2.0, GO2, toi(GO2, 0.6), 0.4)
    c.rrect(21, 3, 27, 8, 1, (214, 170, 90), (170, 120, 50))
    c.line([(24, 8), (24, 26)], 0.7, (60, 60, 70))
    for i, m in enumerate(((255, 120, 30), (255, 200, 60), (255, 90, 30))):                 # lua
        x = 18 + i * 6
        c.poly([(x, 43 - (4 if i == 1 else 0)), (x + 3.4, 50), (x, 53), (x - 3.4, 50)], m, toi(m, 0.8), vien=(170, 60, 10), dv=0.3)
    c.ell(24, 36, 12.5, 9.5, (64, 60, 78), (30, 28, 40), sang=(19, 32, 3, 2.4, 0.3))        # vac
    c.ell(24, 28.4, 11.5, 3.4, (84, 80, 100), (44, 42, 56))
    c.ell(24, 28.2, 9.6, 2.4, (140, 240, 90), (70, 190, 50), vien=(40, 110, 30), dv=0.3)    # thuoc xanh
    for x, y, r in ((20, 25, 1.6), (27, 23, 1.2), (24, 20, 0.9)):
        c.ell(x, y, r, r, (190, 255, 150), (110, 220, 80), vien=(40, 130, 30), dv=0.25)
    _bi(c, 40, 48, 3.8)
    return c.xong()


def bi_to():
    c = Cv(116, 96)
    for sx in (-1, 1):                                                                    # canh cay kho + den long
        x = 58 + sx * 44
        c.line([(x, 92), (x - sx * 2, 60), (x + sx * 5, 40), (x + sx * 2, 22)], 3.0, (96, 70, 60), (56, 38, 36), 0.45)
        c.line([(x - sx * 1, 56), (x - sx * 12, 44), (x - sx * 14, 34)], 1.8, (96, 70, 60), (56, 38, 36), 0.4)
        c.line([(x + sx * 4, 42), (x + sx * 11, 34)], 1.4, (96, 70, 60), (56, 38, 36), 0.4)
        for lx, ly in ((x - sx * 14, 40), (x + sx * 2, 28)):
            c.rrect(lx - 2.4, ly - 1, lx + 2.4, ly + 6.6, 0.8, (255, 214, 90), (250, 150, 40), vien=(70, 40, 30), dv=0.4)
    c.ell(58, 86, 44, 8.5, (136, 150, 130), (96, 108, 100))                                # be da
    for i in range(12):
        a = math.pi * i / 11.0
        c.ell(58 - 40 * math.cos(a), 88 + 3 * math.sin(a), 4.6, 2.6, (176, 180, 190), (120, 126, 140), dv=0.3)
    R = 24
    for dx, f in ((-0.62, 0.74), (0.62, 0.74), (-0.34, 0.9), (0.34, 0.9), (0, 1.0)):       # bi khong lo
        c.ell(58 + dx * R, 62, R * 0.5, R * 0.84 * f + 2, (255, 156, 40), (226, 98, 20), dv=0.5, sang=(58 + dx * R - 3, 50, 4, 5, 0.3))
    _mat_bi(c, 58, 62, 21)
    _mu_pt(c, 58, 44, 15)
    _bi(c, 24, 80, 7.5, True, True); _bi(c, 92, 81, 6.6, True, True)
    c.poly([(30, 62), (36, 56), (44, 60), (42, 70), (34, 72)], (250, 252, 255), (214, 224, 244))        # ma nho
    c.ell(35.6, 62.6, 0.9, 1.2, (40, 40, 60), vien=None); c.ell(39.2, 63, 0.9, 1.2, (40, 40, 60), vien=None)
    for x in (12, 104, 46, 72):
        c.hoa(x, 90, 2.6, [(255, 150, 60), (200, 120, 250)][x % 2])
    return c.xong()


def la_thu():
    c = Cv(30, 14)
    rnd = random.Random(41)
    for i, (x, y) in enumerate(((6, 8), (15, 5.4), (23, 9), (11, 10.6), (19.6, 10.4))):
        m = [(255, 140, 40), (236, 80, 40), (255, 200, 60), (250, 110, 30), (255, 170, 50)][i]
        c.sao(x, y, 4.4, m, toi(m, 0.78), n=5, trong=0.5, goc=rnd.uniform(0, 72), dv=0.3)
    return c.xong()


def mang_nhen():
    c = Cv(34, 34)
    for i in range(6):
        a = math.radians(i * 18)
        c.line([(1, 1), (1 + 32 * math.cos(a), 1 + 32 * math.sin(a))], 0.5, (255, 255, 255), (150, 160, 180), 0.2)
    for r in (9, 17, 25, 31):
        c.arc(1, 1, r, r, 0, 90, 0.45, (255, 255, 255), (150, 160, 180), n=12, dv=0.2)
    c.ell(15, 13, 1.6, 2.0, (50, 44, 60), vien=None)
    return c.xong()


def bu_nhin_bi():
    c = Cv(40, 58)
    c.rrect(6, 44, 34, 57, 2, (238, 204, 110), (204, 156, 66)); c.line([(7, 50.5), (33, 50.5)], 0.8, (150, 96, 40))
    c.rrect(18.6, 26, 21, 46, 0.8, GO, GO2); c.rrect(5, 28, 35, 31, 1, GO, GO2)
    c.poly([(11, 27), (29, 27), (31, 45), (9, 45)], (80, 110, 200), (50, 70, 150))
    c.poly([(5, 27.6), (11, 27), (11, 33), (5, 32)], (236, 80, 70), (190, 50, 50)); c.poly([(35, 27.6), (29, 27), (29, 33), (35, 32)], (236, 80, 70), (190, 50, 50))
    for x in (3.4, 36.6):
        for dy in (-1.6, 0, 1.6):
            c.line([(x - 1.2, 29.6 + dy * 1.4), (x + 1.2, 29.6 + dy)], 0.7, (236, 200, 100), (170, 120, 40), 0.25)
    _bi(c, 20, 19, 8.2, True, True)
    _mu_pt(c, 20, 12.5, 6.6)
    return c.xong()


def cay_den():
    """cay den lap lanh: non trang xanh ket bang nhieu bong tuyet / dom sang, sao tren dinh"""
    c = Cv(38, 66)
    rnd = random.Random(51)
    c.poly([(19, 6), (35, 62), (3, 62)], (222, 242, 255), (170, 214, 250), vien=None)
    c.im.putalpha(c.im.getchannel('A').point(lambda v: v * 110 // 255))
    for i in range(46):
        y = rnd.uniform(11, 61)
        hw = (y - 6) / 56.0 * 15.5
        x = 19 + rnd.uniform(-hw, hw)
        r = rnd.uniform(1.3, 2.6)
        if i % 3 == 0:
            c.sao(x, y, r * 1.2, (255, 255, 255), None, vien=(150, 200, 244), n=6, trong=0.4, dv=0.25)
        else:
            c.ell(x, y, r * 0.6, r * 0.6, (255, 255, 255), vien=(160, 206, 246), dv=0.25)
    c.sao(19, 6.4, 5.4, (236, 250, 255), (170, 220, 255), vien=(110, 170, 230), dv=0.4)
    return c.xong()


def gau_trang():
    c = Cv(40, 42)
    TR = (252, 253, 255); TR2 = (206, 214, 230)
    c.ell(11, 8, 4.0, 4.0, TR, TR2); c.ell(29, 8, 4.0, 4.0, TR, TR2)
    c.ell(20, 29, 13, 12, TR, TR2); c.ell(9, 37.6, 5.2, 3.8, TR, TR2); c.ell(31, 37.6, 5.2, 3.8, TR, TR2)
    c.ell(20, 14, 11, 10, TR, TR2, sang=(16, 10, 3, 2.4, 0.5))
    c.ell(20, 17.6, 4.6, 3.4, (255, 255, 255), (226, 232, 244), dv=0.3)
    c.ell(20, 16.2, 1.5, 1.1, (50, 50, 60), vien=None); c.arc(15.4, 12.6, 1.6, 1.2, 200, 340, 0.6, (50, 50, 60), n=8); c.arc(24.6, 12.6, 1.6, 1.2, 200, 340, 0.6, (50, 50, 60), n=8)
    c.rrect(10.5, 21.6, 29.5, 25.4, 1.6, (90, 160, 236), (50, 110, 200)); c.rrect(23, 24, 26.6, 33, 1.2, (90, 160, 236), (50, 110, 200))
    c.ell(10, 28, 3.4, 4.2, TR, TR2); c.ell(30, 28, 3.4, 4.2, TR, TR2)
    return c.xong()


def xe_tuyet():
    c = Cv(64, 44)
    DO = (226, 50, 56); DO2 = (170, 26, 40); VANG = (255, 214, 80); VANG2 = (226, 160, 40)
    c.arc(30, 36, 26, 5, 20, 160, 1.3, VANG, VANG2, n=16, dv=0.35); c.arc(6, 34, 4, 5, 90, 270, 1.3, VANG, VANG2, n=10, dv=0.35)      # can truot
    c.line([(16, 40), (16, 34)], 1.0, VANG, VANG2, 0.3); c.line([(44, 40), (44, 34)], 1.0, VANG, VANG2, 0.3)
    _hop(c, 24, 8, 12, 12, (80, 190, 110), (255, 220, 90)); _hop(c, 36, 4, 12, 15, (80, 150, 240), (255, 255, 255)); _hop(c, 14, 12, 11, 10, (255, 200, 60), (236, 70, 80))
    c.poly([(4, 20), (10, 16), (14, 22), (50, 22), (56, 10), (62, 12), (58, 34), (10, 34), (5, 28)], DO, DO2)                        # than xe
    c.poly([(8, 19), (12, 17.4), (15, 23), (50, 23), (55.4, 12.6), (59, 13.6), (57.6, 17), (53, 26), (12, 26)], (255, 255, 255), (226, 232, 244), vien=None)   # vien tuyet
    c.sao(22, 29.6, 2.2, VANG, None, vien=None); c.sao(34, 29.6, 2.2, VANG, None, vien=None); c.sao(46, 29.6, 2.2, VANG, None, vien=None)
    return c.xong()


def qua_cau():
    c = Cv(32, 40)
    c.poly([(5, 39), (27, 39), (24.4, 30), (7.6, 30)], (226, 50, 56), (170, 26, 40)); c.rrect(4, 36.4, 28, 39.6, 1, (255, 214, 80), (226, 160, 40), dv=0.3)
    c.ell(16, 17, 14, 14, (214, 238, 255), (150, 200, 244), vien=(90, 140, 200), dv=0.45)
    c.ell(16, 27, 10.5, 3.4, (255, 255, 255), (226, 236, 250), vien=None)
    for y, w in ((24, 6.4), (19, 5.0), (14.4, 3.6)):
        c.poly([(16 - w, y), (16, y - 7), (16 + w, y)], (60, 170, 90), (26, 110, 60), dv=0.3)
    c.sao(16, 7.4, 2.4, (255, 232, 90), (250, 176, 30), dv=0.25)
    for x, y in ((8, 12), (24, 10), (22, 21), (9, 21), (13, 5.4), (25.4, 16)):
        c.ell(x, y, 0.8, 0.8, (255, 255, 255), vien=None)
    c.ell(10.4, 10, 2.6, 4.2, (255, 255, 255), vien=None)
    return c.xong()


def bo_hoa():
    c = Cv(32, 38)
    c.poly([(9, 22), (23, 22), (19, 37), (13, 37)], (255, 190, 214), (240, 130, 170))
    c.poly([(11, 28), (21, 28), (20.4, 31), (11.6, 31)], (255, 255, 255), (236, 226, 240), dv=0.3)
    rnd = random.Random(61)
    for i in range(6):
        c.la(16 + rnd.uniform(-9, 9), 18 + rnd.uniform(-2, 3), 4.4, rnd.uniform(0, 360), XANH)
    for x, y in ((9, 13), (16, 9), (23, 13), (12.4, 19), (19.6, 19), (16, 15)):
        c.ell(x, y, 4.2, 4.0, (240, 50, 80), (180, 20, 56), sang=(x - 1.2, y - 1.4, 1.2, 1.0, 0.45))
        c.arc(x, y, 2.0, 1.8, 30, 300, 0.6, (150, 10, 44), n=10)
    return c.xong()


def bong_tim():
    c = Cv(40, 64)
    for x, y in ((13, 30), (27, 30), (20, 34)):
        c.line([(x, y), (20, 63)], 0.5, (150, 150, 160))
    for x, y, r, m in ((11, 18, 9.0, (255, 120, 160)), (29, 18, 9.0, (236, 50, 90)), (20, 12, 10.5, (255, 80, 120))):
        c.tim(x, y, r, m, toi(m, 0.76))
    return c.xong()


def banh_kem():
    c = Cv(46, 50)
    c.ell(23, 44, 21, 5, (236, 236, 244), (190, 194, 214))
    for y0, y1, w, m in ((30, 44, 18, (255, 190, 214)), (19, 31, 13, (255, 236, 170)), (10, 20, 8.5, (190, 226, 255))):
        c.rrect(23 - w, y0, 23 + w, y1, 2.4, m, toi(m, 0.82))
        for i in range(int(w * 2 / 4.2)):
            x = 23 - w + 2.4 + i * 4.2
            c.ell(x, y0 + 1.0, 2.3, 2.6, (255, 255, 255), (236, 240, 250), dv=0.3)
    for x, m in ((17, (255, 90, 90)), (23, (255, 200, 60)), (29, (90, 170, 255))):
        c.rrect(x - 0.8, 3.6, x + 0.8, 10, 0.3, m, toi(m, 0.8), dv=0.25)
        c.poly([(x, 0.2), (x + 1.3, 2.6), (x, 3.8), (x - 1.3, 2.6)], (255, 226, 90), (255, 150, 40), dv=0.25)
    for x, y, m in ((11, 37, (255, 90, 110)), (23, 38, (120, 200, 120)), (35, 37, (120, 160, 255)), (15, 25, (255, 150, 60)), (31, 25, (200, 120, 250))):
        c.ell(x, y, 1.8, 1.8, m, toi(m, 0.75), dv=0.25)
    return c.xong()


def _khe(bac):
    """cay khe cua nong trai, 5 bac (0 cay non .. 4 co thu ngan nam). Neo chan giua; tam tan cay cach chan ~40 (cho qua khe game ve)."""
    W = [70, 80, 90, 100, 108][bac]; H = [76, 84, 92, 100, 106][bac]
    c = Cv(W, H)
    rnd = random.Random(170 + bac)
    cx = W / 2.0
    ty = H - 2.5
    tr = [3.0, 3.8, 4.6, 5.4, 6.0][bac]                                                 # nua be rong than
    VO = [(160, 112, 66), (156, 108, 64), (146, 102, 64), (132, 108, 84), (126, 108, 92)][bac]
    VO2 = toi(VO, 0.62)
    rx = W / 2.0 - 6; ry = [23, 26, 29, 32, 34][bac]
    cy = ty - 18 - ry * 0.92
    c.ell(cx, ty + 0.4, tr * 4.2, 2.0, (70, 120, 60), None, vien=None)                    # vet co duoi goc
    # than cong nhe + 2 canh
    c.poly([(cx - tr * 2.4, ty), (cx - tr * 1.05, ty - 5), (cx - tr * 0.8, cy + ry * 0.5), (cx + tr * 0.8, cy + ry * 0.5), (cx + tr * 1.15, ty - 5), (cx + tr * 2.6, ty),
            (cx + tr * 0.9, ty - 1.4), (cx, ty - 0.2), (cx - tr * 0.9, ty - 1.4)], VO, VO2)
    c.line([(cx - tr * 0.4, cy + ry * 0.78), (cx - rx * 0.5, cy + ry * 0.2)], tr * 0.8, VO, VO2, 0.4)
    c.line([(cx + tr * 0.4, cy + ry * 0.74), (cx + rx * 0.52, cy + ry * 0.16)], tr * 0.8, VO, VO2, 0.4)
    c.line([(cx - tr * 0.3, ty - 4), (cx - tr * 0.3, cy + ry * 0.6)], tr * 0.5, tron(VO, (255, 255, 255), 0.22), None)      # vet sang doc than
    for i in range(2 + bac):
        yy = ty - 5 - i * 3.6
        c.arc(cx + rnd.uniform(-0.6, 0.6), yy, tr * 0.6, 1.2, 200, 340, 0.4, toi(VO, 0.58), n=6)
    if bac >= 3:                                                                         # reu tren than gia
        for i in range(5):
            c.ell(cx + rnd.uniform(-tr * 0.8, tr * 0.8), ty - rnd.uniform(3, 16), 1.6, 1.1, (120, 160, 74), (88, 128, 58), vien=None)
    DAM = [(48, 150, 62), (44, 146, 60), (40, 138, 60), (36, 128, 58), (40, 134, 62)][bac]
    VUA = [(104, 204, 76), (98, 198, 74), (90, 188, 72), (80, 176, 70), (88, 184, 76)][bac]
    SANG = [(184, 240, 104), (176, 236, 100), (164, 228, 96), (150, 216, 92), (170, 230, 104)][bac]
    # la nhon quanh ria (tan cay khe: la kep nho) - pha vo dang tron
    nr = 22 + bac * 4
    for i in range(nr):
        a = 2 * math.pi * i / nr + rnd.uniform(-0.08, 0.08)
        d = rnd.uniform(0.92, 1.06)
        c.la(cx + rx * d * math.cos(a), cy + ry * d * math.sin(a), rnd.uniform(4.6, 6.2), math.degrees(a) + rnd.uniform(-25, 25), DAM)
    # khoi tan: 3 lop cum la, sang dan ve phia tren - trai
    n1 = 10 + bac * 2
    for i in range(n1):
        a = 2 * math.pi * i / n1
        r = rnd.uniform(8.0, 10.5)
        c.ell(cx + (rx - r * 0.75) * math.cos(a), cy + (ry - r * 0.75) * math.sin(a), r, r * 0.94, DAM, toi(DAM, 0.78), dv=0.45)
    c.ell(cx, cy, rx - 7, ry - 6, VUA, DAM, vien=None)
    n2 = 9 + bac * 2
    for i in range(n2):
        a = 2 * math.pi * i / n2 + 0.3
        r = rnd.uniform(6.5, 9.0)
        x, y = cx - 2 + (rx - 13) * 0.9 * math.cos(a), cy - 3 + (ry - 11) * 0.9 * math.sin(a)
        c.ell(x, y, r, r * 0.9, VUA, toi(VUA, 0.8), vien=toi(DAM, 0.72), dv=0.3)
    for i in range(7 + bac * 2):
        a = rnd.uniform(math.pi * 0.85, math.pi * 1.95)
        d = rnd.uniform(0.1, 0.72)
        r = rnd.uniform(4.6, 6.8)
        x, y = cx - 3 + (rx - 13) * d * math.cos(a), cy - 4 + (ry - 11) * d * math.sin(a)
        c.ell(x, y, r, r * 0.86, SANG, VUA, vien=toi(VUA, 0.74), dv=0.25, sang=(x - r * 0.3, y - r * 0.35, r * 0.32, r * 0.24, 0.45))
    # la nho ro tung chiec o mat truoc
    for i in range(20 + bac * 6):
        a = rnd.uniform(0, 2 * math.pi); d = rnd.uniform(0.15, 0.95)
        x, y = cx + (rx - 4) * d * math.cos(a), cy + (ry - 4) * d * math.sin(a)
        sang = (x - cx) * 0.5 + (y - cy) < 0
        c.la(x, y, rnd.uniform(2.6, 3.6), rnd.uniform(0, 360), SANG if sang else VUA)
    for i in range(4 + bac):                                                             # chum hoa khe tim hong
        a = rnd.uniform(0, 2 * math.pi); d = rnd.uniform(0.35, 0.85)
        x, y = cx + (rx - 7) * d * math.cos(a), cy + (ry - 7) * d * math.sin(a)
        c.hoa(x, y, 1.9, (240, 150, 220), nhuy=(255, 240, 150)); c.hoa(x + 2.6, y + 1.4, 1.5, (250, 190, 230), nhuy=(255, 240, 150))
    if bac >= 4:                                                                         # co thu ngan nam: la vang kim
        for i in range(14):
            a = rnd.uniform(0, 2 * math.pi); d = rnd.uniform(0.3, 1.0)
            c.la(cx + (rx - 4) * d * math.cos(a), cy + (ry - 4) * d * math.sin(a), 3.4, rnd.uniform(0, 360), (252, 216, 84))
    return c.xong()


def khe_0():
    return _khe(0)


def khe_1():
    return _khe(1)


def khe_2():
    return _khe(2)


def khe_3():
    return _khe(3)


def khe_4():
    return _khe(4)


def lap_lanh():
    c = Cv(9, 9)
    c.sao(4.5, 4.5, 4.2, (255, 255, 255), None, vien=None, n=4, trong=0.22, goc=0)
    c.ell(4.5, 4.5, 1.1, 1.1, (255, 255, 255), vien=None)
    return c.xong()


def _mem(w, h, f):
    """anh mem (do trong suot lien tuc) w x h diem logic: f(u, v) voi u, v trong [-1, 1] -> (r, g, b, a 0..255) tung diem"""
    im = Image.new('RGBA', (w * S, h * S), (0, 0, 0, 0))
    px = im.load()
    for y in range(h * S):
        v = (y + 0.5) / (h * S) * 2 - 1
        for x in range(w * S):
            u = (x + 0.5) / (w * S) * 2 - 1
            r, g, b, a = f(u, v)
            if a > 0:
                px[x, y] = (int(r), int(g), int(b), max(0, min(255, int(a))))
    return im


def vong():
    """vong sang duoi chan nhan vat cua minh (elip det, vien sang mem + long nhat)"""
    def f(u, v):
        d = math.sqrt(u * u + v * v)
        a = 170 * math.exp(-((d - 0.80) / 0.085) ** 2)
        if d < 0.80:
            a += 34 * (d / 0.80) ** 2
        return 255, 240, 170, a
    return _mem(36, 15, f)


def _bui(ban, duc):
    def f(u, v):
        a = 0.0
        for (cx, cy, r) in ((-0.34, 0.10, 0.52), (0.30, 0.16, 0.48), (0.0, -0.22, 0.56)):
            d = math.hypot((u - cx * ban) / (r * ban), (v - cy * ban) / (r * ban))
            if d < 1:
                a = max(a, (1 - d * d) ** 1.4)
        return 226, 212, 186, a * duc
    return _mem(18, 12, f)


def bui_0():
    return _bui(0.55, 205)


def bui_1():
    return _bui(0.72, 165)


def bui_2():
    return _bui(0.88, 112)


def bui_3():
    return _bui(1.0, 58)


# ------------------------------------------------------------------ nut giao dien (ban HD dot 3): tu ve, kieu nut trang
# bo goc co bieu tuong + nut MENU hinh the mau xanh vien trang
def _bong_nut(c, x0, y0, x1, y1, r, lech=1.3, mo=0.9, duc=86):
    """bong mo duoi 1 nut bo goc"""
    m = c._mask()
    ImageDraw.Draw(m).rounded_rectangle([x0 * U, (y0 + lech) * U, x1 * U, (y1 + lech) * U], radius=r * U, fill=duc)
    m = m.filter(ImageFilter.GaussianBlur(mo * U))
    c.im.paste(Image.new('RGBA', c.im.size, (8, 26, 54, 255)), (0, 0), m)


def _tam_nut():
    """tam nut trang bo goc 24 x 24 (anh 26 x 28: chua cho cho bong)"""
    c = Cv(26, 28)
    _bong_nut(c, 1.6, 1.6, 24.4, 24.6, 6)
    c.rrect(1, 1, 25, 25, 6.2, (255, 255, 255), (222, 232, 245), vien=(104, 128, 160), dv=0.42)
    c.rrect(2.3, 2.3, 23.7, 12.5, 4.6, (255, 255, 255), None, vien=None)           # mang sang nua tren
    return c


def nut_thu():
    c = _tam_nut()
    c.rrect(5.6, 8.0, 20.4, 18.6, 1.5, (255, 248, 222), (250, 224, 150), vien=(170, 112, 40), dv=0.5)
    c.line([(6.2, 18.0), (11.4, 13.4)], 0.55, (214, 170, 96))
    c.line([(19.8, 18.0), (14.6, 13.4)], 0.55, (214, 170, 96))
    c.poly([(5.9, 8.4), (20.1, 8.4), (13.0, 14.6)], (255, 252, 236), (252, 236, 188), vien=(170, 112, 40), dv=0.42)
    return c.xong()


def nut_chat():
    c = _tam_nut()
    m = c._mask()
    d = ImageDraw.Draw(m)
    d.ellipse([(13 - 7.7) * U, (11.8 - 5.9) * U, (13 + 7.7) * U, (11.8 + 5.9) * U], fill=255)
    d.polygon([(8.4 * U, 15.6 * U), (6.8 * U, 20.4 * U), (12.6 * U, 17.0 * U)], fill=255)
    c._to(m, (104, 192, 252), (30, 124, 226), (20, 86, 172), 0.5, sang=(11.0, 8.9, 5.2, 1.9, 0.5))
    for i in range(3):
        c.ell(9.6 + i * 3.4, 12.0, 1.05, 1.05, (255, 255, 255), vien=None)
    return c.xong()


def nut_cam_xuc():
    c = _tam_nut()
    c.ell(13, 13, 7.8, 7.8, (255, 230, 96), (250, 182, 32), vien=(170, 106, 12), dv=0.5, sang=(10.6, 9.2, 3.6, 2.2, 0.6))
    c.ell(10.2, 11.6, 1.05, 1.55, (86, 50, 18), vien=None)
    c.ell(15.8, 11.6, 1.05, 1.55, (86, 50, 18), vien=None)
    c.arc(13, 13.4, 4.3, 3.7, 22, 158, 1.05, (86, 50, 18))
    c.ell(8.3, 14.4, 1.3, 0.9, (255, 150, 120), vien=None)
    c.ell(17.7, 14.4, 1.3, 0.9, (255, 150, 120), vien=None)
    return c.xong()


def nut_hanh_dong():
    c = _tam_nut()
    mau, v = (246, 104, 52), (150, 46, 16)
    # nguoi dang chay (hinh que net day): tay sau, chan sau, than, chan truoc, tay truoc, dau
    c.line([(13.4, 10.4), (10.0, 10.6), (8.2, 13.0)], 1.7, mau, vien=v, dv=0.42)
    c.line([(11.8, 15.0), (9.2, 17.4), (6.2, 17.8)], 2.0, mau, vien=v, dv=0.42)
    c.line([(14.3, 9.6), (11.8, 15.0)], 2.9, mau, vien=v, dv=0.42)
    c.line([(11.8, 15.0), (15.4, 16.6), (15.0, 20.8)], 2.0, mau, vien=v, dv=0.42)
    c.line([(14.0, 10.6), (17.4, 12.6), (19.6, 10.8)], 1.7, mau, vien=v, dv=0.42)
    c.ell(15.6, 6.6, 2.35, 2.35, (255, 132, 72), mau, vien=v, dv=0.42)
    return c.xong()


def nut_them():
    c = _tam_nut()
    mau = [((92, 180, 250), (36, 128, 226)), ((126, 212, 104), (58, 164, 60)), ((255, 196, 84), (242, 150, 26)), ((250, 136, 170), (228, 76, 122))]
    for i, (a, b2) in enumerate(mau):
        x = 7.0 + (i % 2) * 6.5
        y = 7.0 + (i // 2) * 6.5
        c.rrect(x, y, x + 5.5, y + 5.5, 1.5, a, b2, vien=toi(b2, 0.62), dv=0.38)
    return c.xong()


def nut_dong():
    """nut DONG tron: nen trang vien xam xanh, dau X do (goc tren phai cua bang shop / ruong - theo mau quan tri gui)"""
    c = Cv(26, 28)
    _bong_nut(c, 1.6, 1.6, 24.4, 24.6, 11.4)
    c.ell(13, 13, 11.8, 11.8, (255, 255, 255), (226, 234, 246), vien=(104, 128, 160), dv=0.5)
    c.ell(13, 9.6, 8.6, 5.4, (255, 255, 255), None, vien=None)
    do, v = (232, 64, 64), (150, 28, 34)
    c.line([(8.4, 8.4), (17.6, 17.6)], 3.3, do, vien=v, dv=0.45)
    c.line([(17.6, 8.4), (8.4, 17.6)], 3.3, do, vien=v, dv=0.45)
    c.line([(9.0, 8.6), (12.2, 11.8)], 0.9, (255, 150, 140))
    return c.xong()


def nut_menu():
    """nut MENU: the nhon dau ben trai, vien trang, than xanh chuyen mau, chu trang (anh 54 x 26, nut 50 x 22 tai (1, 1))"""
    c = Cv(54, 26)

    def hinh(x0, y0, x1, y1, r, mui):
        m = c._mask()
        d = ImageDraw.Draw(m)
        d.rounded_rectangle([(mui + 6.5) * U, y0 * U, x1 * U, y1 * U], radius=r * U, fill=255)
        d.polygon([(mui * U, (y0 + y1) / 2 * U), ((mui + 7.6) * U, y0 * U), ((mui + 9.5) * U, y0 * U), ((mui + 9.5) * U, y1 * U), ((mui + 7.6) * U, y1 * U)], fill=255)
        return m
    m = hinh(0, 1.4, 51, 23.4, 4, 2.2)
    m2 = m.filter(ImageFilter.GaussianBlur(0.9 * U)).point(lambda v: v * 86 // 255)
    c.im.paste(Image.new('RGBA', c.im.size, (8, 26, 54, 255)), (0, int(1.2 * U)), m2)
    c._to(hinh(0, 1, 51, 23, 4.2, 1.0), (255, 255, 255), (232, 240, 250), (86, 116, 156), 0.4)
    c._to(hinh(0, 2.7, 49.3, 21.3, 2.9, 3.6), (112, 196, 252), (26, 108, 208), (22, 84, 170), 0.36, sang=(30, 6.6, 20, 2.6, 0.34))
    c.chu('MENU', 30.2, 11.4, 11.6, (255, 255, 255), vien=(20, 74, 156), dv=0.5)
    return c.xong()


def _bao(chu):
    c = Cv(15, 15)
    c.ell(7.5, 7.5, 5.6, 5.6, (255, 104, 88), (220, 36, 32), vien=(255, 255, 255), dv=1.05, sang=(6.2, 5.0, 2.8, 1.5, 0.45))
    c.chu(chu, 7.5, 7.2, 9.6 if len(chu) == 1 else 8.0, (255, 255, 255), None)
    return c.xong()


def _bao_n(i):
    def f():
        return _bao(str(i))
    f.__name__ = 'bao_%d' % i
    return f


def bao_n():
    return _bao('!')


NUT = [nut_menu, nut_thu, nut_chat, nut_cam_xuc, nut_hanh_dong, nut_them, nut_dong, bao_n] + [_bao_n(i) for i in range(1, 10)]

VAT = NUT + [vong, bui_0, bui_1, bui_2, bui_3, cong_hoa, khung_anh, huong_duong, rom_bi, bu_nhin, co_day, cay_thong, nguoi_tuyet, ong_noel, hop_qua, cong_tim, chu_love, gau_tim,
       cong_bong, sao_vui, bi_ngo, cay_ma, bia_mo, canh_tt, dai_phun, khi_cau, xich_du, den_long, chau_hoa, cong_trai,
       bi_chong, nen, nen_2, vac_thuoc, bi_to, la_thu, mang_nhen, bu_nhin_bi, cay_den, gau_trang, xe_tuyet, qua_cau, bo_hoa, bong_tim, banh_kem, lap_lanh, khe_0, khe_1, khe_2, khe_3, khe_4]


def png_hd(im, he_so):
    bo = io.BytesIO(); im.save(bo, 'PNG', optimize=True)
    b = bo.getvalue()
    sau = 8 + 4 + 4 + 13 + 4
    ch = b'apHd' + bytes([he_so])
    return b[:sau] + struct.pack('>I', 1) + ch + struct.pack('>I', zlib.crc32(ch) & 0xFFFFFFFF) + b[sau:]


if __name__ == '__main__':
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    ims = []
    # anh trong suot thay cho cong "FARM" cu cua game (96 x 60 logic, crc32 anh goc 0976e328): cong moi la vat the p_cong_trai
    open(os.path.join(out, '0976e328.png'), 'wb').write(png_hd(Image.new('RGBA', (96 * S, 60 * S), (0, 0, 0, 0)), S))
    for f in VAT:
        im = f()
        if f.__name__ == 'cong_trai':
            im = im.resize((108 * S, 76 * S), Image.LANCZOS)        # vua khoang giua hang rao va may ATM
        open(os.path.join(out, 'p_%s.png' % f.__name__), 'wb').write(png_hd(im, S))
        ims.append((f.__name__, im))
        print('%-12s %dx%d' % (f.__name__, im.size[0] // S, im.size[1] // S))
    if '--xem' in sys.argv:
        W = 1500; x = y = 8; hang = 0
        sh = Image.new('RGBA', (W, 2600), (150, 196, 120, 255))
        for n, im in ims:
            if x + im.size[0] + 8 > W:
                x = 8; y += hang + 10; hang = 0
            sh.alpha_composite(im, (x, y)); x += im.size[0] + 10; hang = max(hang, im.size[1])
        sh.crop((0, 0, W, y + hang + 8)).convert('RGB').save(sys.argv[sys.argv.index('--xem') + 1])
