/*
 * main.c - ban Windows "thuan" cua Army 2 Coffee: client J2ME da dich san sang ma may (khong gia lap).
 *
 *  - cua so Win32 + GDI: khung game 320 x 400 phong to (giu ti le, diem anh net), ban phim ao 4x2 ben duoi
 *    (bam chuot) giong ban iOS/Android
 *  - ban phim: mui ten = di chuyen/chon, Enter/Space = OK/ban, F1 = phim mem trai, F2/Esc = phim mem phai,
 *    F3 = LUC MAX, go chu/so truc tiep; chuot = cham; con lan = len/xuong
 *  - hop nhap chu: hop thoai Windows (Unicode, mat khau an)
 *  - am thanh: tron WAV (va MIDI da tong hop san) qua waveOut
 *  - du lieu (RMS), log.txt, crash.txt: %LOCALAPPDATA%\Army2Coffee
 *  - kiem thu tu dong (Wine/CI): Army2Coffee.exe --lenh <tep lenh> --ra <thu muc anh> [--du-lieu <thu muc>]
 */
#ifndef _WIN32_WINNT
#define _WIN32_WINNT 0x0601
#endif
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <windowsx.h>
#include <mmsystem.h>
#include <shellapi.h>
#include <shlobj.h>
#include <errno.h>
#include <io.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include "platform.h"
#include "pad_labels.h"

unsigned char *stbi_load_from_memory(const unsigned char *buffer, int len, int *x, int *y, int *comp, int req_comp);
void stbi_image_free(void *p);

#define WM_APP_FRAME (WM_APP + 1)
#define WM_APP_TEXTBOX (WM_APP + 2)
#define WM_APP_MSG (WM_APP + 3)

static int gW = 320, gH = 400;
static HINSTANCE gInst;
static HWND gWnd;
static uint32_t *gFront;
static CRITICAL_SECTION gFrontCs;
static volatile LONG gFramePosted;
static char gDataDir[1024], gJarPath[1024];
static const uint8_t *gJarMem;
static size_t gJarLen;
static RECT gCanvasRc, gPadRc;
static FILE *gLog;
static int gLogFd = -1;
static CRITICAL_SECTION gLogCs;
static int gTest;                 /* che do kiem thu (--lenh) */
static char gOutDir[1024];

/* ============================================================ tien ich */
static wchar_t *u8w(const char *s) {
    int n = MultiByteToWideChar(CP_UTF8, 0, s ? s : "", -1, 0, 0);
    wchar_t *w = (wchar_t *)malloc(sizeof(wchar_t) * (size_t)(n > 0 ? n : 1));
    if (n <= 0 || !MultiByteToWideChar(CP_UTF8, 0, s ? s : "", -1, w, n)) w[0] = 0;
    return w;
}

static char *wu8(const wchar_t *w) {
    int n = WideCharToMultiByte(CP_UTF8, 0, w, -1, 0, 0, 0, 0);
    char *s = (char *)malloc((size_t)(n > 0 ? n : 1));
    if (n <= 0 || !WideCharToMultiByte(CP_UTF8, 0, w, -1, s, n, 0, 0)) s[0] = 0;
    return s;
}

static void mkdir_u8(const char *p) {
    wchar_t *w = u8w(p);
    CreateDirectoryW(w, 0);
    free(w);
}

/* ============================================================ nhat ky */
static void log_open(void) {
    char p[1100];
    snprintf(p, sizeof p, "%s\\log.txt", gDataDir);
    wchar_t *w = u8w(p);
    WIN32_FILE_ATTRIBUTE_DATA fa;
    if (GetFileAttributesExW(w, GetFileExInfoStandard, &fa) && fa.nFileSizeLow > 2 * 1024 * 1024) {
        char o[1100];
        snprintf(o, sizeof o, "%s\\log_cu.txt", gDataDir);
        wchar_t *wo = u8w(o);
        MoveFileExW(w, wo, MOVEFILE_REPLACE_EXISTING);
        free(wo);
    }
    gLog = _wfopen(w, L"a");
    free(w);
    if (gLog) gLogFd = _fileno(gLog);
}

void plat_log(const char *s) {
    EnterCriticalSection(&gLogCs);
    if (gLog) {
        time_t now = time(0);
        struct tm tm;
        localtime_s(&tm, &now);
        fprintf(gLog, "%02d/%02d %02d:%02d:%02d %s\n", tm.tm_mday, tm.tm_mon + 1, tm.tm_hour, tm.tm_min, tm.tm_sec, s);
        fflush(gLog);
    }
    LeaveCriticalSection(&gLogCs);
    if (gTest) {
        fprintf(stderr, "%s\n", s);
        fflush(stderr);
    }
    OutputDebugStringA(s);
    OutputDebugStringA("\n");
}

/* khong khoa (dung trong GC / khi sap) */
void plat_log_raw(const char *s) {
    if (gLogFd >= 0) {
        _write(gLogFd, s, (unsigned)strlen(s));
        _write(gLogFd, "\n", 1);
    }
    if (gTest) {
        fputs(s, stderr);
        fputc('\n', stderr);
    }
}

static LONG WINAPI on_crash(EXCEPTION_POINTERS *ep) {
    char p[1100];
    snprintf(p, sizeof p, "%s\\crash.txt", gDataDir);
    wchar_t *w = u8w(p);
    FILE *f = _wfopen(w, L"a");
    free(w);
    uintptr_t base = (uintptr_t)GetModuleHandleW(0);
    uintptr_t addr = (uintptr_t)ep->ExceptionRecord->ExceptionAddress;
    char b[512];
    snprintf(b, sizeof b, "=== SAP: ma 0x%08lx dia chi %p (exe+0x%llx) luc %ld", (unsigned long)ep->ExceptionRecord->ExceptionCode,
             (void *)addr, (unsigned long long)(addr - base), (long)time(0));
    if (f) {
        fprintf(f, "%s\n", b);
#ifdef _M_X64
        CONTEXT *c = ep->ContextRecord;
        fprintf(f, "rip=%llx rsp=%llx rbp=%llx\n", (unsigned long long)c->Rip, (unsigned long long)c->Rsp, (unsigned long long)c->Rbp);
#endif
        fclose(f);
    }
    plat_log_raw(b);
    return EXCEPTION_CONTINUE_SEARCH;
}

