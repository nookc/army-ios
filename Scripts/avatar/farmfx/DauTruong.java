import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Hashtable;
import java.util.Random;
import java.util.Vector;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.Sprite;
import javax.microedition.rms.RecordStore;

/**
 * Dau Truong Boss (map 28) tren app: san 3D (anh dung san tu Blender, 3 lop: troi-bien xa cuon cham, san + tuong, lan can
 * truoc), boss = nhan vat Avatar mac do boss phong to gap doi, nguoi choi + nguoi ao cam sung 3D (4 loai x 10 cap),
 * dan / tia / cau lua / bong bong + no + song xung kich + so sat thuong, thanh boss + bang sung kieu menu cua game.
 * Moi phat ban gui may chu (lenh 119), may chu tru xu / luong that, tinh sat thuong, bao ca khu (ca nguoi ao ban).
 * PatchFarmFx: ae.k() -> capNhat(); ae.b(): sau ir.b() -> veNen(), sau ir.d() -> veTren(), truoc return -> veHud();
 * hn.a(Graphics): dau ham -> veThay() (boss), truoc gx.a() -> sauNguoi() (sung tren tay); ce.a(jr) -> tin();
 * KeyTap.g() -> cham(). Chi dung so nguyen (CLDC 1.0).
 */
public final class DauTruong {

    static final int MAP = 28;
    static final byte CMD = 119;
    // mau menu cua game
    static final int NEN = 0x84D6CC, VIEN = 0x29736B, VIEN2 = 0x5EA09B, SANG = 0xECFBE9, CHU = 0x06515E;
    static final int[] MAU = {0x5FD8FF, 0xFF4FD8, 0xFFE45C, 0xFF7A24};
    static final int[] MAU2 = {0xE6FBFF, 0xFFE0F7, 0xFFFFFF, 0xFFF1A8};
    // o mau trong anh sang.png: 0 lam, 1 hong, 2 vang, 3 cam, 4 trang, 5 tim, 6 do, 7 xanh la
    static final int[] SANG_SUNG = {0, 1, 2, 3};
    static final int[] CO = {8, 12, 16, 24, 32, 48};
    static final int[][] DUOC_SAU = {{36, -56}, {168, -56}, {300, -56}, {432, -56}, {564, -56}, {696, -56}, {828, -56}, {960, -56}};
    static final int[][] DUOC_TRUOC = {{12, 189}, {141, 189}, {270, 189}, {399, 189}, {528, 189}, {657, 189}, {786, 189}, {915, 189}, {1044, 189}};

    // ---------------------------------------------------------------- trang thai tu may chu
    static boolean coTT;
    static String[] tenSung = {"Bong bóng", "Laser plasma", "Sấm sét", "Rồng lửa"};
    /** ten boss chu hoa cho font lon (font lon chi co chu hoa) */
    static final String[] TEN_HOA = {"XÁC ƯỚP", "BỘ XƯƠNG KHÔ", "FRANKENSTEIN", "MA BƯ", "TRÙM XÁC ƯỚP", "XÊN BỌ HUNG"};
    static int[] cdS = {340, 150, 600, 620}, capS = {1, 1, 1, 1}, giaXu = new int[4], giaL = new int[4], dameS = new int[4];
    static int[] nangL = new int[4], nangD = new int[4], nangTL = new int[4];
    static String[] tenCap = {"Đồng", "Bạc", "Vàng", "Ngọc bích", "Lam ngọc", "Tử tinh", "Hồng ngọc", "Băng tinh", "Lôi thần", "Hỏa thần"};
    static int tamBan = 300;
    static int chieuCd = 12000, chieuLan = 8;
    static String[] tenChieu = {"Mưa bong bóng", "Pháo laser hủy diệt", "Lôi thần giáng", "Rồng lửa thiêu đốt"};
    static long chieuLuc = -100000;
    // hieu ung chieu (toi da 8 cung luc)
    static final int NC = 8;
    static boolean[] cOn = new boolean[NC], cCrit = new boolean[NC], cXong = new boolean[NC];
    static int[] cG = new int[NC], cLv = new int[NC], cAi = new int[NC], cBoss = new int[NC], cDmg = new int[NC], cBuoc = new int[NC], cX0 = new int[NC], cY0 = new int[NC];
    static long[] cT = new long[NC];
    // gop so sat thuong cua toi (laser ban nhanh -> 1 so moi 0,22 giay)
    static int tongToi, tongX, tongY;
    static long tongLuc;
    static Hashtable hinh = new Hashtable();
    static long xu, luong;
    static int ngoc, dns;
    static int nB;
    static int[] bId = new int[6], bLoai = new int[6], bHp = new int[6], bMax = new int[6], bHien = new int[6];
    static String[] bTen = new String[6];
    static long[] bChop = new long[6], bChet = new long[6], bRung = new long[6];

    // ---------------------------------------------------------------- tuy chon cua nguoi choi
    static int sung = 1, tien = 0;
    static boolean tuDanh = true;
    static int muc = -1;
    static boolean trong;
    static long vaoLuc, banLuc, diLuc, hoiLuc;
    static boolean vaoSan;
    static boolean bangNang;        // dang mo bang nang cap
    static Hashtable sungNguoi = new Hashtable();      // id -> g*16+lv (nguoi khac, theo phat ban gan nhat)
    static Random R = new Random();

    // ---------------------------------------------------------------- anh
    static Image imXa, imGan, imTruoc, imSung, imTay, imThan, imSang, imSo, imLua, imVong, imBong;
    static Image[] imLon = new Image[4];
    static int lonG = -1, lonLv = -1;
    static boolean napXong, napLoi;
    static int tCW = 26, tCH = 16, tAX = 8, tAY = 10, tMX = 25, tMY = 7;     // o sung tren tay (tu sungt.bin)
    static Image bBuf;
    static Graphics bBufG;
    static int[] bPx, bOut;
    static final int BW = 60, BH = 76;
    static Image[] bAnh = new Image[6], bVien = new Image[6];
    static int[] bVienPx;
    static long[] bAnhLuc = new long[6];

    // ---------------------------------------------------------------- hieu ung (mang song song, so nguyen; toa do the gioi px)
    static final int ND = 40;
    static int[] dX0 = new int[ND], dY0 = new int[ND], dX1 = new int[ND], dY1 = new int[ND], dG = new int[ND], dLv = new int[ND], dDur = new int[ND],
            dSo = new int[ND], dTrung = new int[ND];
    static long[] dT = new long[ND];
    static boolean[] dOn = new boolean[ND], dCrit = new boolean[ND], dCuaToi = new boolean[ND];
    static int[] dBoss = new int[ND];
    static final int NH = 140;     // hat: x, y (x16), vx, vy (x16 / khung 50ms), song (ms), mau (o sang), co
    static int[] hX = new int[NH], hY = new int[NH], hVX = new int[NH], hVY = new int[NH], hSong = new int[NH], hMau = new int[NH], hCo = new int[NH], hZ = new int[NH], hVZ = new int[NH];
    static long[] hT = new long[NH];
    static boolean[] hOn = new boolean[NH];
    static final int NV = 16;      // song xung kich tren san
    static int[] vX = new int[NV], vY = new int[NV], vR = new int[NV], vMau = new int[NV], vDur = new int[NV];
    static long[] vT = new long[NV];
    static boolean[] vOn = new boolean[NV];
    static final int NS = 24;      // so sat thuong
    static int[] sX = new int[NS], sY = new int[NS], sV = new int[NS];
    static long[] sT = new long[NS];
    static boolean[] sOn = new boolean[NS], sCrit = new boolean[NS], sToi = new boolean[NS];
    static final int NM = 10;      // loe dau nong
    static int[] mX = new int[NM], mY = new int[NM], mG = new int[NM], mLv = new int[NM];
    static long[] mT = new long[NM];
    static boolean[] mOn = new boolean[NM];
    static final int NX = 24;      // vu no 3D (anh no40 / no64: 7 khung x 4 mau)
    static int[] xX = new int[NX], xY = new int[NX], xM = new int[NX], xC = new int[NX];
    static long[] xT = new long[NX];
    static boolean[] xOn = new boolean[NX];
    static String bang, bang2;        // bang chu lon giua man
    static String bangAnh;            // anh chu cua bang (h_b_*.png) neu co
    static int bangMau;
    static long bangLuc;
    static Vector tinNho = new Vector();       // dong thong bao nho (thuong / loi)
    static Vector tinLuc = new Vector();
    static long rungDen;
    static int rungBien;
    static int giatG;                 // giat sung cua toi
    static long giatLuc;
    static int dx0, dy0;              // camera rung (bu lai)

    private DauTruong() {
    }

    static long now() {
        return System.currentTimeMillis();
    }

    static int rnd(int a, int b) {
        return b <= a ? a : a + (R.nextInt() & 0x7fffffff) % (b - a);
    }

    static boolean bat() {
        return ir.a == MAP && main.GameMidlet.i != null;
    }

    static boolean sanMoi() {
        return ir.e == 44 && js.ay == 1;
    }

    // ================================================================ anh
    /** anh HUD /dt/<ten>.png (giu lai sau lan nap dau) */
    static Image h(String ten) {
        Object o = hinh.get(ten);
        if (o instanceof Image) {
            return (Image) o;
        }
        if (o != null) {
            return null;
        }
        Image im = anh("/dt/" + ten + ".png");
        hinh.put(ten, im != null ? (Object) im : (Object) new Object());
        return im;
    }

    static void ve(Graphics g, String ten, int x, int y, int neo) {
        Image im = h(ten);
        if (im != null) {
            g.drawImage(im, x, y, neo);
        }
    }

    static Image anh(String p) {
        try {
            return Image.createImage(p);
        } catch (Throwable t) {
            return null;
        }
    }

    static void nap() {
        if (napXong) {
            return;
        }
        napXong = true;
        try {
            imSang = anh("/dt/sang.png");
            imSo = anh("/dt/so.png");
            imSung = anh("/dt/sung.png");
            imTay = anh("/dt/sungt.png");
            imThan = anh("/dt/sungb.png");
            try {
                DataInputStream d = new DataInputStream(Class.forName("DauTruong").getResourceAsStream("/dt/sungt.bin"));   // CLDC 1.0: khong dung X.class
                tCW = d.readShort();
                tCH = d.readShort();
                tAX = d.readShort();
                tAY = d.readShort();
                tMX = d.readShort();
                tMY = d.readShort();
                d.close();
            } catch (Throwable t) {
            }
            imLua = anh("/dt/lua.png");
            imVong = anh("/dt/vong.png");
            imBong = anh("/dt/bong.png");
            if (sanMoi()) {
                imXa = anh("/dt/xa.png");
                imGan = anh("/dt/gan.png");
                imTruoc = anh("/dt/truoc.png");
            }
        } catch (Throwable t) {
            napLoi = true;
        }
        docTuyChon();
    }

