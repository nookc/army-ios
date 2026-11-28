#!/usr/bin/env python3
"""Sinh farmfx/HdViTri.java: vi tri vat trang tri ban HD theo ban do + chu de.  python3 mkvitri.py <tep HdViTri.java>"""
import sys
TEN = ["cong_hoa", "khung_anh", "huong_duong", "rom_bi", "bu_nhin", "co_day", "cay_thong", "nguoi_tuyet", "ong_noel", "hop_qua",
       "cong_tim", "chu_love", "gau_tim", "cong_bong", "sao_vui", "bi_ngo", "cay_ma", "bia_mo", "canh_tt", "dai_phun", "khi_cau",
       "xich_du", "den_long", "chau_hoa", "cong_trai", "bi_chong", "nen", "vac_thuoc", "bi_to", "la_thu", "mang_nhen", "bu_nhin_bi",
       "cay_den", "gau_trang", "xe_tuyet", "qua_cau", "bo_hoa", "bong_tim", "banh_kem"]
I = {n: i for i, n in enumerate(TEN)}
V = []


def dat(map_, cd, ten, *xy):
    for k in range(0, len(xy), 2):
        V.append((map_, cd, I[ten], xy[k], xy[k + 1]))


# ================= Cong vien (11): 1056 x 192; co y 22..72, san gach y 72..167
CV = 11
dat(CV, -1, 'dai_phun', 130, 132); dat(CV, -1, 'cong_hoa', 165, 73); dat(CV, -1, 'den_long', 334, 75)
dat(CV, -1, 'chau_hoa', 262, 86, 406, 86); dat(CV, -1, 'khung_anh', 720, 106); dat(CV, -1, 'canh_tt', 828, 71)
dat(CV, -1, 'xich_du', 930, 124); dat(CV, -1, 'khi_cau', 1010, 96)
# Giang sinh
dat(CV, 1, 'cay_thong', 410, 146); dat(CV, 1, 'ong_noel', 366, 153); dat(CV, 1, 'nguoi_tuyet', 452, 157, 60, 150, 968, 150)
dat(CV, 1, 'hop_qua', 410, 163, 596, 162); dat(CV, 1, 'cay_den', 228, 90, 300, 90, 372, 90, 560, 92, 660, 92, 780, 92, 1000, 160)
dat(CV, 1, 'gau_trang', 205, 160, 640, 160, 890, 162); dat(CV, 1, 'xe_tuyet', 770, 152); dat(CV, 1, 'qua_cau', 320, 166, 500, 166, 690, 100)
# Tinh nhan
dat(CV, 2, 'cong_tim', 410, 150); dat(CV, 2, 'chu_love', 300, 165); dat(CV, 2, 'gau_tim', 470, 161, 700, 160)
dat(CV, 2, 'bo_hoa', 228, 92, 372, 92, 560, 92, 660, 96, 990, 150); dat(CV, 2, 'bong_tim', 200, 164, 530, 164, 640, 164, 880, 164)
# Vui nhon
dat(CV, 3, 'cong_bong', 410, 150); dat(CV, 3, 'sao_vui', 350, 157, 470, 157, 820, 160); dat(CV, 3, 'banh_kem', 600, 160)
dat(CV, 3, 'co_day', 580, 78); dat(CV, 3, 'bong_tim', 220, 164, 660, 164)
# Halloween
dat(CV, 4, 'bi_to', 410, 150); dat(CV, 4, 'vac_thuoc', 590, 160); dat(CV, 4, 'bu_nhin_bi', 560, 71, 990, 150)
dat(CV, 4, 'bi_chong', 228, 92, 372, 92, 500, 164, 660, 96, 780, 164, 880, 96); dat(CV, 4, 'bia_mo', 880, 70); dat(CV, 4, 'cay_ma', 60, 150)
dat(CV, 4, 'nen', 250, 164, 330, 164, 470, 166, 640, 164, 700, 96, 830, 164, 960, 100); dat(CV, 4, 'bi_ngo', 300, 100, 760, 100)
dat(CV, 4, 'la_thu', 80, 100, 210, 120, 290, 140, 360, 110, 480, 96, 540, 150, 620, 120, 700, 150, 760, 130, 850, 110, 920, 150, 1000, 110,
    150, 50, 400, 56, 600, 52, 800, 58)
