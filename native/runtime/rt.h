/*
 * rt.h - runtime cho client J2ME dich san sang C (army2 native).
 *
 * Doi tuong:   [jclass *cls][void *mon][truong...]          (struct O_<lop> sinh trong classes.h)
 * Mang:        [jclass *cls][void *mon][int32 length][pad][du lieu...]
 * Ham Java:    R M_<lop>_<ten>_<hash>(jctx *t, [jobj self,] tham so...)
 * Ngoai le:    t->exc != 0 sau loi goi = co ngoai le dang bay.
 * Bo nho:      Boehm GC (bao thu, khong di chuyen doi tuong).
 */
#pragma once
#include <stdint.h>
#include <stddef.h>
#include <string.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct jclass jclass;
typedef struct jobject {
    jclass *cls;
    void *mon;
} jobject;
typedef jobject *jobj;

typedef struct jarray {
    jobject h;
    int32_t length;
    int32_t pad_;
} jarray;

#define AD(a, T) ((T *)((char *)(a) + sizeof(jarray)))
#define ALEN(a) (((jarray *)(a))->length)

typedef struct jctx {
    jobj exc;              /* ngoai le dang bay */
    jobj thread;           /* doi tuong java.lang.Thread */
    void *nt;              /* du lieu luong goc (rt.c) */
    int32_t id;
    int32_t depth;
} jctx;

typedef struct itab {
    const jclass *cls;
    int32_t sel;
    void *fn;
} itab;

enum {
    F_IFACE = 1,
    F_ABSTRACT = 2,
    F_FINAL = 4,
    F_ARRAY = 8,
    F_PRIM = 16,
};

struct jclass {
    const char *name;
    jclass *super;
    uint32_t flags;
    int32_t depth;
    jclass **anc;          /* anc[0..depth], anc[depth] == lop nay */
    int32_t isize;
    int32_t nifc;
    jclass **ifc;          /* moi interface lop cai dat (bac cau) */
    const itab *itab;
    int32_t nitab;
    void (*init)(jctx *);
    volatile int32_t istate;   /* 0 chua, 1 dang chay, 2 xong, 3 loi */
    void *ithread;
    void **vt;
    int32_t nvt;
    jclass *comp;          /* mang: lop phan tu */
    jclass *arr;           /* lop mang cua lop nay (dem) */
    int32_t esize;
    char etype;            /* mang: Z B C S I J F D L */
    jobj mirror;           /* doi tuong java.lang.Class */
    jobj (*newinst)(jctx *);
};

#define LIKELY(x) __builtin_expect(!!(x), 1)
#define UNLIKELY(x) __builtin_expect(!!(x), 0)

/* ---- kiem tra ---- */
#define CK(L) do { if (UNLIKELY(t->exc != 0)) goto L; } while (0)
#define NN(v, L) do { if (UNLIKELY((v) == 0)) { rt_npe(t); goto L; } } while (0)
#define AB(a, i, L) do { if (UNLIKELY((uint32_t)(i) >= (uint32_t)ALEN(a))) { rt_aioobe(t, (i)); goto L; } } while (0)
#define CI(K, L) do { if (UNLIKELY((K).istate != 2)) { rt_clinit(t, &(K)); CK(L); } } while (0)
#define ASTORE_CHK(a, v, L) do { if ((v) != 0 && UNLIKELY(!rt_store_ok((a), (v)))) { rt_ase(t); goto L; } } while (0)

/* ---- chuoi hang (sinh trong meta.c) ---- */
extern jobj rt_lits[];
extern const uint16_t *const rt_litdat[];
extern const int32_t rt_litlen[];
extern const int32_t rt_nlits;
jobj rt_lit(jctx *t, int32_t idx);
#define LIT(n) (LIKELY(rt_lits[n] != 0) ? rt_lits[n] : rt_lit(t, (n)))

extern jclass *const rt_all_classes[];
extern const int32_t rt_nclasses;

/* ---- lop mang nguyen thuy ---- */
extern jclass rt_arr_Z, rt_arr_B, rt_arr_C, rt_arr_S, rt_arr_I, rt_arr_J, rt_arr_F, rt_arr_D;