/* ============================================================ duong dan */
const char *plat_data_dir(void) { return gDataDir; }
const char *plat_jar_path(void) { return gJarPath[0] ? gJarPath : NULL; }
const uint8_t *plat_jar_mem(size_t *len) {
    if (!gJarMem) return NULL;
    *len = gJarLen;
    return gJarMem;
}

static void find_paths(void) {
    wchar_t exe[MAX_PATH];
    GetModuleFileNameW(0, exe, MAX_PATH);
    wchar_t *sl = wcsrchr(exe, L'\\');
    if (sl) *sl = 0;
    if (!gDataDir[0]) {
        wchar_t base[MAX_PATH];
        if (SUCCEEDED(SHGetFolderPathW(0, CSIDL_LOCAL_APPDATA | CSIDL_FLAG_CREATE, 0, 0, base))) {
            wchar_t d[MAX_PATH + 32];
            _snwprintf(d, MAX_PATH + 31, L"%ls\\Army2Coffee", base);
            d[MAX_PATH + 31] = 0;
            CreateDirectoryW(d, 0);
            char *u = wu8(d);
            snprintf(gDataDir, sizeof gDataDir, "%s", u);
            free(u);
        } else {
            wchar_t d[MAX_PATH + 32];
            _snwprintf(d, MAX_PATH + 31, L"%ls\\du_lieu", exe);
            d[MAX_PATH + 31] = 0;
            CreateDirectoryW(d, 0);
            char *u = wu8(d);
            snprintf(gDataDir, sizeof gDataDir, "%s", u);
            free(u);
        }
    }
    mkdir_u8(gDataDir);
    /* jar trong exe (tai nguyen RCDATA 101); khong co thi tim Army2Coffee.jar canh exe */
    HRSRC r = FindResourceW(0, MAKEINTRESOURCEW(101), (LPCWSTR)RT_RCDATA);
    if (r) {
        HGLOBAL g = LoadResource(0, r);
        if (g) {
            gJarMem = (const uint8_t *)LockResource(g);
            gJarLen = SizeofResource(0, r);
        }
    }
    if (!gJarMem) {
        char *u = wu8(exe);
        snprintf(gJarPath, sizeof gJarPath, "%s\\Army2Coffee.jar", u);
        free(u);
    }
}

/* ============================================================ khung hinh */
void plat_present(const uint32_t *fb, int w, int h) {
    EnterCriticalSection(&gFrontCs);
    if (!gFront) gFront = (uint32_t *)malloc((size_t)w * h * 4);
    memcpy(gFront, fb, (size_t)w * h * 4);
    LeaveCriticalSection(&gFrontCs);
    if (gWnd && InterlockedExchange(&gFramePosted, 1) == 0) PostMessageW(gWnd, WM_APP_FRAME, 0, 0);
}

/* ============================================================ ban phim ao 4x2 */
static const int kCodes[8] = {-6, -1, -40, -7, -3, -2, -4, -5};
static const int kDir[8] = {0, 1, 0, 0, 1, 1, 1, 0};
typedef struct lab {
    int w, h;
    uint32_t *px;    /* BGRA da tron tren nen trang */
    uint32_t *pxp;   /* ... tren nen dang bam */
} lab;
static lab gLab[8];
static int gCellCount[8], gKeyOwner[8];
static int gPadCell = -1;          /* o dang bam bang chuot */
static int gCanvasDown;            /* dang keo tren khung game */
static const COLORREF kPadBg = RGB(255, 255, 255), kPadPressed = RGB(204, 232, 255), kPadLine = RGB(161, 207, 237);

static void lab_make(int i, const uint8_t *png, int len) {
    int w, h, c;
    unsigned char *d = stbi_load_from_memory(png, len, &w, &h, &c, 4);
    if (!d) return;
    lab *l = &gLab[i];
    l->w = w;
    l->h = h;
    l->px = (uint32_t *)malloc((size_t)w * h * 4);
    l->pxp = (uint32_t *)malloc((size_t)w * h * 4);
    /* tron san tren nen trang (o thuong) va nen xanh nhat (o dang bam) */
    static const int bgc[2][3] = {{255, 255, 255}, {204, 232, 255}};
    for (int k = 0; k < w * h; k++) {
        int r = d[k * 4], g = d[k * 4 + 1], b = d[k * 4 + 2], a = d[k * 4 + 3];
        for (int v = 0; v < 2; v++) {
            int R = (r * a + bgc[v][0] * (255 - a)) / 255;
            int G = (g * a + bgc[v][1] * (255 - a)) / 255;
            int B = (b * a + bgc[v][2] * (255 - a)) / 255;
            (v ? l->pxp : l->px)[k] = 0xFF000000u | ((uint32_t)R << 16) | ((uint32_t)G << 8) | (uint32_t)B;
        }
    }
    stbi_image_free(d);
}

static void key_down(int i) {
    if (gKeyOwner[i]++ == 0) j2n_post(J2N_KEY_PRESS, kCodes[i], 0);
}

static void key_up(int i) {
    if (gKeyOwner[i] <= 0) return;
    if (--gKeyOwner[i] == 0) j2n_post(J2N_KEY_RELEASE, kCodes[i], 0);
}

static void cell_press(int i) {
    gCellCount[i]++;
    key_down(i);
    InvalidateRect(gWnd, &gPadRc, FALSE);
}

static void cell_release(int i) {
    if (gCellCount[i] <= 0) return;
    gCellCount[i]--;
    key_up(i);
    InvalidateRect(gWnd, &gPadRc, FALSE);
}

static int cell_at(int x, int y) {
    if (x < gPadRc.left || x >= gPadRc.right || y < gPadRc.top || y >= gPadRc.bottom) return -1;
    int cw = gPadRc.right - gPadRc.left, ch = gPadRc.bottom - gPadRc.top;
    int c = (x - gPadRc.left) * 4 / (cw > 0 ? cw : 1), r = (y - gPadRc.top) * 2 / (ch > 0 ? ch : 1);
    if (c > 3) c = 3;
    if (r > 1) r = 1;
    return r * 4 + c;
}