    /** anh sung lon (bang nang cap), 4 khung vay canh */
    static Image lon(int g, int lv, int f) {
        if (g != lonG || lv != lonLv) {
            for (int i = 0; i < 4; i++) {
                imLon[i] = null;
            }
            for (int i = 0; i < 4; i++) {
                imLon[i] = anh("/dt/g" + g + "_" + (lv - 1) + "_" + i + ".png");
            }
            lonG = g;
            lonLv = lv;
        }
        return imLon[f & 3] != null ? imLon[f & 3] : imLon[0];
    }

    /** khung vay canh hien tai (4 khung: ha - giua - nang - giua) */
    static int khungVay(int lech) {
        return (int) ((now() / 120 + lech) & 3);
    }

    /** o sang: mau m (0..7), co c (0..5) -> ve tam (x, y) */
    static void sang(Graphics g, int m, int c, int x, int y) {
        if (imSang == null) {
            return;
        }
        if (c < 0) {
            c = 0;
        } else if (c > 5) {
            c = 5;
        }
        int s = CO[c];
        int ox = 0;
        for (int i = 0; i < c; i++) {
            ox += CO[i];
        }
        g.drawRegion(imSang, ox, m * 48, s, s, 0, x - s / 2, y - s / 2, 20);
    }

    /** co sang gan nhat voi duong kinh d px */
    static int coSang(int d) {
        for (int i = 0; i < CO.length; i++) {
            if (CO[i] >= d) {
                return i;
            }
        }
        return CO.length - 1;
    }

    // ================================================================ tuy chon luu RMS
    static void docTuyChon() {
        try {
            RecordStore rs = RecordStore.openRecordStore("avatarDauTruong", true);
            if (rs.getNumRecords() > 0) {
                byte[] b = rs.getRecord(1);
                if (b != null && b.length >= 3) {
                    sung = Math.max(0, Math.min(3, b[0]));
                    tien = b[1] == 1 ? 1 : 0;
                    tuDanh = b[2] != 0;
                }
            }
            rs.closeRecordStore();
        } catch (Throwable t) {
        }
    }

    static void ghiTuyChon() {
        try {
            byte[] b = {(byte) sung, (byte) tien, (byte) (tuDanh ? 1 : 0)};
            RecordStore rs = RecordStore.openRecordStore("avatarDauTruong", true);
            if (rs.getNumRecords() > 0) {
                rs.setRecord(1, b, 0, b.length);
            } else {
                rs.addRecord(b, 0, b.length);
            }
            rs.closeRecordStore();
        } catch (Throwable t) {
        }
    }

    // ================================================================ gui may chu
    static void gui(int sub, int a, int b, int c) {
        try {
            jr m = new jr(CMD);
            DataOutputStream o = m.c();
            o.writeByte(sub);
            if (sub == 1) {
                o.writeInt(a);
                o.writeByte(b);
                o.writeByte(c);
            } else if (sub == 2) {
                o.writeByte(a);
            }
            kt.a().a(m);
        } catch (Throwable t) {
        }
    }

    // ================================================================ tin tu may chu (ce.a dau ham)
    public static boolean tin(jr m) {
        if (m == null || m.a != CMD) {
            return false;
        }
        try {
            DataInputStream d = m.b();
            int sub = d.readByte();
            if (sub == 0) {
                int n = d.readByte();
                for (int i = 0; i < n && i < 4; i++) {
                    tenSung[i] = d.readUTF();
                    cdS[i] = d.readShort();
                    capS[i] = d.readByte();
                    giaXu[i] = d.readInt();
                    giaL[i] = d.readInt();
                    dameS[i] = d.readInt();
                    nangL[i] = d.readInt();
                    nangD[i] = d.readInt();
                    nangTL[i] = d.readByte();
                }
                int nc = d.readByte();
                for (int i = 0; i < nc; i++) {
                    String s = d.readUTF();
                    if (i < tenCap.length) {
                        tenCap[i] = s;
                    }
                }
                tamBan = d.readShort();
                d.readShort();
                chieuCd = d.readInt();
                chieuLan = d.readShort();
                for (int i = 0; i < 4; i++) {
                    tenChieu[i] = d.readUTF();
                }
                xu = d.readLong();
                luong = d.readLong();
                ngoc = d.readInt();
                dns = d.readInt();
                int nb = d.readByte();
                nB = Math.min(6, nb);
                for (int i = 0; i < nb; i++) {
                    int id = d.readInt(), lo = d.readByte(), hp = d.readInt(), mx = d.readInt();
                    String ten = d.readUTF();
                    if (i < 6) {
                        bId[i] = id;
                        bLoai[i] = lo;
                        bHp[i] = hp;
                        bHien[i] = hp;
                        bMax[i] = Math.max(1, mx);
                        bTen[i] = ten;
                        bChet[i] = hp <= 0 ? now() : 0;
                        bAnh[i] = null;
                    }
                }
                coTT = true;
            } else if (sub == 1) {
                int ai = d.readInt(), boss = d.readInt(), g = d.readByte(), lv = d.readByte(), dmg = d.readInt();
                boolean crit = d.readByte() != 0;
                int hp = d.readInt(), mx = d.readInt();
                if ((lv & 0x40) != 0) {                 // trung do chieu: hieu ung chieu tu ve so
                    int k = viTri(boss);
                    if (k >= 0) {
                        bHp[k] = hp;
                        bMax[k] = Math.max(1, mx);
                    }
                } else {
                    trungTin(ai, boss, g, lv & 0x3F, dmg, crit, hp, mx);
                }
            } else if (sub == 2) {
                int boss = d.readInt(), ai = d.readInt();
                String ten = d.readUTF();
                bossChet(boss, ai, ten);
            } else if (sub == 3) {
                int boss = d.readInt(), lo = d.readByte(), hp = d.readInt(), mx = d.readInt();
                String ten = d.readUTF();
                int k = viTri(boss);
                if (k < 0 && nB < 6) {
                    k = nB++;
                }
                if (k >= 0) {
                    bId[k] = boss;
                    bLoai[k] = lo;
                    bHp[k] = hp;
                    bHien[k] = hp;
                    bMax[k] = Math.max(1, mx);
                    bTen[k] = ten;
                    bChet[k] = 0;
                    bAnh[k] = null;
                    hn h = ir.g(boss);
                    if (h != null) {
                        hienBoss(h.aw * js.ay, h.ax * js.ay);
                    }
                    banner("BOSS XUẤT HIỆN", ten, 0xFFD23A);
                }
            } else if (sub == 4) {
                long x = d.readLong(), l = d.readLong();
                int ng = d.readInt(), dn = d.readInt();
                String txt = d.readUTF();
                tinNho(txt, 0xFFE45C);
                if (x > 0 || l > 0) {
                    hn me = main.GameMidlet.i;
                    for (int i = 0; i < 10; i++) {
                        hat(me.aw * js.ay, me.ax * js.ay - 20, rnd(-40, 41), rnd(-70, -30), 900, 2, rnd(1, 3), 0);
                    }
                }
            } else if (sub == 5) {
                int loai = d.readByte();
                String txt = d.readUTF();
                tinNho(txt, loai == 1 ? 0xFF6B6B : 0xFFFFFF);
                if (loai == 1) {
                    tuDanh = false;
                    ghiTuyChon();
                }
            } else if (sub == 6) {
                xu = d.readLong();
                luong = d.readLong();
                ngoc = d.readInt();
                dns = d.readInt();
            } else if (sub == 8) {
                int ai = d.readInt(), boss = d.readInt(), g = d.readByte(), lv = d.readByte(), dmg = d.readInt();
                boolean crit = d.readByte() != 0;
                batChieu(ai, boss, g, lv, dmg, crit);
            } else if (sub == 7) {
                int g = d.readByte(), lv = d.readByte(), tl = d.readByte(), kq = d.readByte();
                String txt = d.readUTF();
                if (g >= 0 && g < 4) {
                    capS[g] = lv;
                }
                hn me = main.GameMidlet.i;
                if (kq == 1) {
                    banner("NÂNG CẤP THÀNH CÔNG", tenSung[g] + " Lv" + lv + " - " + tenCap[Math.max(0, Math.min(9, lv - 1))], 0xFFD23A);
                    for (int i = 0; i < 40; i++) {
                        hat(me.aw * js.ay, me.ax * js.ay - 18, rnd(-90, 91), rnd(-120, -20), 1200, i % 2 == 0 ? 2 : SANG_SUNG[g], rnd(1, 4), 0);
                    }
                    song(me.aw * js.ay, me.ax * js.ay, 70, 2, 600);
                    rung(5, 300);
                } else if (kq == 0) {
                    banner("THẤT BẠI", "Tỉ lệ " + tl + "% - giữ Lv" + lv, 0xFF6B6B);
                    for (int i = 0; i < 14; i++) {
                        hat(me.aw * js.ay, me.ax * js.ay - 18, rnd(-30, 31), rnd(-50, -10), 900, 4, rnd(1, 3), 0);
                    }
                } else {
                    tinNho(txt, 0xFFFFFF);
                }
            }
        } catch (Throwable t) {
        }
        return true;
    }

    static int viTri(int id) {
        for (int i = 0; i < nB; i++) {
            if (bId[i] == id) {
                return i;
            }
        }
        return -1;
    }