/* ---- cap phat ---- */
jobj rt_new(jctx *t, jclass *k);
jobj rt_newarr(jctx *t, jclass *ak, int32_t n);
jclass *rt_arrof(jctx *t, jclass *comp);
jobj rt_multianew(jctx *t, jclass *ak, int32_t nd, const int32_t *dims);
jobj rt_mirror(jctx *t, jclass *k);

/* ---- ngoai le ---- */
void rt_npe(jctx *t);
void rt_aioobe(jctx *t, int32_t i);
void rt_arith(jctx *t);
void rt_ase(jctx *t);
void rt_cce(jctx *t, jobj o, const jclass *k);
void rt_err(jctx *t, const char *cls, const char *msg);
void rt_throw(jctx *t, jclass *k, const char *msg);
void rt_abstract(jctx *t);
void rt_print_exc(jctx *t, jobj e, const char *where);

/* ---- lop ---- */
void rt_clinit(jctx *t, jclass *k);
int rt_isinst_slow(const jclass *c, const jclass *k);
const itab *rt_ilookup(jctx *t, jobj o, int32_t sel);
int rt_store_ok(jobj arr, jobj v);
jclass *rt_find_class(const char *name);

static inline int rt_sub(const jclass *c, const jclass *k) {
    return c->depth >= k->depth && c->anc[k->depth] == k;
}

static inline int rt_isinst(jobj o, const jclass *k) {
    const jclass *c = o->cls;
    if (c == k) return 1;
    if (!(k->flags & (F_IFACE | F_ARRAY))) return rt_sub(c, k);
    return rt_isinst_slow(c, k);
}

/* ---- monitor ---- */
void rt_menter(jctx *t, jobj o);
void rt_mexit(jctx *t, jobj o);
void rt_mexit_keep(jctx *t, jobj o);   /* nha khoa ma khong xoa ngoai le dang bay */
void rt_wait(jctx *t, jobj o, int64_t ms);
void rt_notify(jctx *t, jobj o, int all);

/* ---- so hoc Java ---- */
static inline float rt_fbits(uint32_t b) { float f; memcpy(&f, &b, 4); return f; }
static inline double rt_dbits(uint64_t b) { double d; memcpy(&d, &b, 8); return d; }
static inline int32_t rt_f2i(float f) {
    if (f != f) return 0;
    if (f >= 2147483648.0f) return INT32_MAX;
    if (f <= -2147483648.0f) return INT32_MIN;
    return (int32_t)f;
}
static inline int32_t rt_d2i(double d) {
    if (d != d) return 0;
    if (d >= 2147483647.0) return INT32_MAX;
    if (d <= -2147483648.0) return INT32_MIN;
    return (int32_t)d;
}
static inline int64_t rt_f2l(float f) {
    if (f != f) return 0;
    if (f >= 9223372036854775808.0f) return INT64_MAX;
    if (f <= -9223372036854775808.0f) return INT64_MIN;
    return (int64_t)f;
}
static inline int64_t rt_d2l(double d) {
    if (d != d) return 0;
    if (d >= 9223372036854775808.0) return INT64_MAX;
    if (d <= -9223372036854775808.0) return INT64_MIN;
    return (int64_t)d;
}

/* ---- tien ich cho native ---- */
jctx *rt_cur(void);
jobj rt_str_utf16(jctx *t, const uint16_t *s, int32_t n);
jobj rt_str_utf8(jctx *t, const char *s);
jobj rt_str_latin1(jctx *t, const char *s, int32_t n);
int32_t rt_str_len(jobj s);
const uint16_t *rt_str_chars(jobj s);              /* con tro vao mang char[] (khong copy) */
char *rt_str_cstr(jobj s);                         /* UTF-8, cap phat GC (atomic) */
void *rt_alloc_atomic(size_t n);
void *rt_alloc(size_t n);
void rt_gc(void);
int64_t rt_free_memory(void);
int64_t rt_total_memory(void);
int64_t rt_now_ms(void);

/* luong */
void rt_thread_start(jctx *t, jobj thread);
void rt_sleep(jctx *t, int64_t ms);
void rt_yield(void);
extern void (*rt_thread_run_hook)(jctx *t, jobj thread);  /* goi Thread.run() (dat trong boot) */
int rt_attach_thread(jctx *ctx);                     /* luong goc moi tham gia GC */

/* khoi dong */
void rt_init(void);
void rt_log(const char *fmt, ...);

#ifdef __cplusplus
}
#endif