static void draw_chevron(HDC dc, int cx, int cy, int s, int dir) {
    /* dir: 0 len, 1 xuong, 2 trai, 3 phai */
    POINT p[3];
    if (dir == 0) {
        p[0].x = cx - s; p[0].y = cy + s / 2; p[1].x = cx; p[1].y = cy - s / 2; p[2].x = cx + s; p[2].y = cy + s / 2;
    } else if (dir == 1) {
        p[0].x = cx - s; p[0].y = cy - s / 2; p[1].x = cx; p[1].y = cy + s / 2; p[2].x = cx + s; p[2].y = cy - s / 2;
    } else if (dir == 2) {
        p[0].x = cx + s / 2; p[0].y = cy - s; p[1].x = cx - s / 2; p[1].y = cy; p[2].x = cx + s / 2; p[2].y = cy + s;
    } else {
        p[0].x = cx - s / 2; p[0].y = cy - s; p[1].x = cx + s / 2; p[1].y = cy; p[2].x = cx - s / 2; p[2].y = cy + s;
    }
    Polyline(dc, p, 3);
}

static void draw_pad(HDC dc) {
    int cw = gPadRc.right - gPadRc.left, ch = gPadRc.bottom - gPadRc.top;
    if (cw <= 0 || ch <= 0) return;
    HPEN line = CreatePen(PS_SOLID, 1, kPadLine);
    int arrowW = ch / 30 > 1 ? ch / 30 : 2;
    HPEN arrow = CreatePen(PS_SOLID, arrowW, RGB(69, 77, 92));
    for (int i = 0; i < 8; i++) {
        int c = i % 4, r = i / 4;
        RECT rc = {gPadRc.left + c * cw / 4, gPadRc.top + r * ch / 2, gPadRc.left + (c + 1) * cw / 4, gPadRc.top + (r + 1) * ch / 2};
        HBRUSH br = CreateSolidBrush(gCellCount[i] > 0 ? kPadPressed : kPadBg);
        FillRect(dc, &rc, br);
        DeleteObject(br);
        HGDIOBJ op = SelectObject(dc, line);
        MoveToEx(dc, rc.left, rc.top, 0);
        LineTo(dc, rc.right, rc.top);
        MoveToEx(dc, rc.left, rc.top, 0);
        LineTo(dc, rc.left, rc.bottom);
        SelectObject(dc, op);
        int mx = (rc.left + rc.right) / 2, my = (rc.top + rc.bottom) / 2;
        int bw = rc.right - rc.left;
        if (kDir[i]) {
            int s = (rc.bottom - rc.top) / 7;
            op = SelectObject(dc, arrow);
            draw_chevron(dc, mx, my, s, i == 1 ? 0 : i == 5 ? 1 : i == 4 ? 2 : 3);
            SelectObject(dc, op);
        } else if (gLab[i].px) {
            lab *l = &gLab[i];
            /* cung mot ti le cho moi nhan (theo nhan rong nhat "LUC MAX"), giong ban iOS */
            int ww = 1, hh = 1;
            for (int j = 0; j < 8; j++) {
                if (gLab[j].w > ww) ww = gLab[j].w;
                if (gLab[j].h > hh) hh = gLab[j].h;
            }
            double f = bw * 0.8 / ww, fh = (rc.bottom - rc.top) * 0.55 / hh;
            if (fh < f) f = fh;
            if (f >= 2.0) f = (double)(int)f;   /* nguyen lan cho diem anh deu */
            if (f < 1.0) f = 1.0;
            int dw = (int)(l->w * f + 0.5), dh = (int)(l->h * f + 0.5);
            BITMAPINFO bi;
            memset(&bi, 0, sizeof bi);
            bi.bmiHeader.biSize = sizeof bi.bmiHeader;
            bi.bmiHeader.biWidth = l->w;
            bi.bmiHeader.biHeight = -l->h;
            bi.bmiHeader.biPlanes = 1;
            bi.bmiHeader.biBitCount = 32;
            bi.bmiHeader.biCompression = BI_RGB;
            SetStretchBltMode(dc, COLORONCOLOR);
            StretchDIBits(dc, mx - dw / 2, my - dh / 2, dw, dh, 0, 0, l->w, l->h, gCellCount[i] > 0 ? l->pxp : l->px, &bi,
                          DIB_RGB_COLORS, SRCCOPY);
        }
    }
    DeleteObject(line);
    DeleteObject(arrow);
}

/* ============================================================ bo cuc */
static void layout(int cw, int ch) {
    int ph = cw * 3 / 10;                 /* ban phim ao: 2 hang, o rong = 1/4, cao = 0,15 chieu rong */
    if (ph > ch * 35 / 100) ph = ch * 35 / 100;
    gPadRc.left = 0;
    gPadRc.right = cw;
    gPadRc.top = ch - ph;
    gPadRc.bottom = ch;
    int ah = ch - ph;
    int sw = cw, sh = sw * gH / gW;
    if (sh > ah) {
        sh = ah;
        sw = sh * gW / gH;
    }
    gCanvasRc.left = (cw - sw) / 2;
    gCanvasRc.top = (ah - sh) / 2;
    gCanvasRc.right = gCanvasRc.left + sw;
    gCanvasRc.bottom = gCanvasRc.top + sh;
}

