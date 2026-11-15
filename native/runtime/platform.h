/*
 * platform.h - giao dien giua runtime (C) va nen tang (iOS ObjC / Linux harness).
 *
 * Nen tang cai dat cac ham plat_*; runtime cung cap cac ham j2n_* cho nen tang goi (an toan da luong).
 */
#pragma once
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ---------------- nen tang cai dat ---------------- */
/* khung hinh moi da ve xong: fb = 0xFFRRGGBB, w*h diem (runtime giu bo dem, nen tang copy hoac dung ngay trong ham) */
void plat_present(const uint32_t *fb, int w, int h);
/* hien hop nhap chu (TextBox); khi xong goi j2n_text_done(). constraints: MIDP TextField (2 = so, 0x10000 = mat khau) */
void plat_show_textbox(const char *title, const char *text, int max_size, int constraints);
int plat_open_url(const char *url);
int plat_sms(const char *number, const char *text);
void plat_vibrate(int ms);
void plat_exit(void);
/* thu muc ghi du lieu (RMS) - ton tai, ghi duoc */
const char *plat_data_dir(void);
/* duong dan tep jar cua game */
const char *plat_jar_path(void);
/* jar nam san trong bo nho (tai nguyen exe / asset apk); NULL = doc tep plat_jar_path() */
const uint8_t *plat_jar_mem(size_t *len);
/* am thanh: tra ve handle >= 0 hoac -1 */
int plat_sound_create(const uint8_t *data, int len, const char *type);
void plat_sound_start(int h, int loops);
void plat_sound_stop(int h);
void plat_sound_close(int h);
void plat_sound_volume(int h, int level);
void plat_log(const char *s);
/* ghi log khan cap: chi write(), khong khoa/khong cap phat (dung trong GC, tin hieu loi) */
void plat_log_raw(const char *s);

/* ---------------- runtime cung cap ---------------- */
enum {
    J2N_KEY_PRESS = 1,
    J2N_KEY_RELEASE = 2,
    J2N_POINTER_PRESS = 3,
    J2N_POINTER_DRAG = 4,
    J2N_POINTER_RELEASE = 5,
    J2N_SIZE = 9,
    J2N_KEY_REPEAT = 10,
    J2N_GC = 13, /* tu kiem thu: chay GC tren luong su kien Java */
};
/* khoi dong game tren luong moi; w,h = kich thuoc Canvas */
int j2n_start(const char *midlet_class, int w, int h);
/* nhu j2n_start nhung chay game ngay tren luong goi (khong tra ve khi game con chay) */
int j2n_run(const char *midlet_class, int w, int h);
void j2n_post(int type, int a, int b);
void j2n_text_done(int ok, const char *utf8);
/* v8h-31: 1 nhip quet man hinh (t_ns dong ho don dieu, vd Choreographer frameTimeNanos) - vong lap game ve khung theo nhip nay
   (khong goi = ngu theo dong ho nhu cu). j2n_vsync_mode: 0 tat, 1 khoa 30 khung/giay (mac dinh), 2 trung binh 30 ms khop nhip */
void j2n_vsync(int64_t t_ns);
extern int j2n_vsync_mode;
/* chup khung hinh hien tai (cho kiem thu): copy vao out (w*h) neu kich thuoc khop, tra ve so khung da ve */
int j2n_snapshot(uint32_t *out, int w, int h);
/* chup khung hien tai voi kich thuoc that (man phong to lon hon w x h): tra ve bo dem malloc (nguoi goi free) hoac NULL */
uint32_t *j2n_snapshot_any(int *w, int *h, int *frame_no);
/* Avatar: Canvas co ten lop trong 'names' (cach nhau dau phay, vd "FishGame") ve phong to 'scale' lan - goi truoc j2n_start */
void j2n_set_hd(const char *names, int scale);
/* Avatar: ket noi toi 127.0.0.1 / localhost duoc chuyen sang may chu nay: "host" (giu cong cua game) hoac "host:cong";
   NULL / "" = khong chuyen. Goi luc nao cung duoc (ap dung tu lan ket noi sau) */
void j2n_set_server(const char *spec);
int j2n_frames(void);
/* Avatar: nen tang dang ky ham bat/tat ban phim he thong khi game vao/ra o go chu (show 1/0, area_bottom: day vung game
   con dung duoc tinh bang diem anh Canvas, 0 = ca man). Khong dang ky = bo qua */
void j2n_set_keyboard_hook(void (*fn)(int show, int area_bottom));
/* so vung du lieu tinh GC da xet / da nhan lam goc (iOS: chi giu cua app) */
void rt_gc_roots_stats(unsigned *seen, unsigned *kept);

#ifdef __cplusplus
}
#endif