    // ================================================================ logic moi khung (ae.k)
    public static void capNhat() {
        if (!bat()) {
            if (trong) {
                trong = false;
                xoaHieuUng();
            }
            return;
        }
        long t = now();
        if (!trong) {
            trong = true;
            vaoLuc = t;
            nap();
            xoaHieuUng();
            coTT = false;
            hoiLuc = 0;
            vaoSan = sanMoi();
        }
        if (vaoSan) {
            // dau truong moi khong co cua: app tu dat o (0,0) -> dua vao dau san (may chu cung dat o day) roi buoc 1 doan cho dong bo
            hn me0 = main.GameMidlet.i;
            if (me0.aw < 40 || me0.ax < 30) {
                me0.a(96 + rnd(0, 40), 112 + rnd(0, 30));
                ir.u.a = (me0.aw + 24) * js.ay;
                ir.u.b = me0.ax * js.ay;
                me0.av = ir.u;
                me0.l();
            }
            vaoSan = false;
        }
        if (!coTT && t - hoiLuc > 2500) {
            hoiLuc = t;
            gui(0, 0, 0, 0);
        }
        // camera rung: bu lai do lech khung truoc roi lech moi (lop "do" la tu khoa Java -> qua lop Cam sinh bang ASM)
        Cam.dich(-dx0, -dy0);
        dx0 = dy0 = 0;
        if (t < rungDen) {
            dx0 = rnd(-rungBien, rungBien + 1);
            dy0 = rnd(-rungBien, rungBien + 1);
            Cam.dich(dx0, dy0);
        }
        for (int i = 0; i < nB; i++) {
            if (bHien[i] > bHp[i]) {
                bHien[i] -= Math.max(1, (bHien[i] - bHp[i]) / 6);
            } else {
                bHien[i] = bHp[i];
            }
        }
        if (!coTT || bangNang || dangMoMenu()) {
            return;
        }
        // tu ban
        hn me = main.GameMidlet.i;
        if (muc < 0 || muc >= nB || bHp[muc] <= 0 || bChet[muc] != 0 || ir.g(bId[muc]) == null) {
            muc = ganNhat(me);
        }
        if (tuDanh && muc >= 0) {
            hn b = ir.g(bId[muc]);
            if (b != null) {
                int dx = b.aw - me.aw, dy = b.ax - me.ax;
                int kc = can(dx * dx + dy * dy);
                if (kc > tamBan * 55 / 100) {
                    if (t - diLuc > 900) {
                        diLuc = t;
                        int lui = tamBan * 33 / 100;           // dung cach boss ~100px: ca 2 cung tren man
                        int tx = b.aw - dx * lui / Math.max(1, kc), ty = b.ax - dy * lui / Math.max(1, kc);
                        ty = Math.max(30, Math.min(205, ty));
                        tx = Math.max(30, Math.min(ir.e * 24 - 30, tx));
                        ir.u.a = tx * js.ay;
                        ir.u.b = ty * js.ay;
                        me.av = ir.u;
                        me.l();
                    }
                } else {
                    if (kc < 46 && t - diLuc > 900) {           // dung sat boss qua: lui ra cho dep
                        diLuc = t;
                        int lx = me.aw - (dx >= 0 ? 60 : -60);
                        ir.u.a = Math.max(30, Math.min(ir.e * 24 - 30, lx)) * js.ay;
                        ir.u.b = me.ax * js.ay;
                        me.av = ir.u;
                        me.l();
                    }
                    if (t - chieuLuc >= chieuCd && t - banLuc > 200) {
                        dungChieu(muc);
                    } else if (t - banLuc >= cdS[sung]) {
                        ban(muc);
                    }
                }
            }
        }
    }

    static boolean dangMoMenu() {
        return main.a.v != null || main.a.A != null || main.a.t != null;
    }