static void paint(HWND h) {
    PAINTSTRUCT ps;
    HDC dc = BeginPaint(h, &ps);
    RECT cr;
    GetClientRect(h, &cr);
    HDC m = CreateCompatibleDC(dc);
    HBITMAP bm = CreateCompatibleBitmap(dc, cr.right > 0 ? cr.right : 1, cr.bottom > 0 ? cr.bottom : 1);
    HGDIOBJ ob = SelectObject(m, bm);
    HBRUSH bg = CreateSolidBrush(RGB(99, 204, 255));
    FillRect(m, &cr, bg);
    DeleteObject(bg);
    EnterCriticalSection(&gFrontCs);
    if (gFront) {
        BITMAPINFO bi;
        memset(&bi, 0, sizeof bi);
        bi.bmiHeader.biSize = sizeof bi.bmiHeader;
        bi.bmiHeader.biWidth = gW;
        bi.bmiHeader.biHeight = -gH;
        bi.bmiHeader.biPlanes = 1;
        bi.bmiHeader.biBitCount = 32;
        bi.bmiHeader.biCompression = BI_RGB;
        SetStretchBltMode(m, COLORONCOLOR);
        StretchDIBits(m, gCanvasRc.left, gCanvasRc.top, gCanvasRc.right - gCanvasRc.left, gCanvasRc.bottom - gCanvasRc.top, 0, 0,
                      gW, gH, gFront, &bi, DIB_RGB_COLORS, SRCCOPY);
    }
    LeaveCriticalSection(&gFrontCs);
    draw_pad(m);
    BitBlt(dc, ps.rcPaint.left, ps.rcPaint.top, ps.rcPaint.right - ps.rcPaint.left, ps.rcPaint.bottom - ps.rcPaint.top, m,
           ps.rcPaint.left, ps.rcPaint.top, SRCCOPY);
    SelectObject(m, ob);
    DeleteObject(bm);
    DeleteDC(m);
    EndPaint(h, &ps);
}

/* ============================================================ hop nhap chu */
typedef struct tbox {
    wchar_t *title, *text;
    int max, cons;
    wchar_t *result;
} tbox;

static int gTextActive;

static void put16(uint8_t **p, uint16_t v) { memcpy(*p, &v, 2); *p += 2; }
static void put32(uint8_t **p, uint32_t v) { memcpy(*p, &v, 4); *p += 4; }
static void putws(uint8_t **p, const wchar_t *s) {
    size_t n = wcslen(s) + 1;
    memcpy(*p, s, n * 2);
    *p += n * 2;
}
static void align4(uint8_t **p, uint8_t *base) {
    while ((size_t)(*p - base) & 3) *(*p)++ = 0;
}

static void dlg_item(uint8_t **p, uint8_t *base, DWORD style, short x, short y, short cx, short cy, WORD id, WORD cls,
                     const wchar_t *text) {
    align4(p, base);
    put32(p, style | WS_CHILD | WS_VISIBLE);
    put32(p, 0);
    put16(p, (uint16_t)x);
    put16(p, (uint16_t)y);
    put16(p, (uint16_t)cx);
    put16(p, (uint16_t)cy);
    put16(p, id);
    put16(p, 0xFFFF);
    put16(p, cls);
    putws(p, text);
    put16(p, 0);
}

static INT_PTR CALLBACK tbox_proc(HWND d, UINT msg, WPARAM wp, LPARAM lp) {
    tbox *t = (tbox *)GetWindowLongPtrW(d, DWLP_USER);
    switch (msg) {
    case WM_INITDIALOG: {
        t = (tbox *)lp;
        SetWindowLongPtrW(d, DWLP_USER, (LONG_PTR)t);
        HWND e = GetDlgItem(d, 100);
        if (t->max > 0) SendMessageW(e, EM_SETLIMITTEXT, (WPARAM)t->max, 0);
        SetWindowTextW(e, t->text);
        SendMessageW(e, EM_SETSEL, 0, -1);
        SetFocus(e);
        return FALSE;
    }
    case WM_COMMAND:
        if (LOWORD(wp) == IDOK) {
            HWND e = GetDlgItem(d, 100);
            int n = GetWindowTextLengthW(e);
            t->result = (wchar_t *)calloc((size_t)n + 1, sizeof(wchar_t));
            GetWindowTextW(e, t->result, n + 1);
            EndDialog(d, 1);
            return TRUE;
        }
        if (LOWORD(wp) == IDCANCEL) {
            EndDialog(d, 0);
            return TRUE;
        }
        break;
    }
    return FALSE;
}

static void show_tbox(tbox *t) {
    uint8_t *buf = (uint8_t *)calloc(1, 4096), *p = buf;
    int c = t->cons & 0xFFFF;
    DWORD es = ES_AUTOHSCROLL | WS_BORDER | WS_TABSTOP;
    if (t->cons & 0x10000) es |= ES_PASSWORD;
    if (c == 2) es |= ES_NUMBER;
    put32(&p, DS_MODALFRAME | DS_CENTER | DS_SETFONT | WS_POPUP | WS_CAPTION | WS_SYSMENU);
    put32(&p, 0);
    put16(&p, 4);                        /* so muc */
    put16(&p, 0);
    put16(&p, 0);
    put16(&p, 220);
    put16(&p, 74);
    put16(&p, 0);                        /* khong menu */
    put16(&p, 0);                        /* lop mac dinh */
    putws(&p, L"Army 2 Coffee");
    put16(&p, 10);
    putws(&p, L"Segoe UI");
    dlg_item(&p, buf, SS_LEFT, 8, 7, 204, 10, 101, 0x0082, t->title[0] ? t->title : L"Nhập:");
    dlg_item(&p, buf, es, 8, 20, 204, 14, 100, 0x0081, L"");
    dlg_item(&p, buf, BS_DEFPUSHBUTTON | WS_TABSTOP, 100, 46, 54, 18, IDOK, 0x0080, L"OK");
    dlg_item(&p, buf, BS_PUSHBUTTON | WS_TABSTOP, 158, 46, 54, 18, IDCANCEL, 0x0080, L"Hủy");
    INT_PTR r = DialogBoxIndirectParamW(gInst, (LPCDLGTEMPLATEW)buf, gWnd, tbox_proc, (LPARAM)t);
    free(buf);
    gTextActive = 0;
    if (r == 1 && t->result) {
        char *u = wu8(t->result);
        j2n_text_done(1, u);
        free(u);
    } else {
        j2n_text_done(0, NULL);
    }
    free(t->result);
    free(t->title);
    free(t->text);
    free(t);
}