dat(CV, 4, 'mang_nhen', 444, 40, 870, 40)

# ================= Khu giai tri (9): 1008 x 192; san gach y 47..167
GT = 9
dat(GT, 1, 'cay_thong', 530, 128); dat(GT, 1, 'ong_noel', 486, 136); dat(GT, 1, 'nguoi_tuyet', 574, 138); dat(GT, 1, 'hop_qua', 530, 146)
dat(GT, 1, 'cay_den', 430, 96, 640, 96); dat(GT, 1, 'gau_trang', 620, 162)
dat(GT, 2, 'cong_tim', 530, 130); dat(GT, 2, 'bo_hoa', 474, 142, 586, 142); dat(GT, 2, 'bong_tim', 436, 152, 626, 152)
dat(GT, 3, 'cong_bong', 530, 130); dat(GT, 3, 'sao_vui', 470, 142, 590, 142); dat(GT, 3, 'banh_kem', 530, 152)
dat(GT, 4, 'bi_to', 530, 130); dat(GT, 4, 'bi_chong', 462, 150, 598, 150); dat(GT, 4, 'nen', 500, 162, 562, 164, 640, 150)
dat(GT, 4, 'la_thu', 420, 120, 480, 160, 600, 110, 660, 160, 380, 150, 700, 130)

# ================= Nong trai chung (25): 648 x 168; cong FARM cu o (207, 20), duong dat y 68..148
# cong_trai = -2: anh cong FARM goc da bi thay bang anh trong suot (hdx/0976e328.png) nen cong moi PHAI luon duoc ve,
# ke ca khi chu de = 0 (truoc day -1 -> quan tri tat chu de la mat cong)
NT = 25
dat(NT, -2, 'cong_trai', 207, 21); dat(NT, -1, 'huong_duong', 148, 60, 266, 62); dat(NT, -1, 'rom_bi', 400, 64); dat(NT, -1, 'bu_nhin', 575, 72)
dat(NT, 1, 'nguoi_tuyet', 306, 66); dat(NT, 1, 'cay_den', 120, 64, 345, 64); dat(NT, 1, 'gau_trang', 452, 68); dat(NT, 1, 'hop_qua', 500, 68)
dat(NT, 2, 'bo_hoa', 306, 66, 120, 66); dat(NT, 2, 'bong_tim', 452, 70)
dat(NT, 3, 'sao_vui', 306, 68); dat(NT, 3, 'banh_kem', 452, 68)
dat(NT, 4, 'bi_chong', 306, 66, 120, 66); dat(NT, 4, 'nen', 240, 70, 176, 70); dat(NT, 4, 'bu_nhin_bi', 452, 68)
dat(NT, 4, 'la_thu', 100, 100, 220, 120, 340, 96, 460, 126, 560, 100, 300, 140)

out = ['/**', ' * Vi tri vat trang tri ban HD - TEP SINH TU DONG boi Scripts/avatar/hd/mkvitri.py (sua o do roi chay lai).',
       ' * Moi vat 5 so: ma ban do, chu de (-2 LUON co ke ca khi quan tri tat chu de - vat thay han anh goc nhu cong nong trai;', ' * -1 vat chung khi bat chu de bat ky, 1 Giang sinh, 2 Tinh nhan, 3 Vui nhon, 4 Halloween),',
       ' * so anh (HdTrangTri.TEN), x, y (diem chan giua, don vi ban do: 1 o = 24).', ' */', 'final class HdViTri {', '    private HdViTri() {', '    }', '',
       '    static final String[] TEN = {' + ', '.join('"%s"' % n for n in TEN) + '};', '', '    static final int[] VT = {']
for i in range(0, len(V), 6):
    out.append('        ' + ' '.join('%d, %d, %d, %d, %d,' % v for v in V[i:i + 6]))
out += ['    };', '}', '']
open(sys.argv[1], 'w').write('\n'.join(out))
print(len(V), 'vat')