    static int ganNhat(hn me) {
        int best = -1, bd = 0x7fffffff;
        for (int i = 0; i < nB; i++) {
            if (bHp[i] <= 0 || bChet[i] != 0) {
                continue;
            }
            hn b = ir.g(bId[i]);
            if (b == null) {
                continue;
            }
            int dx = b.aw - me.aw, dy = b.ax - me.ax;
            int d = dx * dx + dy * dy;
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    static int can(int v) {
        if (v <= 0) {
            return 0;
        }
        int r = v, x = 0;
        int b = 1 << 30;
        while (b > v) {
            b >>= 2;
        }
        while (b != 0) {
            if (r >= x + b) {
                r -= x + b;
                x = (x >> 1) + b;
            } else {
                x >>= 1;
            }
            b >>= 2;
        }
        return x;
    }

    /** ban 1 phat vao boss k (gui may chu + dan bay ngay cho muot) */
    static void ban(int k) {
        long t = now();
        hn me = main.GameMidlet.i;
        hn b = ir.g(bId[k]);
        if (b == null) {
            return;
        }
        if (tien == 0 ? xu < giaXu[sung] : luong <= 0 && giaL[sung] > 0) {
            tinNho(tien == 0 ? "Không đủ xu để bắn!" : "Không đủ lượng để bắn!", 0xFF6B6B);
            tuDanh = false;
            ghiTuyChon();
            return;
        }
        banLuc = t;
        gui(1, bId[k], sung, tien);
        if (tien == 0) {
            xu -= giaXu[sung];
        }
        me.K = (byte) (b.aw >= me.aw ? gx.I : gx.J);
        phongDan(me, b, sung, capS[sung], true);
        giatG = sung;
        giatLuc = t;
    }

    /** tung chieu vao boss k (may chu tru tien + bao ca khu, hieu ung chay khi nhan S_CHIEU) */
    static void dungChieu(int k) {
        long t = now();
        if (t - chieuLuc < chieuCd) {
            return;
        }
        chieuLuc = t;
        hn me = main.GameMidlet.i;
        hn b = ir.g(bId[k]);
        if (b != null) {
            me.K = (byte) (b.aw >= me.aw ? gx.I : gx.J);
        }
        gui(3, bId[k], sung, tien);
        // tu nang luong tren tay ngay khi bam
        for (int i = 0; i < 12; i++) {
            hat(me.aw * js.ay + rnd(-16, 17), me.ax * js.ay - rnd(10, 30), rnd(-20, 21), rnd(-60, -20), 500, SANG_SUNG[sung], rnd(2, 4), 0);
        }
        song(me.aw * js.ay, me.ax * js.ay, 34, SANG_SUNG[sung], 400);
    }

    /** may chu bao: ai do tung chieu -> bat dau hieu ung */
    static void batChieu(int ai, int boss, int g, int lv, int dmg, boolean crit) {
        int i = trong(cOn);
        if (i < 0) {
            i = 0;
        }
        hn a = ir.g(ai);
        hn me = main.GameMidlet.i;
        if (a == null && me != null && ai == me.w) {
            a = me;
        }
        if (a == null) {
            return;
        }
        hn b = ir.g(boss);
        if (b != null) {
            a.K = (byte) (b.aw >= a.aw ? gx.I : gx.J);
        }
        cOn[i] = true;
        cXong[i] = false;
        cG[i] = g;
        cLv[i] = lv;
        cAi[i] = ai;
        cBoss[i] = boss;
        cDmg[i] = dmg;
        cCrit[i] = crit;
        cBuoc[i] = 0;
        cT[i] = now();
        int phai = a.K == gx.I ? 1 : -1;
        cX0[i] = a.aw * js.ay + phai * (6 + tMX - tAX);
        cY0[i] = (a.ax + a.N) * js.ay - 11 + (tMY - tAY);
        if (me != null && ai == me.w) {
            banner(null, tenChieu[g], MAU[g]);
            bangAnh = "h_b_chieu" + g;
            rung(2 + lv / 3, 300);
        }
    }

    /** dan tu nong sung cua nguoi a toi boss b */
    static void phongDan(hn a, hn b, int g, int lv, boolean cuaToi) {
        int phai = a.K == gx.I ? 1 : -1;
        int x0 = a.aw * js.ay + phai * (6 + tMX - tAX), y0 = (a.ax + a.N) * js.ay - 11 + (tMY - tAY);
        int x1 = b.aw * js.ay + (a.aw < b.aw ? -16 : 16) + rnd(-5, 6), y1 = b.ax * js.ay - 26 - rnd(0, 26);
        int soVien = lv >= 8 ? 3 : lv >= 4 ? 2 : 1;
        if (g == 1 || g == 2) {
            soVien = 1;
        }
        loe(x0, y0, g, lv);
        for (int v = 0; v < soVien; v++) {
            int i = trong(dOn);
            if (i < 0) {
                return;
            }
            dOn[i] = true;
            dX0[i] = x0;
            dY0[i] = y0;
            dX1[i] = x1 + (v == 0 ? 0 : (v == 1 ? -14 : 14));
            dY1[i] = y1 + (v == 0 ? 0 : (v == 1 ? 8 : -8));
            dG[i] = g;
            dLv[i] = lv;
            dT[i] = now() + v * 60;
            int kc = can((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0));
            int tocDo = g == 0 ? 210 : g == 1 ? 900 : g == 2 ? 2000 : 260;       // px / giay
            dDur[i] = Math.max(g == 2 ? 140 : 80, kc * 1000 / tocDo);
            dCuaToi[i] = cuaToi && v == 0;
            dSo[i] = -1;
            dTrung[i] = 0;
            dBoss[i] = b.w;
        }
    }

    static int trong(boolean[] a) {
        for (int i = 0; i < a.length; i++) {
            if (!a[i]) {
                return i;
            }
        }
        return -1;
    }

    /** may chu bao: ai do ban trung boss */
    static void trungTin(int ai, int boss, int g, int lv, int dmg, boolean crit, int hp, int mx) {
        int k = viTri(boss);
        if (k >= 0) {
            bHp[k] = hp;
            bMax[k] = Math.max(1, mx);
        }
        hn me = main.GameMidlet.i;
        if (me != null && ai == me.w) {
            // gan so vao vien dan dang bay som nhat cua toi
            int best = -1;
            long bt = Long.MAX_VALUE;
            for (int i = 0; i < ND; i++) {
                if (dOn[i] && dCuaToi[i] && dSo[i] < 0 && dBoss[i] == boss && dT[i] < bt) {
                    bt = dT[i];
                    best = i;
                }
            }
            if (best >= 0) {
                dSo[best] = dmg;
                dCrit[best] = crit;
            } else {
                hn b = ir.g(boss);
                if (b != null) {
                    so(b.aw * js.ay + rnd(-10, 11), b.ax * js.ay - 50, dmg, crit, true);
                }
            }
            return;
        }
        hn a = ir.g(ai), b = ir.g(boss);
        sungNguoi.put(new Integer(ai), new Integer(g * 16 + lv));
        if (a != null && b != null) {
            a.K = (byte) (b.aw >= a.aw ? gx.I : gx.J);
            phongDan(a, b, g, lv, false);
            for (int i = 0; i < ND; i++) {
                if (dOn[i] && !dCuaToi[i] && dSo[i] < 0 && dBoss[i] == boss && dX0[i] == a.aw * js.ay + (a.K == gx.I ? 1 : -1) * (6 + tMX - tAX)) {
                    dSo[i] = dmg;
                    dCrit[i] = crit;
                    break;
                }
            }
        } else if (b != null) {
            if (crit) {
                so(b.aw * js.ay, b.ax * js.ay - 50, dmg, crit, false);
            }
            chop(k);
        }
    }

    static void bossChet(int boss, int ai, String ten) {
        int k = viTri(boss);
        if (k < 0) {
            return;
        }
        bHp[k] = 0;
        bChet[k] = now();
        hn b = ir.g(boss);
        if (b != null) {
            int x = b.aw * js.ay, y = b.ax * js.ay;
            for (int i = 0; i < 70; i++) {
                hat(x + rnd(-20, 21), y - rnd(10, 60), rnd(-130, 131), rnd(-170, -20), rnd(700, 1500), i % 3 == 0 ? 2 : i % 3 == 1 ? 3 : 6, rnd(1, 5), rnd(0, 20));
            }
            no(x, y - 40, 3, 1);
            no(x - 22, y - 24, 2, 1);
            no(x + 24, y - 30, 3, 0);
            no(x + 6, y - 60, 2, 0);
            song(x, y, 110, 3, 700);
            song(x, y, 70, 2, 500);
            rung(7, 500);
        }
        hn me = main.GameMidlet.i;
        banner("HẠ GỤC " + TEN_HOA[bLoai[k] % TEN_HOA.length], me != null && ai == me.w ? "Bạn kết liễu! Nhận thưởng lớn" : ten.length() > 0 ? ten + " kết liễu" : "", 0xFF7A24);
        if (muc == k) {
            muc = -1;
        }
    }

    static void hienBoss(int x, int y) {
        song(x, y, 90, 5, 800);
        for (int i = 0; i < 40; i++) {
            hat(x + rnd(-30, 31), y - rnd(0, 10), rnd(-20, 21), rnd(-160, -60), rnd(600, 1200), 5, rnd(1, 4), 0);
        }
        rung(4, 300);
    }

    // ================================================================ hieu ung co ban
    static void hat(int x, int y, int vx, int vy, int song, int mau, int co, int z) {
        int i = trong(hOn);
        if (i < 0) {
            return;
        }
        hOn[i] = true;
        hX[i] = x << 4;
        hY[i] = y << 4;
        hVX[i] = vx;
        hVY[i] = vy;
        hSong[i] = song;
        hMau[i] = mau;
        hCo[i] = co;
        hZ[i] = z << 4;
        hVZ[i] = 0;
        hT[i] = now();
    }

    /** vu no 3D: mau 0 lam / 1 hong / 2 vang / 3 lua; to 0 = 40px, 1 = 64px */
    static void no(int x, int y, int mau, int to) {
        int i = trong(xOn);
        if (i < 0) {
            long cu = Long.MAX_VALUE;
            for (int k = 0; k < NX; k++) {
                if (xT[k] < cu) {
                    cu = xT[k];
                    i = k;
                }
            }
        }
        xOn[i] = true;
        xX[i] = x;
        xY[i] = y;
        xM[i] = Math.max(0, Math.min(3, mau));
        xC[i] = to;
        xT[i] = now();
    }

    static void song(int x, int y, int r, int mau, int dur) {
        int i = trong(vOn);
        if (i < 0) {
            return;
        }
        vOn[i] = true;
        vX[i] = x;
        vY[i] = y;
        vR[i] = r;
        vMau[i] = mau;
        vDur[i] = dur;
        vT[i] = now();
    }

    static void so(int x, int y, int v, boolean crit, boolean toi) {
        int i = trong(sOn);
        if (i < 0) {
            long cu = Long.MAX_VALUE;
            for (int k = 0; k < NS; k++) {
                if (sT[k] < cu) {
                    cu = sT[k];
                    i = k;
                }
            }
        }
        sOn[i] = true;
        sX[i] = x;
        sY[i] = y;
        sV[i] = v;
        sCrit[i] = crit;
        sToi[i] = toi;
        sT[i] = now();
    }

    static void loe(int x, int y, int g, int lv) {
        int i = trong(mOn);
        if (i < 0) {
            i = 0;
        }
        mOn[i] = true;
        mX[i] = x;
        mY[i] = y;
        mG[i] = g;
        mLv[i] = lv;
        mT[i] = now();
    }

    static void rung(int bien, int ms) {
        rungBien = Math.max(rungBien * (now() < rungDen ? 1 : 0), bien);
        rungDen = Math.max(rungDen, now() + ms);
    }

    static void chop(int k) {
        if (k >= 0) {
            bChop[k] = now();
        }
    }

    static void banner(String a, String b, int mau) {
        bang = a;
        bang2 = b;
        bangMau = mau;
        bangLuc = now();
        bangAnh = a == null ? null : a.startsWith("BOSS XU") ? "h_b_xuat" : a.startsWith("HẠ GỤC") ? "h_b_ha"
                : a.startsWith("NÂNG CẤP TH") ? "h_b_nangok" : a.startsWith("THẤT") ? "h_b_nangxit" : null;
        if (a == null) {
            bang = "";
        }
    }

    static void tinNho(String s, int mau) {
        tinNho.addElement(s);
        tinLuc.addElement(new long[]{now(), mau});
        while (tinNho.size() > 4) {
            tinNho.removeElementAt(0);
            tinLuc.removeElementAt(0);
        }
    }

    static void xoaHieuUng() {
        for (int i = 0; i < ND; i++) {
            dOn[i] = false;
        }
        for (int i = 0; i < NH; i++) {
            hOn[i] = false;
        }
        for (int i = 0; i < NV; i++) {
            vOn[i] = false;
        }
        for (int i = 0; i < NS; i++) {
            sOn[i] = false;
        }
        for (int i = 0; i < NX; i++) {
            xOn[i] = false;
        }
        for (int i = 0; i < NC; i++) {
            cOn[i] = false;
        }
        bang = null;
        bangNang = false;
        nB = 0;
        muc = -1;
        Cam.dich(-dx0, -dy0);
        dx0 = dy0 = 0;
        rungDen = 0;
    }

    // ================================================================ ve lop nen (sau ir.b, da dich camera)
    public static void veNen(Graphics g) {
        if (!bat()) {
            return;
        }
        nap();
        long t = now();
        int cx = Cam.x(), cy = Cam.y();
        if (imGan != null && sanMoi()) {
            int W = main.a.m, H = main.a.n;
            if (imXa != null) {
                int xx = cx - cx / 4;
                int yy = -352;
                if (yy > cy) {
                    g.setColor(0x2E1A5E);
                    g.fillRect(cx, cy, W, yy - cy);
                }
                g.drawImage(imXa, xx, yy, 20);
                if (xx + imXa.getWidth() < cx + W) {
                    g.setColor(0x6E3A78);
                    g.fillRect(xx + imXa.getWidth(), yy, cx + W - xx - imXa.getWidth(), 360);
                }
            }
            g.drawImage(imGan, 0, -216, 20);
            veLua(g, DUOC_SAU, t);
        }
        // song xung kich tren san
        for (int i = 0; i < NV; i++) {
            if (!vOn[i]) {
                continue;
            }
            int e = (int) (t - vT[i]);
            if (e >= vDur[i]) {
                vOn[i] = false;
                continue;
            }
            int r = vR[i] * (40 + e * 60 / vDur[i]) / 100;
            int c = mauO(vMau[i]);
            g.setColor(c);
            g.drawArc(vX[i] - r, vY[i] - r * 2 / 5, r * 2, r * 4 / 5, 0, 360);
            if (e < vDur[i] * 2 / 3) {
                g.setColor(tron(c, 0xFFFFFF, 50));
                g.drawArc(vX[i] - r + 2, vY[i] - r * 2 / 5 + 1, r * 2 - 4, r * 4 / 5 - 2, 0, 360);
                g.setColor(c);
                g.drawArc(vX[i] - r + 4, vY[i] - r * 2 / 5 + 2, r * 2 - 8, r * 4 / 5 - 4, 0, 360);
            }
        }
        // vong phap tran duoi chan boss
        for (int i = 0; i < nB; i++) {
            if (bChet[i] != 0) {
                continue;
            }
            hn b = ir.g(bId[i]);
            if (b == null) {
                continue;
            }
            int x = b.aw * js.ay, y = b.ax * js.ay;
            veVong(g, x, y, t, i == muc, bLoai[i]);
        }
    }

    static void veVong(Graphics g, int x, int y, long t, boolean dangNham, int loai) {
        int[] m = {0xFFD23A, 0xBFE8FF, 0x8CFF6E, 0xFF7AD9, 0xFFB23A, 0xA05CFF};
        int c = m[loai % m.length];
        int ph = (int) (t / 90 % 360);
        int rx = 34, ry = 12;
        if (imVong != null) {
            int f = (int) (t / 110 % 4);
            g.drawRegion(imVong, 0, f * 36, 96, 36, 0, x - 48, y - 18, 20);
        } else {
            g.setColor(c);
            g.drawArc(x - rx, y - ry, rx * 2, ry * 2, 0, 360);
        }
        g.setColor(c);
        for (int k = 0; k < 3; k++) {
            g.drawArc(x - rx - 4, y - ry - 2, rx * 2 + 8, ry * 2 + 4, ph + k * 120, 40);
        }
        if (dangNham) {
            g.setColor(0xFF4040);
            int s = (int) (t / 120 % 2);
            g.drawArc(x - rx - 8 - s, y - ry - 4, rx * 2 + 16 + 2 * s, ry * 2 + 8, 0, 360);
        }
    }

    static void veLua(Graphics g, int[][] ds, long t) {
        for (int i = 0; i < ds.length; i++) {
            int x = ds[i][0], y = ds[i][1];
            if (imLua != null) {
                int f = (int) ((t / 80 + i * 3) % 6);
                g.drawRegion(imLua, f * 12, 0, 12, 18, 0, x - 6, y - 14, 20);
            }
            sang(g, 3, 2, x, y - 4);
            if (rnd(0, 6) == 0) {
                hat(x + rnd(-2, 3), y - 8, rnd(-6, 7), rnd(-40, -20), 500, 3, 1, 0);
            }
        }
    }

    // ================================================================ boss: ve thay nhan vat (hn.a dau ham)
    public static boolean veThay(hn h, Graphics g) {
        if (!bat() || h == null) {
            return false;
        }
        int k = viTri(h.w);
        if (k < 0) {
            veSungTay(h, g);                     // sung to: ve TRUOC nhan vat -> nhan vat dung truoc, mat khong bi canh che
            return false;
        }
        long t = now();
        int x = h.aw * js.ay, y = h.ax * js.ay;
        if (bChet[k] != 0) {
            return true;                          // da ha: an cho toi khi hoi sinh
        }
        Image im = bAnh[k];
        if (im == null || t - bAnhLuc[k] > 110) {
            Image moi = phongTo(h);
            if (moi != null) {
                bAnh[k] = moi;
                im = moi;
                bAnhLuc[k] = t;
                bVien[k] = vien(k == muc ? 0xFF5A3C : 0xFFD23A);
            }
        }
        int bob = 0;
        int rx = 0;
        if (t - bChop[k] < 140) {
            rx = rnd(-2, 3);
        }
        // bong to duoi chan
        if (ir.r != null) {
            g.drawImage(ir.r, x - 6, y, 3);
            g.drawImage(ir.r, x + 6, y, 3);
        }
        if (im != null) {
            Image vi = bVien[k];
            if (vi != null) {                              // vien sang 1px quanh boss -> noi bat giua dam dong
                g.drawImage(vi, x + rx - 1, y + 6 - bob, 33);
                g.drawImage(vi, x + rx + 1, y + 6 - bob, 33);
                g.drawImage(vi, x + rx, y + 5 - bob, 33);
                g.drawImage(vi, x + rx, y + 7 - bob, 33);
            }
            g.drawImage(im, x + rx, y + 6 - bob, 33);
            if (t - bChop[k] < 90) {
                sang(g, 4, 5, x, y - 40);
            }
        } else {
            return false;
        }
        // ten + thanh mau tren dau
        int top = y - BH * 2 + 14;
        int w = 64;
        g.setColor(0x1A1020);
        g.fillRect(x - w / 2 - 1, top - 1, w + 2, 6);
        int hien = bHien[k] * w / Math.max(1, bMax[k]);
        int that = bHp[k] * w / Math.max(1, bMax[k]);
        g.setColor(0xFFFFFF);
        g.fillRect(x - w / 2, top, hien, 4);
        g.setColor(that * 3 > w ? 0xE8323C : 0xFF8A1E);
        g.fillRect(x - w / 2, top, that, 4);
        g.setColor(0xFF9AA0);
        g.drawLine(x - w / 2, top, x - w / 2 + that - 1, top);
        main.a.L.a(g, bTen[k], x, top - main.a.L.a() - 1, 2);
        if (h.O != null) {
            h.O.a(g);
        }
        return true;
    }

    /** bong cua anh boss vua phong to (moi diem khong trong suot -> mau vien) */
    static Image vien(int mau) {
        try {
            int n = BW * BH * 4;
            if (bVienPx == null) {
                bVienPx = new int[n];
            }
            int c = 0xFF000000 | mau;
            for (int i = 0; i < n; i++) {
                bVienPx[i] = (bOut[i] >>> 24) != 0 ? c : 0;
            }
            return Image.createRGBImage(bVienPx, BW * 2, BH * 2, true);
        } catch (Throwable e) {
            return null;
        }
    }

    /** ve nhan vat boss vao anh tam (nen tim), tach nen, phong to x2 khong lam mo (giu net pixel) */
    static Image phongTo(hn h) {
        try {
            if (bBuf == null) {
                bBuf = Image.createImage(BW, BH);
                bBufG = bBuf.getGraphics();
                bPx = new int[BW * BH];
                bOut = new int[BW * BH * 4];
            }
            bBufG.setClip(0, 0, BW, BH);
            bBufG.setColor(0xFF00FF);
            bBufG.fillRect(0, 0, BW, BH);
            fh o = h.O;
            h.O = null;
            h.a(bBufG, BW / 2, BH - 5, false);
            h.O = o;
            bBuf.getRGB(bPx, 0, BW, 0, 0, BW, BH);
            int W2 = BW * 2;
            for (int yy = 0; yy < BH; yy++) {
                int r0 = yy * BW, o0 = yy * 2 * W2;
                for (int xx = 0; xx < BW; xx++) {
                    int c = bPx[r0 + xx];
                    int rgb = c & 0xFFFFFF;
                    // nen tim + vien pha nen
                    int v = (rgb == 0xFF00FF || (((rgb >> 16) & 255) > 200 && (rgb & 255) > 200 && ((rgb >> 8) & 255) < 60)) ? 0 : (0xFF000000 | rgb);
                    int p = o0 + xx * 2;
                    bOut[p] = v;
                    bOut[p + 1] = v;
                    bOut[p + W2] = v;
                    bOut[p + W2 + 1] = v;
                }
            }
            return Image.createRGBImage(bOut, W2, BH * 2, true);
        } catch (Throwable e) {
            return null;
        }
    }

    // ================================================================ sung tren tay (hn.a truoc gx.a)
    /** hn.a truoc gx.a: than sung (khong canh) ve TRUOC nhan vat - canh vay o lop sau (veThay), mat nhan vat khong bi che */
    public static void sauNguoi(hn h, Graphics g) {
        if (!bat() || h == null || imThan == null || h.w > 2000000000 || h.az != 0 || viTri(h.w) >= 0) {
            return;
        }
        int gg, lv;
        int[] sl = sungCua(h);
        gg = sl[0];
        lv = sl[1];
        if (gg < 0 || gg > 3 || lv < 1 || lv > 10) {
            return;
        }
        int x = h.aw * js.ay, y = (h.ax + h.N) * js.ay;
        boolean phai = h.K == gx.I;
        int giat = (h == main.GameMidlet.i && now() - giatLuc < 90) ? 4 : 0;
        int hx = x + (phai ? 6 : -6), hy = y - 11;
        int gx0 = phai ? hx - tAX - giat : hx - (tCW - tAX) + giat;
        g.drawRegion(imThan, (lv - 1) * tCW, gg * tCH, tCW, tCH, phai ? 0 : Sprite.TRANS_MIRROR, gx0, hy - tAY, 20);
    }

    /** sung + cap cua nguoi h: toi = dang chon; nguoi khac = phat ban gan nhat; nguoi ao = theo id (giong may chu) */
    static int[] sungCua(hn h) {
        if (h == main.GameMidlet.i) {
            return new int[]{sung, capS[sung]};
        }
        Object o = sungNguoi.get(new Integer(h.w));
        if (o != null) {
            int v = ((Integer) o).intValue();
            return new int[]{v / 16, v % 16};
        }
        int a = h.w < 0 ? -h.w : h.w;
        return new int[]{a % 4, 1 + (a / 4) % 10};
    }

    static void veSungTay(hn h, Graphics g) {
        if (!bat() || h == null || imTay == null || h.w > 2000000000 || h.az != 0) {
            return;
        }
        // nhu hn.a: bo qua nguoi ngoai man
        if ((h.aw + 50) * js.ay < Cam.x() || (h.aw - 50) * js.ay > Cam.x() + main.a.m) {
            return;
        }
        int[] sl = sungCua(h);
        int gg = sl[0], lv = sl[1];
        if (gg < 0 || gg > 3 || lv < 1 || lv > 10) {
            return;
        }
        int x = h.aw * js.ay, y = (h.ax + h.N) * js.ay;
        boolean phai = h.K == gx.I;
        int giat = 0;
        if (h == main.GameMidlet.i && now() - giatLuc < 90) {
            giat = 4;
        }
        // tay cam o ngang hong, hoi lech ve phia truoc; sung to (khoe) -> nong vuot qua nguoi
        int hx = x + (phai ? 6 : -6), hy = y - 11;
        int sx = (lv - 1) * tCW, sy = khungVay(h.w & 3) * tCH * 4 + gg * tCH;
        int gx0 = phai ? hx - tAX - giat : hx - (tCW - tAX) + giat;
        g.drawRegion(imTay, sx, sy, tCW, tCH, phai ? 0 : Sprite.TRANS_MIRROR, gx0, hy - tAY, 20);
        // cap 10 Hoa than: lua + tan lua boc quanh sung lien tuc
        if (lv >= 10 && rnd(0, 3) == 0) {
            int fx = gx0 + rnd(tCW / 6, tCW * 5 / 6), fy = hy - tAY + rnd(tCH / 5, tCH * 4 / 5);
            hat(fx, fy, rnd(-12, 13), rnd(-70, -25), rnd(350, 650), rnd(0, 3) == 0 ? 2 : rnd(0, 2) == 0 ? 3 : 6, rnd(1, 4), 0);
        }
        if (lv >= 10 && ((now() / 90 + (h.w & 7)) & 1) == 0) {
            sang(g, 6, 2, gx0 + tCW / 2, hy - tAY + tCH / 2);
        }
        // loi nang luong nhap nhay tren than sung (cap 4+)
        if (lv >= 4 && ((now() / 140 + (h.w & 7)) % 3) == 0) {
            int lx = phai ? gx0 + tAX + 6 : gx0 + tCW - tAX - 6;
            sang(g, SANG_SUNG[gg], 0, lx, hy - tAY + tCH / 2);
        }
        if (lv >= 7) {
            int c = MAU[gg];
            int ph = (int) (now() / 60 % 360);
            g.setColor(c);
            g.drawArc(x - 14, y - 4, 28, 9, ph, 90);
            g.drawArc(x - 14, y - 4, 28, 9, ph + 180, 90);
        }
    }

    // ================================================================ ve lop tren (sau ir.d, da dich camera)
    public static void veTren(Graphics g) {
        if (!bat()) {
            return;
        }
        long t = now();
        if (imTruoc != null && sanMoi()) {
            g.drawImage(imTruoc, 0, 150, 20);
            veLua(g, DUOC_TRUOC, t);
        }
        // loe dau nong
        for (int i = 0; i < NM; i++) {
            if (!mOn[i]) {
                continue;
            }
            int e = (int) (t - mT[i]);
            if (e > 110) {
                mOn[i] = false;
                continue;
            }
            int c = 2 + mLv[i] / 4 - (e > 55 ? 1 : 0);
            sang(g, SANG_SUNG[mG[i]], c + 1, mX[i], mY[i]);
            sang(g, 4, c, mX[i], mY[i]);
        }
        // dan
        for (int i = 0; i < ND; i++) {
            if (!dOn[i]) {
                continue;
            }
            int e = (int) (t - dT[i]);
            if (e < 0) {
                continue;
            }
            if (e >= dDur[i]) {
                trungDich(i);
                dOn[i] = false;
                continue;
            }
            veDan(g, i, e, t);
        }
        if (tongToi > 0 && t - tongLuc > 220) {
            so(tongX + rnd(-12, 13), tongY - rnd(0, 8), tongToi, false, true);
            tongToi = 0;
        }
        for (int i = 0; i < NC; i++) {
            if (cOn[i]) {
                veChieu(g, i, t);
            }
        }
        for (int i = 0; i < NX; i++) {
            if (!xOn[i]) {
                continue;
            }
            int f = (int) ((t - xT[i]) / 55);
            if (f >= 7) {
                xOn[i] = false;
                continue;
            }
            int sz = xC[i] == 1 ? 64 : 40;
            Image im = h(xC[i] == 1 ? "no64" : "no40");
            if (im != null) {
                g.drawRegion(im, f * sz, xM[i] * sz, sz, sz, 0, xX[i] - sz / 2, xY[i] - sz / 2, 20);
            }
        }
        // hat
        for (int i = 0; i < NH; i++) {
            if (!hOn[i]) {
                continue;
            }
            int e = (int) (t - hT[i]);
            if (e >= hSong[i]) {
                hOn[i] = false;
                continue;
            }
            hX[i] += hVX[i] * 16 / 20;
            hY[i] += hVY[i] * 16 / 20;
            hVY[i] += 9;
            hVX[i] = hVX[i] * 96 / 100;
            int x = hX[i] >> 4, y = hY[i] >> 4;
            int c = hCo[i];
            if (e > hSong[i] * 2 / 3 && c > 0) {
                c--;
            }
            if (c <= 1) {
                g.setColor(mauO(hMau[i]));
                g.fillRect(x, y, 2, 2);
                g.setColor(0xFFFFFF);
                g.fillRect(x, y, 1, 1);
            } else {
                sang(g, hMau[i], c - 2, x, y);
            }
        }
        // so sat thuong
        for (int i = 0; i < NS; i++) {
            if (!sOn[i]) {
                continue;
            }
            int e = (int) (t - sT[i]);
            if (e > 900) {
                sOn[i] = false;
                continue;
            }
            int y = sY[i] - e * 30 / 900 - (e < 120 ? (120 - e) / 12 : 0);
            veSo(g, sX[i], y, sV[i], sCrit[i], sToi[i], e);
        }
    }

    static void veDan(Graphics g, int i, int e, long t) {
        int gg = dG[i], lv = dLv[i];
        int x0 = dX0[i], y0 = dY0[i], x1 = dX1[i], y1 = dY1[i];
        int p = e * 1024 / dDur[i];
        int x = x0 + (x1 - x0) * p / 1024, y = y0 + (y1 - y0) * p / 1024;
        int big = lv / 3;
        if (gg == 0) {               // bong bong: bay cham, lac lu, to dan theo cap
            y -= (p * (1024 - p) >> 14);
            x += (CayRongSin((int) (t / 20) + i * 7) * 3) >> 10;
            int c = Math.min(2, 0 + lv / 4);
            if (imBong != null) {
                int[] s = {14, 20, 28};
                int ox = c == 0 ? 0 : c == 1 ? 14 : 34;
                g.drawRegion(imBong, ox, 0, s[c], s[c], 0, x - s[c] / 2, y - s[c] / 2, 20);
            }
            sang(g, 0, 2 + big, x, y);
            if ((e / 40) % 2 == 0) {
                hat(x + rnd(-3, 4), y + rnd(-3, 4), rnd(-10, 11), rnd(-10, 11), 300, 0, 1, 0);
            }
        } else if (gg == 1) {        // laser: tia sang dai + dau dan
            int tx = x0 + (x1 - x0) * Math.max(0, p - 280) / 1024, ty = y0 + (y1 - y0) * Math.max(0, p - 280) / 1024;
            int w = 1 + lv / 4;
            for (int k = -w - 1; k <= w + 1; k++) {
                g.setColor(Math.abs(k) > w ? 0x8A1A6E : Math.abs(k) > w / 2 ? 0xFF4FD8 : 0xFFE0F7);
                g.drawLine(tx, ty + k, x, y + k);
            }
            sang(g, 1, 2 + big, x, y);
            Image pl = h("pl");
            if (pl != null) {
                g.drawRegion(pl, (int) ((t / 60) & 3) * 16, 0, 16, 16, 0, x - 8, y - 8, 20);
            } else {
                sang(g, 4, 1, x, y);
            }
        } else if (gg == 2) {        // sam set: tia zigzag nhay lien tuc
            veSet(g, x0, y0, x1, y1, lv, t + i);
            sang(g, 2, 3 + big, x1, y1);
            Image pl = h("pl");
            if (pl != null) {
                g.drawRegion(pl, (int) ((t / 50) & 3) * 16, 16, 16, 16, 0, x1 - 8, y1 - 8, 20);
            }
        } else {                     // rong lua: cau lua 3D xoay + vet lua
            y -= (p * (1024 - p) >> 13);
            sang(g, 3, 3 + big, x, y);
            Image cl = h("cau");
            if (cl != null) {
                int f = (int) ((t / 70 + i) & 3);
                g.drawRegion(cl, f * 22, 0, 22, 22, 0, x - 11, y - 11, 20);
            } else {
                sang(g, 2, 1 + big, x, y);
            }
            for (int k = 0; k < 2; k++) {
                hat(x + rnd(-3, 4), y + rnd(-3, 4), rnd(-25, 26) - (x1 - x0) / 20, rnd(-30, 5), rnd(250, 450), k == 0 ? 3 : 6, rnd(2, 4), 0);
            }
        }
    }

    /** ve 1 chieu theo thoi gian; cuoi chieu: no lon + so sat thuong */
    static void veChieu(Graphics g, int i, long t) {
        int e = (int) (t - cT[i]);
        hn b = ir.g(cBoss[i]);
        int bx = b != null ? b.aw * js.ay : cX0[i] + 80, by = b != null ? b.ax * js.ay : cY0[i] + 17;
        int tamY = by - 34;
        int gg = cG[i], lv = cLv[i];
        int to = lv / 3;                                   // 0..3: cap cang cao chieu cang to
        int m = SANG_SUNG[gg];
        int dai = gg == 0 ? 1300 : gg == 1 ? 1250 : gg == 2 ? 1000 : 1100;
        if (gg == 0) {                                     // MUA BONG BONG: bong bong roi don xuong boss roi no
            int n = 8 + lv * 2;
            for (int k = 0; k < n; k++) {
                int t0 = k * 900 / n, p = e - t0;
                if (p < 0 || p > 420) {
                    continue;
                }
                int ox = ((k * 37) % 61) - 30, oy = ((k * 23) % 21) - 10;
                int x = bx + ox, y = tamY - 150 + (150 + oy) * p / 420;
                int c = Math.min(2, (k + lv) % 3);
                int[] sz = {14, 20, 28};
                int sx = c == 0 ? 0 : c == 1 ? 14 : 34;
                if (imBong != null) {
                    g.drawRegion(imBong, sx, 0, sz[c], sz[c], 0, x - sz[c] / 2, y - sz[c] / 2, 20);
                }
                sang(g, 0, 2 + to, x, y);
                if (p > 380 && cBuoc[i] <= k) {
                    cBuoc[i] = k + 1;
                    for (int q = 0; q < 6; q++) {
                        hat(x, y, rnd(-70, 71), rnd(-80, 21), 380, q % 2 == 0 ? 0 : 4, rnd(1, 3), 0);
                    }
                    song(x, by, 20 + lv * 2, 0, 260);
                }
            }
            if (e > 900) {
                int r = 20 + (e - 900) * (30 + lv * 4) / 400;
                g.setColor(0xBFF4FF);
                g.drawArc(bx - r, tamY - r, r * 2, r * 2, 0, 360);
                g.setColor(0x5FD8FF);
                g.drawArc(bx - r + 2, tamY - r + 2, r * 2 - 4, r * 2 - 4, 0, 360);
            }
        } else if (gg == 1) {                              // PHAO LASER: tu luc roi tia khong lo
            int x0 = cX0[i], y0 = cY0[i];
            if (e < 380) {
                sang(g, 1, 2 + e * (3 + to) / 380, x0, y0);
                sang(g, 4, 1 + e * 2 / 380, x0, y0);
                if (rnd(0, 2) == 0) {
                    int a = rnd(0, 64);
                    hat(x0 + (CayRongSin(a) * 26 >> 10), y0 + (CayRongSin(a + 16) * 26 >> 10), -(CayRongSin(a) * 60 >> 10), -(CayRongSin(a + 16) * 60 >> 10), 250, 1, 2, 0);
                }
            } else if (e < 1150) {
                int w = 4 + lv / 2 + ((e / 40) % 2);
                for (int k = -w - 2; k <= w + 2; k++) {
                    int a = k < 0 ? -k : k;
                    g.setColor(a > w ? 0x7A1060 : a > w * 2 / 3 ? 0xFF4FD8 : a > w / 3 ? 0xFFA6EC : 0xFFFFFF);
                    g.drawLine(x0, y0 + k, bx, tamY + k * 3 / 2);
                }
                for (int k = 0; k < 4; k++) {
                    int p = rnd(0, 1024);
                    sang(g, k % 2 == 0 ? 1 : 4, 1 + to, x0 + (bx - x0) * p / 1024, y0 + (tamY - y0) * p / 1024 + rnd(-w, w + 1));
                }
                sang(g, 1, 5, bx, tamY);
                sang(g, 4, 3, bx, tamY);
                sang(g, 1, 3 + to, x0, y0);
                if ((e / 60) % 2 == 0) {
                    hat(bx, tamY, rnd(-120, 121), rnd(-140, 21), 400, rnd(0, 2) == 0 ? 1 : 4, rnd(2, 4), 0);
                    chop(viTri(cBoss[i]));
                }
                if (cAi[i] == main.GameMidlet.i.w) {
                    rung(2 + to, 80);
                }
            }
        } else if (gg == 2) {                              // LOI THAN GIANG: sam set tu troi danh xuong
            int n = 4 + lv / 2;
            int topY = Cam.y() - 10;
            for (int k = 0; k < n; k++) {
                int t0 = k * 700 / n, p = e - t0;
                if (p < 0 || p > 160) {
                    continue;
                }
                int ox = ((k * 29) % 51) - 25;
                veSet(g, bx + ox + ((k * 13) % 31) - 15, topY, bx + ox, tamY + 10, lv + 4, t / 30 + k);
                sang(g, 2, 5, bx + ox, tamY + 10);
                sang(g, 4, 3, bx + ox, tamY + 10);
                if (cBuoc[i] <= k) {
                    cBuoc[i] = k + 1;
                    song(bx + ox, by, 34 + lv * 4, 2, 360);
                    for (int q = 0; q < 10; q++) {
                        hat(bx + ox, tamY + 10, rnd(-110, 111), rnd(-150, 11), 420, q % 3 == 0 ? 4 : 2, rnd(2, 4), 0);
                    }
                    chop(viTri(cBoss[i]));
                    rung(3 + to, 120);
                }
            }
        } else {                                           // RONG LUA: rong lua bay ran luon toi boss roi no
            int x0 = cX0[i], y0 = cY0[i];
            int bay = 900;
            int p = Math.min(1024, e * 1024 / bay);
            int seg = 12 + lv;
            for (int k = seg; k >= 0; k--) {
                int q = p - k * 26;
                if (q < 0 || q > 1024) {
                    continue;
                }
                int x = x0 + (bx - x0) * q / 1024;
                int y = y0 + (tamY - y0) * q / 1024 - (q * (1024 - q) >> 12) + (CayRongSin(q / 16 + k * 3) * (14 + to * 3) >> 10);
                int c = k == 0 ? 3 + to : Math.max(0, 3 + to - k / 3);
                sang(g, k < 3 ? 2 : 3, c, x, y);
                if (k == 0) {
                    sang(g, 4, 1 + to, x, y);
                }
                if (k % 4 == 0 && rnd(0, 3) == 0) {
                    hat(x, y, rnd(-30, 31), rnd(-40, 11), 450, k % 8 == 0 ? 6 : 3, rnd(2, 4), 0);
                }
            }
        }
        if (e >= dai && !cXong[i]) {                       // ket thuc: no lon + so
            cXong[i] = true;
            int n = 30 + lv * 6;
            for (int k = 0; k < n; k++) {
                hat(bx + rnd(-12, 13), tamY + rnd(-12, 13), rnd(-170, 171), rnd(-200, 41), rnd(500, 1000), k % 3 == 0 ? 4 : m, rnd(2, 5), 0);
            }
            int mauNo = gg == 0 ? 0 : gg == 1 ? 1 : gg == 2 ? 2 : 3;
            no(bx, tamY, mauNo, 1);
            no(bx - 18, tamY + 8, mauNo, 0);
            no(bx + 18, tamY - 6, mauNo, 0);
            song(bx, by, 70 + lv * 7, m, 650);
            song(bx, by, 40 + lv * 4, 4, 420);
            if (lv >= 10) {
                song(bx, by, 110, 2, 800);
            }
            sang(g, m, 5, bx, tamY);
            sang(g, 4, 4, bx, tamY);
            boolean toi = main.GameMidlet.i != null && cAi[i] == main.GameMidlet.i.w;
            so(bx, tamY - 26, cDmg[i], true, toi);
            if (toi) {
                rung(5 + to, 350);
            }
            chop(viTri(cBoss[i]));
        }
        if (e > dai + 120) {
            cOn[i] = false;
        }
    }

    static int CayRongSin(int a) {
        a &= 63;
        int q = a & 15;
        int v = q * 1024 / 16;
        if ((a & 16) != 0) {
            v = 1024 - v;
        }
        return (a & 32) != 0 ? -v : v;
    }

    static void veSet(Graphics g, int x0, int y0, int x1, int y1, int lv, long seed) {
        Random r = new Random(seed / 40);
        int n = 7;
        int px = x0, py = y0;
        int w = 1 + lv / 4;
        for (int k = 1; k <= n; k++) {
            int qx = x0 + (x1 - x0) * k / n, qy = y0 + (y1 - y0) * k / n;
            if (k < n) {
                qx += (r.nextInt() % 9);
                qy += (r.nextInt() % 11);
            }
            for (int d = -w; d <= w; d++) {
                g.setColor(d == 0 ? 0xFFFFFF : Math.abs(d) == w ? 0xB89A10 : 0xFFE45C);
                g.drawLine(px, py + d, qx, qy + d);
            }
            if ((k & 1) == 0) {
                sang(g, 2, 1, qx, qy);
            }
            px = qx;
            py = qy;
        }
    }

    /** dan toi dich: no + so + chop boss */
    static void trungDich(int i) {
        int gg = dG[i], lv = dLv[i];
        int x = dX1[i], y = dY1[i];
        int m = SANG_SUNG[gg];
        int n = 8 + lv * 3;
        for (int k = 0; k < n; k++) {
            hat(x, y, rnd(-90, 91) * (10 + lv) / 12, rnd(-110, 31) * (10 + lv) / 12, rnd(250, 600), k % 3 == 0 ? 4 : m, rnd(1, 3 + lv / 3), 0);
        }
        int bossY = y + 34;
        int mauNo = gg == 0 ? 0 : gg == 1 ? 1 : gg == 2 ? 2 : 3;
        if (gg == 3 || lv >= 4 || dCrit[i] || rnd(0, 3) == 0) {
            no(x + rnd(-4, 5), y + rnd(-4, 5), mauNo, (lv >= 8 || (gg == 3 && lv >= 5) || dCrit[i]) ? 1 : 0);
        }
        song(x, bossY, 26 + lv * 5, m, 320 + lv * 20);
        if (gg == 3 || lv >= 7) {
            song(x, bossY, 16 + lv * 4, 4, 240);
        }
        if (gg == 3 && dCuaToi[i]) {
            rung(2 + lv / 3, 120);
        }
        int k = viTri(dBoss[i]);
        chop(k);
        if (dSo[i] >= 0 && dCuaToi[i] && !dCrit[i]) {
            if (tongToi == 0) {
                tongLuc = now();
            }
            tongToi += dSo[i];
            tongX = x;
            tongY = y - 14;
        } else if (dSo[i] >= 0 && (dCuaToi[i] || dCrit[i])) {
            so(x + rnd(-10, 11), y - 16, dSo[i], dCrit[i], dCuaToi[i]);
            if (dCrit[i]) {
                rung(4 + lv / 3, 200);
                for (int q = 0; q < 12; q++) {
                    hat(x, y, rnd(-150, 151), rnd(-150, 51), 450, 2, rnd(2, 4), 0);
                }
            }
        }
    }

    /** so sat thuong kieu ban demo: h_dmg (cua toi), h_crit (chi mang to + chu CHI MANG), h_dmgk (nguoi khac) */
    static void veSo(Graphics g, int x, int y, int v, boolean crit, boolean toi, int e) {
        String s = String.valueOf(v);
        String ten = crit ? "h_crit" : toi ? "h_dmg" : "h_dmgk";
        Image im = h(ten);
        if (im == null) {
            main.a.L.a(g, s, x, y, 2);
            return;
        }
        int w = crit ? 10 : toi ? 7 : 6, hh = im.getHeight();
        int buoc = w - (crit ? 2 : 1);
        int nhay = (crit && e < 160) ? (160 - e) / 40 : 0;
        int total = s.length() * buoc;
        int xx = x - total / 2;
        for (int k = 0; k < s.length(); k++) {
            int d = s.charAt(k) - '0';
            if (d < 0 || d > 9) {
                continue;
            }
            g.drawRegion(im, d * w, 0, w, hh, 0, xx + k * buoc, y - nhay, 20);
        }
        if (crit) {
            g.drawRegion(im, 10 * w, 0, w, hh, 0, xx + s.length() * buoc, y - nhay, 20);
            if (e < 700) {
                ve(g, "h_chimang", x, y - 9 - nhay, 17);
            }
        }
    }

    static int mauO(int m) {
        switch (m) {
            case 0: return 0x5FD8FF;
            case 1: return 0xFF4FD8;
            case 2: return 0xFFE45C;
            case 3: return 0xFF7A24;
            case 4: return 0xFFFFFF;
            case 5: return 0xB07CFF;
            case 6: return 0xFF3B3B;
            default: return 0x6CFF9A;
        }
    }

    static int tron(int a, int b, int pt) {
        int r = ((a >> 16) & 255) + ((((b >> 16) & 255) - ((a >> 16) & 255)) * pt) / 100;
        int gg = ((a >> 8) & 255) + ((((b >> 8) & 255) - ((a >> 8) & 255)) * pt) / 100;
        int bb = (a & 255) + (((b & 255) - (a & 255)) * pt) / 100;
        return (r << 16) | (gg << 8) | bb;
    }

    // ================================================================ HUD (truoc return cua ae.b, toa do man hinh) - kieu ban demo
    static int pY, the0x, nutY, chX, chY, bnX, bnY, bnW, bnH;
    static final int SOFT = 26, PANEL = 86, THE_W = 54, THE_H = 44;

    public static void veHud(Graphics g) {
        if (!bat()) {
            return;
        }
        long t = now();
        int W = main.a.m, H = main.a.n;
        g.setClip(0, 0, W, main.a.q);
        int cx = W / 2;
        // ---- thanh boss + chip tien (duoi 2 icon cua game goc tren trai)
        int by = 40, bx = cx - 116;
        if (muc >= 0 && muc < nB) {
            int k = muc;
            ve(g, "h_boss", bx, by, 20);
            ve(g, "h_bten" + (bLoai[k] % 6), bx + 5, by + 2, 20);
            int bw = 222, x0 = bx + 5, y0 = by + 13;
            g.setColor(0x0C0612);
            g.fillRoundRect(x0, y0, bw, 8, 6, 6);
            g.setColor(0xFFFFFF);
            g.fillRoundRect(x0, y0, Math.max(0, bHien[k] * bw / Math.max(1, bMax[k])), 8, 6, 6);
            int th = Math.max(0, bHp[k] * bw / Math.max(1, bMax[k]));
            g.setColor(0xC2183B);
            g.fillRoundRect(x0, y0, th, 8, 6, 6);
            g.setColor(0xFF6B6B);
            g.fillRoundRect(x0, y0, th, 4, 6, 6);
            chu(g, so(bHp[k]) + " / " + so(bMax[k]), cx, y0 + 1, true);
        } else if (coTT) {
            ve(g, "h_boss", bx, by, 20);
            main.a.L.a(g, "Boss tiếp theo sắp xuất hiện...", cx, by + 7, 2);
        }
        chY = by + 28;
        chX = cx - 116;
        chip(g, chX, chY, "h_ixu", gon(xu));
        chip(g, chX + 79, chY, "h_iluong", gon(luong));
        chip(g, chX + 158, chY, "h_ida", String.valueOf(dns));
        // ---- bang duoi
        pY = H - SOFT - PANEL;
        ve(g, "h_nen", cx - 120, pY, 20);
        if (W > 240) {
            g.setColor(0x0F3438);
            g.fillRect(0, pY + 2, cx - 120, PANEL);
            g.fillRect(cx + 120, pY + 2, W - cx - 120, PANEL);
        }
        the0x = cx - (THE_W * 4 + 12) / 2;
        int cy = pY + 5;
        for (int i = 0; i < 4; i++) {
            int x = the0x + i * (THE_W + 4);
            boolean chon = i == sung;
            if (chon) {
                ve(g, "h_the_chon" + i, x - 3, cy - 3, 20);
            } else {
                ve(g, "h_the", x, cy, 20);
            }
            int lv = capS[i];
            if (imSung != null) {
                g.drawRegion(imSung, (lv - 1) * 52, khungVay(i) * 124 + i * 31, 52, 31, 0, x + 1, cy, 20);
            }
            ve(g, "h_lv" + lv, x + THE_W - 2, cy + 1, 24);
            ve(g, "h_ten" + i + (chon ? 1 : 0), x, cy + 28, 20);
            // 5 sao = 10 cap (nua sao = 1 cap)
            for (int s2 = 0; s2 < 5; s2++) {
                int con = lv - s2 * 2;
                String st = con >= 2 ? "h_sao1" : con == 1 ? "h_saonua" : chon ? "h_sao0b" : "h_sao0a";
                ve(g, st, x + 9 + s2 * 7, cy + 36, 20);
            }
        }
        nutY = pY + 54;
        ve(g, tuDanh ? "h_tudanh1" : "h_tudanh0", the0x, nutY, 20);
        ve(g, tien == 0 ? "h_tien0" : "h_tien1", the0x + 96, nutY, 20);
        int lv = capS[sung];
        if (lv >= 10) {
            ve(g, "h_nangtoi", the0x + 150, nutY, 20);
        } else {
            ve(g, "h_nang", the0x + 150, nutY, 20);
            ve(g, "h_nanglv" + (lv + 1), the0x + 150, nutY + 2, 20);
            String c1 = String.valueOf(nangL[sung]), c2 = String.valueOf(nangD[sung]);
            int ww = (c1.length() + c2.length()) * 5 + 30;
            int xx = the0x + 191 - ww / 2;
            chu(g, c1, xx, nutY + 15, false);
            xx += c1.length() * 5 + 1;
            ve(g, "h_iluong", xx, nutY + 14, 20);
            xx += 13;
            chu(g, c2, xx, nutY + 15, false);
            xx += c2.length() * 5 + 1;
            ve(g, "h_ida", xx, nutY + 14, 20);
        }
        // ---- nut chieu tron (tren bang, ben phai)
        int kx = W - 24, ky = pY - 26;
        ve(g, "h_chieu" + sung, kx, ky, 3);
        int con = (int) (chieuCd - (t - chieuLuc));
        if (con > 0) {
            g.setColor(0x140A20);
            int goc = con * 360 / Math.max(1, chieuCd);
            g.fillArc(kx - 15, ky - 15, 30, 30, 90, goc);
            chu(g, String.valueOf((con + 999) / 1000), kx, ky - 4, true);
        } else if ((t / 250) % 2 == 0) {
            g.setColor(0xFFF07A);
            g.drawArc(kx - 18, ky - 18, 36, 36, 0, 360);
        }
        // ---- bang chu lon
        if (bang != null) {
            int e = (int) (t - bangLuc);
            if (e > 2400) {
                bang = null;
            } else {
                int y = 150 - (e < 200 ? (200 - e) / 10 : 0);
                ve(g, "h_bang", cx - 120, y - 14, 20);
                if (bangAnh != null && h(bangAnh) != null) {
                    ve(g, bangAnh, cx - 120, y - 12, 20);
                } else if (bang.length() > 0) {
                    main.a.R.a(g, bang, cx, y - 10, 2);
                }
                if (bang2 != null && bang2.length() > 0) {
                    main.a.L.a(g, bang2, cx, y + 6, 2);
                }
            }
        }
        // ---- thong bao nho (toi da 2 dong) - duoi hang chip tien, khong che tran dau
        int yy = chY + 16 + 15;
        while (tinNho.size() > 2) {
            tinNho.removeElementAt(0);
            tinLuc.removeElementAt(0);
        }
        for (int i = tinNho.size() - 1; i >= 0; i--) {
            long[] lt = (long[]) tinLuc.elementAt(i);
            int e = (int) (t - lt[0]);
            if (e > 2200) {
                tinNho.removeElementAt(i);
                tinLuc.removeElementAt(i);
                continue;
            }
            String s2 = (String) tinNho.elementAt(i);
            int ww = main.a.L.a(s2) + 12;
            g.setColor(0x180C28);
            g.fillRoundRect(cx - ww / 2, yy - 2, ww, 14, 8, 8);
            main.a.L.a(g, s2, cx, yy, 2);
            yy -= 15;
        }
        if (bangNang) {
            veBangNang(g, W, H);
        }
    }

    /** chu so trang (h_so.png: 0123456789./,+-kKtrỷ%) */
    static final String KY = "0123456789./,+-kKtrỷ%";

    static void chu(Graphics g, String s, int x, int y, boolean giua) {
        Image im = h("h_so");
        if (im == null) {
            main.a.L.a(g, s, x, y, giua ? 2 : 0);
            return;
        }
        int buoc = 5, n = s.length();
        int w = 0;
        for (int i = 0; i < n; i++) {
            w += s.charAt(i) == ' ' ? 3 : buoc;
        }
        int xx = giua ? x - w / 2 : x;
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c == ' ') {
                xx += 3;
                continue;
            }
            int k = KY.indexOf(c);
            if (k >= 0) {
                g.drawRegion(im, k * 6, 0, 6, 8, 0, xx, y, 20);
            }
            xx += buoc;
        }
    }