void plat_show_textbox(const char *title, const char *text, int max, int cons) {
    char b[300];
    snprintf(b, sizeof b, "[app] hop nhap chu (kieu %d, toi da %d)", cons, max);
    plat_log(b);
    if (gTest) {
        fprintf(stderr, "[harness] TextBox '%s' text='%s' max=%d cons=0x%x\n", title ? title : "", text ? text : "", max, cons);
        return;                          /* kiem thu: lenh text:/textcancel tra loi */
    }
    tbox *t = (tbox *)calloc(1, sizeof *t);
    t->title = u8w(title);
    t->text = u8w(text);
    t->max = max;
    t->cons = cons;
    gTextActive = 1;
    PostMessageW(gWnd, WM_APP_TEXTBOX, 0, (LPARAM)t);
}

/* ============================================================ am thanh (tron nhieu WAV qua waveOut) */
#define SR 22050
#define NSND 64
#define NBUF 4
#define BUFN 1024
typedef struct snd {
    int16_t *pcm;
    int n, pos, loops, playing, vol, used;
} snd;
static snd gSnd[NSND];
static CRITICAL_SECTION gSndCs;
static HWAVEOUT gWo;
static WAVEHDR gHdr[NBUF];
static int16_t gBuf[NBUF][BUFN];
static HANDLE gWoEv;
static int gAudioOk;

static uint32_t le32(const uint8_t *p) { return p[0] | (p[1] << 8) | (p[2] << 16) | ((uint32_t)p[3] << 24); }
static uint16_t le16(const uint8_t *p) { return (uint16_t)(p[0] | (p[1] << 8)); }

/* WAV PCM 8/16 bit, mono/stereo, moi tan so -> 16 bit mono 22050 Hz */
static int16_t *wav_decode(const uint8_t *d, int n, int *outn) {
    if (n < 12 || memcmp(d, "RIFF", 4) || memcmp(d + 8, "WAVE", 4)) return 0;
    int fmt = 0, ch = 1, rate = 8000, bits = 8;
    const uint8_t *data = 0;
    int dlen = 0;
    int p = 12;
    while (p + 8 <= n) {
        uint32_t cl = le32(d + p + 4);
        if (!memcmp(d + p, "fmt ", 4) && p + 8 + 16 <= n) {
            fmt = le16(d + p + 8);
            ch = le16(d + p + 10);
            rate = (int)le32(d + p + 12);
            bits = le16(d + p + 22);
        } else if (!memcmp(d + p, "data", 4)) {
            data = d + p + 8;
            dlen = (int)cl;
            if (dlen > n - p - 8) dlen = n - p - 8;
        }
        p += 8 + (int)cl + (cl & 1);
    }
    if (fmt != 1 || !data || ch < 1 || rate < 1000 || (bits != 8 && bits != 16)) return 0;
    int bps = bits / 8 * ch;
    int frames = dlen / bps;
    if (frames <= 0) return 0;
    int on = (int)((long long)frames * SR / rate);
    if (on <= 0) on = 1;
    int16_t *out = (int16_t *)malloc(sizeof(int16_t) * (size_t)on);
    for (int i = 0; i < on; i++) {
        long long sp = (long long)i * rate * 256 / SR;
        int i0 = (int)(sp >> 8), fr = (int)(sp & 255);
        int s[2];
        for (int k = 0; k < 2; k++) {
            int fi = i0 + k;
            if (fi >= frames) fi = frames - 1;
            int acc = 0;
            for (int c = 0; c < ch; c++) {
                const uint8_t *q = data + fi * bps + c * (bits / 8);
                acc += bits == 8 ? ((int)q[0] - 128) << 8 : (int16_t)le16(q);
            }
            s[k] = acc / ch;
        }
        out[i] = (int16_t)((s[0] * (256 - fr) + s[1] * fr) >> 8);
    }
    *outn = on;
    return out;
}

static void mix(int16_t *o, int n) {
    EnterCriticalSection(&gSndCs);
    for (int i = 0; i < n; i++) {
        int acc = 0;
        for (int k = 0; k < NSND; k++) {
            snd *s = &gSnd[k];
            if (!s->used || !s->playing) continue;
            acc += s->pcm[s->pos] * s->vol / 100;
            if (++s->pos >= s->n) {
                s->pos = 0;
                if (s->loops > 0 && --s->loops == 0) s->playing = 0;
            }
        }
        o[i] = (int16_t)(acc > 32767 ? 32767 : acc < -32768 ? -32768 : acc);
    }
    LeaveCriticalSection(&gSndCs);
}

static DWORD WINAPI audio_thread(LPVOID a) {
    (void)a;
    SetThreadPriority(GetCurrentThread(), THREAD_PRIORITY_TIME_CRITICAL);
    for (int i = 0; i < NBUF; i++) {
        mix(gBuf[i], BUFN);
        waveOutWrite(gWo, &gHdr[i], sizeof gHdr[i]);
    }
    for (;;) {
        WaitForSingleObject(gWoEv, INFINITE);
        for (int i = 0; i < NBUF; i++) {
            if (gHdr[i].dwFlags & WHDR_DONE) {
                gHdr[i].dwFlags &= ~WHDR_DONE;
                mix(gBuf[i], BUFN);
                waveOutWrite(gWo, &gHdr[i], sizeof gHdr[i]);
            }
        }
    }
    return 0;
}

static ULONGLONG gAudioTry;

