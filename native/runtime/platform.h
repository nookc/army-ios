/*
 * platform.h - giao dien giua runtime (C) va nen tang (iOS ObjC / Linux harness).
 *
 * Nen tang cai dat cac ham plat_*; runtime cung cap cac ham j2n_* cho nen tang goi (an toan da luong).
 */
#pragma once
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
/* am thanh: tra ve handle >= 0 hoac -1 */
int plat_sound_create(const uint8_t *data, int len, const char *type);
void plat_sound_start(int h, int loops);
void plat_sound_stop(int h);
void plat_sound_close(int h);
void plat_sound_volume(int h, int level);
void plat_log(const char *s);

/* ---------------- runtime cung cap ---------------- */
enum {
    J2N_KEY_PRESS = 1,
    J2N_KEY_RELEASE = 2,
    J2N_POINTER_PRESS = 3,
    J2N_POINTER_DRAG = 4,
    J2N_POINTER_RELEASE = 5,
    J2N_SIZE = 9,
    J2N_KEY_REPEAT = 10,
};
/* khoi dong game tren luong moi; w,h = kich thuoc Canvas */
int j2n_start(const char *midlet_class, int w, int h);
void j2n_post(int type, int a, int b);
void j2n_text_done(int ok, const char *utf8);
/* chup khung hinh hien tai (cho kiem thu): copy vao out (w*h) neu kich thuoc khop, tra ve so khung da ve */
int j2n_snapshot(uint32_t *out, int w, int h);
int j2n_frames(void);

#ifdef __cplusplus
}
#endif