    static void chip(Graphics g, int x, int y, String icon, String v) {
        ve(g, "h_chip", x, y, 20);
        ve(g, icon, x + 4, y + 2, 20);
        chu(g, v, x + 16, y + 3, false);
    }

    static void veBangNang(Graphics g, int W, int H) {
        bnW = 228;
        bnH = 206;
        bnX = (W - bnW) / 2;
        bnY = Math.max(50, (H - SOFT - PANEL - bnH) / 2 + 30);
        ve(g, "h_khung", bnX, bnY, 20);
        int lv = capS[sung];
        main.a.L.a(g, tenSung[sung] + "  Lv" + lv, W / 2, bnY + 7, 2);
        main.a.L.a(g, tenCap[Math.max(0, Math.min(9, lv - 1))] + " - " + tenChieu[sung], W / 2, bnY + 20, 2);
        Image im = lon(sung, lv, khungVay(0));
        if (im != null) {
            g.drawImage(im, W / 2, bnY + 84, 3);
        }
        int ty = bnY + 140;
        if (lv >= 10) {
            main.a.L.a(g, "Súng đã đạt cấp tối đa!", W / 2, ty + 6, 2);
        } else {
            main.a.L.a(g, "Lên Lv" + (lv + 1) + " " + tenCap[lv] + ": " + nangL[sung] + " lượng + " + nangD[sung] + " đá", W / 2, ty, 2);
            main.a.L.a(g, "Tỉ lệ thành công " + nangTL[sung] + "% - có " + dns + " đá", W / 2, ty + 14, 2);
        }
        ve(g, lv >= 10 ? "h_dong" : "h_nangbtn", bnX + 10, bnY + bnH - 32, 20);
        ve(g, "h_dong", bnX + bnW - 110, bnY + bnH - 32, 20);
    }