/* mo waveOut; that bai (may chua co thiet bi am thanh) thi thu lai sau moi 5 giay khi game phat tieng */
static void audio_init(void) {
    static int logged;
    gAudioTry = GetTickCount64();
    WAVEFORMATEX f;
    memset(&f, 0, sizeof f);
    f.wFormatTag = WAVE_FORMAT_PCM;
    f.nChannels = 1;
    f.nSamplesPerSec = SR;
    f.wBitsPerSample = 16;
    f.nBlockAlign = 2;
    f.nAvgBytesPerSec = SR * 2;
    if (!gWoEv) gWoEv = CreateEventW(0, FALSE, FALSE, 0);
    if (waveOutOpen(&gWo, WAVE_MAPPER, &f, (DWORD_PTR)gWoEv, 0, CALLBACK_EVENT) != MMSYSERR_NOERROR) {
        if (!logged++) plat_log("[am thanh] khong mo duoc waveOut (se thu lai)");
        return;
    }
    for (int i = 0; i < NBUF; i++) {
        memset(&gHdr[i], 0, sizeof gHdr[i]);
        gHdr[i].lpData = (LPSTR)gBuf[i];
        gHdr[i].dwBufferLength = sizeof gBuf[i];
        waveOutPrepareHeader(gWo, &gHdr[i], sizeof gHdr[i]);
    }
    gAudioOk = 1;
    if (logged) plat_log("[am thanh] da mo waveOut");
    CloseHandle(CreateThread(0, 0, audio_thread, 0, 0, 0));
}

int plat_sound_create(const uint8_t *data, int len, const char *type) {
    int n = 0;
    int16_t *pcm = wav_decode(data, len, &n);
    char b[200];
    snprintf(b, sizeof b, "[am thanh] %s %d byte: %s", type ? type : "?", len, pcm ? "ok" : "khong doc duoc");
    plat_log(b);
    if (!pcm) return -1;
    EnterCriticalSection(&gSndCs);
    int h = -1;
    for (int i = 0; i < NSND; i++) {
        if (!gSnd[i].used) {
            h = i;
            break;
        }
    }
    if (h >= 0) {
        snd *s = &gSnd[h];
        memset(s, 0, sizeof *s);
        s->pcm = pcm;
        s->n = n;
        s->vol = 100;
        s->used = 1;
    } else {
        free(pcm);
    }
    LeaveCriticalSection(&gSndCs);
    return h;
}

void plat_sound_start(int h, int loops) {
    if (h < 0 || h >= NSND) return;
    if (!gAudioOk && GetTickCount64() - gAudioTry > 5000) audio_init();   /* chi luong game goi */
    EnterCriticalSection(&gSndCs);
    snd *s = &gSnd[h];
    if (s->used) {
        s->pos = 0;
        s->loops = loops < 0 ? -1 : (loops < 1 ? 1 : loops);
        s->playing = 1;
    }
    LeaveCriticalSection(&gSndCs);
}

void plat_sound_stop(int h) {
    if (h < 0 || h >= NSND) return;
    EnterCriticalSection(&gSndCs);
    gSnd[h].playing = 0;
    LeaveCriticalSection(&gSndCs);
}

void plat_sound_close(int h) {
    if (h < 0 || h >= NSND) return;
    EnterCriticalSection(&gSndCs);
    snd *s = &gSnd[h];
    if (s->used) {
        free(s->pcm);
        memset(s, 0, sizeof *s);
    }
    LeaveCriticalSection(&gSndCs);
}

void plat_sound_volume(int h, int level) {
    if (h < 0 || h >= NSND) return;
    EnterCriticalSection(&gSndCs);
    gSnd[h].vol = level < 0 ? 0 : level > 100 ? 100 : level;
    LeaveCriticalSection(&gSndCs);
}

/* ============================================================ khac */
int plat_open_url(const char *url) {
    wchar_t *w = u8w(url);
    ShellExecuteW(0, L"open", w, 0, 0, SW_SHOWNORMAL);
    free(w);
    return 1;
}

int plat_sms(const char *number, const char *text) {
    char b[600];
    snprintf(b, sizeof b, "Soạn tin nhắn:\n\n%s\n\nGửi tới: %s", text ? text : "", number ? number : "");
    char *copy = strdup(b);
    PostMessageW(gWnd, WM_APP_MSG, 0, (LPARAM)copy);
    return 1;
}

void plat_vibrate(int ms) { (void)ms; }

void plat_exit(void) { ExitProcess(0); }

/* ============================================================ cua so */
static int vk_code(WPARAM vk) {
    switch (vk) {
    case VK_UP: return -1;
    case VK_DOWN: return -2;
    case VK_LEFT: return -3;
    case VK_RIGHT: return -4;
    case VK_RETURN: case VK_SPACE: return -5;
    case VK_F1: return -6;
    case VK_F2: case VK_ESCAPE: return -7;
    case VK_F3: return -40;
    case VK_BACK: case VK_DELETE: return -8;
    case VK_NUMPAD8: return -1;
    case VK_NUMPAD2: return -2;
    case VK_NUMPAD4: return -3;
    case VK_NUMPAD6: return -4;
    case VK_NUMPAD5: return -5;
    }
    return 0;
}

static int gHeld[256];

static void release_all(void) {
    for (int i = 0; i < 256; i++) {
        if (gHeld[i]) {
            gHeld[i] = 0;
            int c = vk_code((WPARAM)i);
            if (c) j2n_post(J2N_KEY_RELEASE, c, 0);
        }
    }
    for (int i = 0; i < 8; i++) {
        while (gCellCount[i] > 0) cell_release(i);
    }
    if (gCanvasDown) {
        gCanvasDown = 0;
        j2n_post(J2N_POINTER_RELEASE, 0, 0);
    }
}

static void canvas_pt(int x, int y, int *gx, int *gy) {
    int w = gCanvasRc.right - gCanvasRc.left, h = gCanvasRc.bottom - gCanvasRc.top;
    int px = w > 0 ? (x - gCanvasRc.left) * gW / w : 0, py = h > 0 ? (y - gCanvasRc.top) * gH / h : 0;
    *gx = px < 0 ? 0 : px >= gW ? gW - 1 : px;
    *gy = py < 0 ? 0 : py >= gH ? gH - 1 : py;
}

