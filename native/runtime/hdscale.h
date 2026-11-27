/* hdscale.h - phong to anh cho may HD (xem hdscale.c). Diem anh: 0xAARRGGBB, alpha khong nhan truoc. */
#pragma once
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* pixel art -> N lan (2..4) theo luat xBRZ; dst co (w*N) x (h*N) */
void hd_xbrz(int N, const uint32_t *src, uint32_t *dst, int w, int h);
/* anh min -> N lan (Catmull-Rom) */
void hd_bicubic(int N, const uint32_t *src, uint32_t *dst, int w, int h);
/* doi co bat ky bang trung binh dien tich */
void hd_area(const uint32_t *src, int sw, int sh, uint32_t *dst, int dw, int dh);
/* 1 = anh x2 chi la anh x1 nhan doi tung diem */
void hd_lin(const uint32_t *src, int sw, int sh, uint32_t *dst, int dw, int dh);   /* noi suy 2 chieu, alpha nhan truoc */
int hd_doubled(const uint32_t *src, int w, int h);
/* 1 = anh "min" (khong phai pixel art) */
int hd_smooth(const uint32_t *src, int w, int h);
/* Tao anh he so K tu anh nguon he so s (w, h chia het cho s). smooth: 1 = bicubic, 0 = xBRZ, -1 = tu nhan dang.
 * Tra ve bo dem malloc (nguoi goi free) co *ow x *oh = (w/s*K) x (h/s*K); NULL neu loi. */
uint32_t *hd_make(const uint32_t *src, int w, int h, int s, int K, int smooth, int *ow, int *oh);

#ifdef __cplusplus
}
#endif