    static String so(long v) {
        if (v < 0) {
            return "-" + so(-v);
        }
        String s = String.valueOf(v);
        StringBuffer b = new StringBuffer();
        int n = s.length();
        for (int i = 0; i < n; i++) {
            b.append(s.charAt(i));
            if ((n - i - 1) % 3 == 0 && i < n - 1) {
                b.append('.');
            }
        }
        return b.toString();
    }

    /** so gon cho dong tien: 8,4 tỷ / 12,5tr / 950k */
    static String gon(long v) {
        if (v >= 1000000000L) {
            return (v / 1000000000L) + "," + (v / 100000000L % 10) + " tỷ";
        }
        if (v >= 1000000L) {
            return (v / 1000000L) + "," + (v / 100000L % 10) + "tr";
        }
        if (v >= 10000L) {
            return (v / 1000L) + "k";
        }
        return so(v);
    }

    static String soGon(int v) {
        if (v >= 1000000) {
            return (v / 1000000) + "," + (v / 100000 % 10) + "tr";
        }
        if (v >= 10000) {
            return (v / 1000) + "k";
        }
        return so(v);
    }

    static String phanLuong(int milli) {
        if (milli >= 1000) {
            return (milli / 1000) + "," + (milli / 100 % 10) + "L";
        }
        return "0," + (milli / 100) + (milli % 100 / 10 > 0 ? String.valueOf(milli % 100 / 10) : "") + "L";
    }