static LRESULT CALLBACK wnd_proc(HWND h, UINT msg, WPARAM wp, LPARAM lp) {
    switch (msg) {
    case WM_APP_FRAME:
        InterlockedExchange(&gFramePosted, 0);
        InvalidateRect(h, &gCanvasRc, FALSE);
        return 0;
    case WM_APP_TEXTBOX:
        release_all();
        show_tbox((tbox *)lp);
        return 0;
    case WM_APP_MSG: {
        wchar_t *w = u8w((char *)lp);
        MessageBoxW(h, w, L"Army 2 Coffee", MB_OK | MB_ICONINFORMATION);
        free(w);
        free((char *)lp);
        return 0;
    }
    case WM_SIZE:
        layout(LOWORD(lp), HIWORD(lp));
        InvalidateRect(h, 0, FALSE);
        return 0;
    case WM_ERASEBKGND:
        return 1;
    case WM_PAINT:
        paint(h);
        return 0;
    case WM_KEYDOWN:
    case WM_SYSKEYDOWN: {
        int c = vk_code(wp);
        if (wp == VK_F10) return 0;
        if (c && wp < 256) {
            if (!gHeld[wp]) {
                gHeld[wp] = 1;
                j2n_post(J2N_KEY_PRESS, c, 0);
            }
            return 0;
        }
        break;
    }
    case WM_KEYUP:
    case WM_SYSKEYUP: {
        int c = vk_code(wp);
        if (c && wp < 256) {
            if (gHeld[wp]) {
                gHeld[wp] = 0;
                j2n_post(J2N_KEY_RELEASE, c, 0);
            }
            return 0;
        }
        break;
    }
    case WM_CHAR:
        /* chu / so go truc tiep (o ten, o mat khau khi dang chon...) */
        if (wp > 32 && wp < 127) {
            j2n_post(J2N_KEY_PRESS, (int)wp, 0);
            j2n_post(J2N_KEY_RELEASE, (int)wp, 0);
        }
        return 0;
    case WM_LBUTTONDOWN: {
        int x = GET_X_LPARAM(lp), y = GET_Y_LPARAM(lp);
        SetCapture(h);
        int c = cell_at(x, y);
        if (c >= 0) {
            gPadCell = c;
            cell_press(c);
        } else if (x >= gCanvasRc.left && x < gCanvasRc.right && y >= gCanvasRc.top && y < gCanvasRc.bottom) {
            int gx, gy;
            canvas_pt(x, y, &gx, &gy);
            gCanvasDown = 1;
            j2n_post(J2N_POINTER_PRESS, gx, gy);
        }
        return 0;
    }
    case WM_MOUSEMOVE:
        if (wp & MK_LBUTTON) {
            int x = GET_X_LPARAM(lp), y = GET_Y_LPARAM(lp);
            if (gCanvasDown) {
                int gx, gy;
                canvas_pt(x, y, &gx, &gy);
                j2n_post(J2N_POINTER_DRAG, gx, gy);
            } else if (gPadCell >= 0) {
                int c = cell_at(x, y);
                if (c >= 0 && c != gPadCell && kDir[c] && kDir[gPadCell]) {   /* truot giua cac phim huong */
                    cell_release(gPadCell);
                    gPadCell = c;
                    cell_press(c);
                }
            }
        }
        return 0;
    case WM_LBUTTONUP: {
        ReleaseCapture();
        int x = GET_X_LPARAM(lp), y = GET_Y_LPARAM(lp);
        if (gCanvasDown) {
            int gx, gy;
            canvas_pt(x, y, &gx, &gy);
            gCanvasDown = 0;
            j2n_post(J2N_POINTER_RELEASE, gx, gy);
        }
        if (gPadCell >= 0) {
            cell_release(gPadCell);
            gPadCell = -1;
        }
        return 0;
    }
    case WM_MOUSEWHEEL: {
        int d = GET_WHEEL_DELTA_WPARAM(wp);
        int code = d > 0 ? -1 : -2;
        j2n_post(J2N_KEY_PRESS, code, 0);
        j2n_post(J2N_KEY_RELEASE, code, 0);
        return 0;
    }
    case WM_ACTIVATE:
        if (LOWORD(wp) == WA_INACTIVE) release_all();
        break;
    case WM_KILLFOCUS:
        release_all();
        break;
    case WM_DESTROY:
        PostQuitMessage(0);
        return 0;
    }
    return DefWindowProcW(h, msg, wp, lp);
}

/* ============================================================ kiem thu tu dong (--lenh) */
static void msleep(int ms) { Sleep((DWORD)(ms > 0 ? ms : 0)); }

static void shot(const char *name) {
    uint32_t *buf = (uint32_t *)malloc((size_t)gW * gH * 4);
    int f = j2n_snapshot(buf, gW, gH);
    char p[1200];
    snprintf(p, sizeof p, "%s/%s.ppm", gOutDir, name);
    FILE *o = fopen(p, "wb");
    if (o) {
        fprintf(o, "P6\n%d %d\n255\n", gW, gH);
        for (int i = 0; i < gW * gH; i++) {
            uint32_t c = buf[i];
            unsigned char rgb[3] = {(unsigned char)(c >> 16), (unsigned char)(c >> 8), (unsigned char)c};
            fwrite(rgb, 1, 3, o);
        }
        fclose(o);
    }
    free(buf);
    fprintf(stderr, "[harness] shot %s (khung %d)\n", name, f);
}

static void run_line(char *line) {
    size_t l = strlen(line);
    while (l && (line[l - 1] == '\n' || line[l - 1] == '\r')) line[--l] = 0;
    if (!l) return;
    if (!strncmp(line, "type:", 5)) {
        for (const char *c = line + 5; *c; c++) {
            j2n_post(J2N_KEY_PRESS, (unsigned char)*c, 0);
            msleep(60);
            j2n_post(J2N_KEY_RELEASE, (unsigned char)*c, 0);
            msleep(60);
        }
        return;
    }
    if (!strncmp(line, "text:", 5)) {
        j2n_text_done(1, line + 5);
        return;
    }
    if (!strcmp(line, "textcancel")) {
        j2n_text_done(0, 0);
        return;
    }
    char op[32], name[256];
    int a = 0, b = 0, c = 0, d = 0;
    if (sscanf(line, "shot %255s", name) == 1) {
        shot(name);
        return;
    }
    if (sscanf(line, "%31s %d %d %d %d", op, &a, &b, &c, &d) < 1) return;
    if (!strcmp(op, "key")) {
        j2n_post(J2N_KEY_PRESS, a, 0);
        msleep(80);
        j2n_post(J2N_KEY_RELEASE, a, 0);
    } else if (!strcmp(op, "down")) {
        j2n_post(J2N_KEY_PRESS, a, 0);
    } else if (!strcmp(op, "up")) {
        j2n_post(J2N_KEY_RELEASE, a, 0);
    } else if (!strcmp(op, "sleep")) {
        msleep(a);
    } else if (!strcmp(op, "tap")) {
        j2n_post(J2N_POINTER_PRESS, a, b);
        msleep(60);
        j2n_post(J2N_POINTER_RELEASE, a, b);
    } else if (!strcmp(op, "drag")) {
        j2n_post(J2N_POINTER_PRESS, a, b);
        for (int i = 1; i <= 10; i++) {
            msleep(20);
            j2n_post(J2N_POINTER_DRAG, a + (c - a) * i / 10, b + (d - b) * i / 10);
        }
        j2n_post(J2N_POINTER_RELEASE, c, d);
    } else if (!strcmp(op, "quit")) {
        fflush(stderr);
        ExitProcess(0);
    }
}

static char gCmdFile[1024];

static DWORD WINAPI test_thread(LPVOID a) {
    (void)a;
    /* doc tep lenh lien tuc (them dong moi = lenh moi), giong ban Linux .live */
    long pos = 0;
    char line[2048];
    for (;;) {
        FILE *f = fopen(gCmdFile, "rb");
        if (f) {
            fseek(f, pos, SEEK_SET);
            while (fgets(line, sizeof line, f)) {
                size_t l = strlen(line);
                if (l == 0 || line[l - 1] != '\n') break;   /* dong chua ghi xong */
                pos += (long)l;
                run_line(line);
            }
            fclose(f);
        }
        msleep(100);
    }
    return 0;
}

/* ============================================================ khoi dong */
static int pick_height(void) {
    return gH;
}

int WINAPI wWinMain(HINSTANCE inst, HINSTANCE prev, PWSTR cmd, int show) {
    (void)prev;
    (void)cmd;
    gInst = inst;
    InitializeCriticalSection(&gFrontCs);
    InitializeCriticalSection(&gLogCs);
    InitializeCriticalSection(&gSndCs);
    SetProcessDPIAware();
    int argc = 0;
    wchar_t **argv = CommandLineToArgvW(GetCommandLineW(), &argc);
    for (int i = 1; i + 1 < argc; i++) {
        char *v = wu8(argv[i + 1]);
        if (!wcscmp(argv[i], L"--lenh")) {
            snprintf(gCmdFile, sizeof gCmdFile, "%s", v);
            gTest = 1;
        } else if (!wcscmp(argv[i], L"--ra")) {
            snprintf(gOutDir, sizeof gOutDir, "%s", v);
        } else if (!wcscmp(argv[i], L"--du-lieu")) {
            snprintf(gDataDir, sizeof gDataDir, "%s", v);
        } else if (!wcscmp(argv[i], L"--cao")) {
            int hh = atoi(v);
            if (hh >= 300 && hh <= 700) gH = hh;
        }
        free(v);
    }
    if (!gOutDir[0]) snprintf(gOutDir, sizeof gOutDir, ".");
    find_paths();
    log_open();
    SetUnhandledExceptionFilter(on_crash);
    {
        char b[300];
        snprintf(b, sizeof b, "[app] khoi dong Army 2 Coffee (Windows), man %dx%d, jar %s", gW, gH,
                 gJarMem ? "trong exe" : gJarPath);
        plat_log(b);
    }
    lab_make(0, lab_f1, sizeof lab_f1);
    lab_make(2, lab_luc, sizeof lab_luc);
    lab_make(3, lab_f2, sizeof lab_f2);
    lab_make(7, lab_ok, sizeof lab_ok);
    audio_init();

    WNDCLASSEXW wc;
    memset(&wc, 0, sizeof wc);
    wc.cbSize = sizeof wc;
    wc.style = CS_HREDRAW | CS_VREDRAW;
    wc.lpfnWndProc = wnd_proc;
    wc.hInstance = inst;
    wc.hIcon = LoadIconW(inst, MAKEINTRESOURCEW(1));
    wc.hIconSm = wc.hIcon;
    wc.hCursor = LoadCursorW(0, (LPCWSTR)IDC_ARROW);
    wc.lpszClassName = L"Army2CoffeeWnd";
    RegisterClassExW(&wc);

    /* kich thuoc: khung 320 x gH + ban phim ao (0,3 chieu rong), phong to vua man hinh lam viec */
    gH = pick_height();
    RECT work;
    SystemParametersInfoW(SPI_GETWORKAREA, 0, &work, 0);
    int ww = work.right - work.left, wh = work.bottom - work.top;
    double s = 3.0;
    while (s > 1.0 && (gW * s > ww * 0.9 || (gH + gW * 0.3) * s + 60 > wh * 0.97)) s -= 0.25;
    int cw = (int)(gW * s), ch = (int)((gH + gW * 0.3) * s);
    RECT r = {0, 0, cw, ch};
    DWORD style = WS_OVERLAPPEDWINDOW;
    AdjustWindowRect(&r, style, FALSE);
    gWnd = CreateWindowExW(0, wc.lpszClassName, L"Army 2 Coffee", style, CW_USEDEFAULT, CW_USEDEFAULT, r.right - r.left,
                           r.bottom - r.top, 0, 0, inst, 0);
    layout(cw, ch);
    ShowWindow(gWnd, show);
    UpdateWindow(gWnd);

    if (!j2n_start("coreLG.TerrainMidlet", gW, gH)) {
        MessageBoxW(gWnd, L"Không khởi động được game.", L"Army 2 Coffee", MB_ICONERROR);
        return 1;
    }
    if (gTest) CloseHandle(CreateThread(0, 0, test_thread, 0, 0, 0));
    MSG m;
    while (GetMessageW(&m, 0, 0, 0) > 0) {
        TranslateMessage(&m);
        DispatchMessageW(&m);
    }
    ExitProcess(0);
    return 0;
}