    // ================================================================ cham (KeyTap.g)
    public static boolean cham(int x, int y) {
        if (!bat() || !coTT || dangMoMenu()) {
            return false;
        }
        int W = main.a.m;
        if (bangNang) {
            if (y >= bnY + bnH - 32 && y < bnY + bnH - 8) {
                if (x >= bnX + 10 && x < bnX + 110) {
                    if (capS[sung] < 10) {
                        gui(2, sung, 0, 0);
                    } else {
                        bangNang = false;
                    }
                } else if (x >= bnX + bnW - 110) {
                    bangNang = false;
                }
            } else if (x < bnX || x > bnX + bnW || y < bnY || y > bnY + bnH) {
                bangNang = false;
            }
            return true;
        }
        int kx = W - 24, ky = pY - 26;
        if ((x - kx) * (x - kx) + (y - ky) * (y - ky) <= 20 * 20) {
            if (muc >= 0 && now() - chieuLuc >= chieuCd) {
                dungChieu(muc);
            }
            return true;
        }
        if (y >= pY + 4 && y < pY + 4 + THE_H) {
            int i = (x - the0x) / (THE_W + 4);
            if (i >= 0 && i < 4) {
                if (i != sung) {
                    sung = i;
                    banner(null, tenSung[i] + " Lv" + capS[i], MAU[i]);
                    bangAnh = "h_b_doi";
                    bang = "";
                }
                ghiTuyChon();
            }
            return true;
        }
        if (y >= nutY && y < nutY + 26) {
            if (x < the0x + 92) {
                tuDanh = !tuDanh;
                banner(null, tuDanh ? "Tự chạy tới boss, tự bắn, tự dùng chiêu" : "Chạm boss để bắn, chạm nút tròn để tung chiêu", 0xFFFFFF);
                bangAnh = tuDanh ? "h_b_tdbat" : "h_b_tdtat";
                bang = "";
            } else if (x < the0x + 146) {
                tien = 1 - tien;
                banner(null, tien == 0 ? "Mỗi phát trừ xu" : "Mỗi phát trừ lượng, sát thương cao hơn", 0xFFFFFF);
                bangAnh = tien == 0 ? "h_b_banxu" : "h_b_banluong";
                bang = "";
            } else {
                bangNang = true;
            }
            ghiTuyChon();
            return true;
        }
        if (y >= pY) {
            return true;                                   // nen bang duoi: khong cho di bo
        }
        // cham vao boss: chon muc tieu + ban 1 phat
        int wx = (x + Cam.x()) / Math.max(1, js.ay), wy = (y + Cam.y()) / Math.max(1, js.ay);
        for (int i = 0; i < nB; i++) {
            if (bChet[i] != 0) {
                continue;
            }
            hn b = ir.g(bId[i]);
            if (b == null) {
                continue;
            }
            if (wx > b.aw - 30 && wx < b.aw + 30 && wy > b.ax - BH * 2 + 20 && wy < b.ax + 8) {
                muc = i;
                if (now() - banLuc >= cdS[sung]) {
                    ban(i);
                }
                return true;
            }
        }
        return false;
    }
}
