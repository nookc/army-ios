/*
 * rt.c - loi runtime: cap phat (Boehm GC), lop, ngoai le, monitor, luong, chuoi.
 */
#define GC_THREADS 1
#include "gc.h"
#include <pthread.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <errno.h>
#include <sched.h>
#include "classes.h"

#define STR(o) ((struct O_java_lang_String *)(o))
#define THR(o) ((struct O_java_lang_Throwable *)(o))

/* ============================================================ log */
#include "platform.h"
void rt_log(const char *fmt, ...) {
    char b[1024];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(b, sizeof b, fmt, ap);
    va_end(ap);
    plat_log(b); /* iOS: vao Documents/log.txt; Linux: stderr */
}

/* ============================================================ luong / ngu canh */
static __thread jctx *tl_ctx;
static pthread_mutex_t g_mu = PTHREAD_MUTEX_INITIALIZER;      /* lop mang, intern, mirror */
static pthread_mutex_t init_mu = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t init_cv = PTHREAD_COND_INITIALIZER;
static int32_t g_next_tid = 1;
void (*rt_thread_run_hook)(jctx *t, jobj thread);

jctx *rt_cur(void) { return tl_ctx; }

static jctx *new_ctx(jobj thread) {
    /* GC_MALLOC_UNCOLLECTABLE: la goc cho GC (giu exc / thread), tu giai phong khi luong ket thuc */
    jctx *c = (jctx *)GC_MALLOC_UNCOLLECTABLE(sizeof(jctx));
    memset(c, 0, sizeof *c);
    c->thread = thread;
    pthread_mutex_lock(&g_mu);
    c->id = g_next_tid++;
    pthread_mutex_unlock(&g_mu);
    return c;
}

/* ============================================================ cap phat */
static jobj g_oom;   /* OutOfMemoryError cap san */

void *rt_alloc(size_t n) { return GC_MALLOC(n); }
void *rt_alloc_atomic(size_t n) {
    void *p = GC_MALLOC_ATOMIC(n);
    if (p) memset(p, 0, n);
    return p;
}

static void oom(jctx *t) {
    if (t) t->exc = g_oom;
}

jobj rt_new(jctx *t, jclass *k) {
    if (UNLIKELY(k->flags & (F_IFACE | F_ABSTRACT))) {
        rt_err(t, "java/lang/InstantiationError", k->name);
        return 0;
    }
    jobj o = (jobj)GC_MALLOC((size_t)k->isize);
    if (UNLIKELY(!o)) {
        oom(t);
        return 0;
    }
    o->cls = k;
    return o;
}

jobj rt_newarr(jctx *t, jclass *ak, int32_t n) {
    if (UNLIKELY(n < 0)) {
        char b[32];
        snprintf(b, sizeof b, "%d", n);
        rt_err(t, "java/lang/NegativeArraySizeException", b);
        return 0;
    }
    size_t sz = sizeof(jarray) + (size_t)n * (size_t)ak->esize;
    jarray *a;
    if (ak->etype == 'L') {
        a = (jarray *)GC_MALLOC(sz);
    } else {
        a = (jarray *)GC_MALLOC_ATOMIC(sz);   /* khong dung IGNORE_OFF_PAGE: native giu con tro vao giua mang */
        if (a) memset(a, 0, sz);
    }
    if (UNLIKELY(!a)) {
        oom(t);
        return 0;
    }
    a->h.cls = ak;
    a->length = n;
    return (jobj)a;
}

#define PRIM_ARR(T, ch, sz) \
    static jclass *A_arr_##T[2]; \
    jclass rt_arr_##T = {.name = "[" #T, .flags = F_ARRAY | F_FINAL, .depth = 1, .anc = A_arr_##T, \
                         .istate = 2, .esize = sz, .etype = ch};
PRIM_ARR(Z, 'Z', 1)
PRIM_ARR(B, 'B', 1)
PRIM_ARR(C, 'C', 2)
PRIM_ARR(S, 'S', 2)
PRIM_ARR(I, 'I', 4)
PRIM_ARR(J, 'J', 8)
PRIM_ARR(F, 'F', 4)
PRIM_ARR(D, 'D', 8)

static void init_prim_arrays(void) {
    jclass *all[] = {&rt_arr_Z, &rt_arr_B, &rt_arr_C, &rt_arr_S, &rt_arr_I, &rt_arr_J, &rt_arr_F, &rt_arr_D};
    for (int i = 0; i < 8; i++) {
        jclass *a = all[i];
        a->super = &K_java_lang_Object;
        a->anc[0] = &K_java_lang_Object;
        a->anc[1] = a;
        a->vt = K_java_lang_Object.vt;
        a->nvt = K_java_lang_Object.nvt;
        a->isize = (int32_t)sizeof(jarray);
    }
}

jclass *rt_arrof(jctx *t, jclass *comp) {
    jclass *a = __atomic_load_n(&comp->arr, __ATOMIC_ACQUIRE);
    if (LIKELY(a != 0)) return a;
    pthread_mutex_lock(&g_mu);
    a = comp->arr;
    if (!a) {
        a = (jclass *)GC_MALLOC_UNCOLLECTABLE(sizeof(jclass));
        memset(a, 0, sizeof *a);
        size_t ln = strlen(comp->name);
        char *nm = (char *)GC_MALLOC_UNCOLLECTABLE(ln + 4);
        if (comp->flags & F_ARRAY) {
            nm[0] = '[';
            memcpy(nm + 1, comp->name, ln + 1);
        } else {
            nm[0] = '[';
            nm[1] = 'L';
            memcpy(nm + 2, comp->name, ln);
            nm[ln + 2] = ';';
            nm[ln + 3] = 0;
        }
        a->name = nm;
        a->super = &K_java_lang_Object;
        a->flags = F_ARRAY | F_FINAL;
        a->depth = 1;
        a->anc = (jclass **)GC_MALLOC_UNCOLLECTABLE(2 * sizeof(jclass *));
        a->anc[0] = &K_java_lang_Object;
        a->anc[1] = a;
        a->istate = 2;
        a->vt = K_java_lang_Object.vt;
        a->nvt = K_java_lang_Object.nvt;
        a->comp = comp;
        a->esize = (int32_t)sizeof(jobj);
        a->etype = 'L';
        a->isize = (int32_t)sizeof(jarray);
        __atomic_store_n(&comp->arr, a, __ATOMIC_RELEASE);
    }
    pthread_mutex_unlock(&g_mu);
    (void)t;
    return a;
}

static jobj multi(jctx *t, jclass *ak, int32_t nd, const int32_t *dims) {
    jobj a = rt_newarr(t, ak, dims[0]);
    if (!a || nd == 1) return a;
    for (int32_t i = 0; i < dims[0]; i++) {
        jobj sub = multi(t, ak->comp, nd - 1, dims + 1);
        if (t->exc) return 0;
        AD(a, jobj)[i] = sub;
    }
    return a;
}

jobj rt_multianew(jctx *t, jclass *ak, int32_t nd, const int32_t *dims) {
    for (int32_t i = 0; i < nd; i++) {
        if (dims[i] < 0) {
            rt_err(t, "java/lang/NegativeArraySizeException", "");
            return 0;
        }
    }
    return multi(t, ak, nd, dims);
}

/* ============================================================ lop */
jclass *rt_find_class(const char *name) {
    for (int32_t i = 0; i < rt_nclasses; i++) {
        if (strcmp(rt_all_classes[i]->name, name) == 0) return rt_all_classes[i];
    }
    if (name[0] == '[') {
        const char *c = name + 1;
        jclass *comp = 0;
        switch (*c) {
        case 'Z': if (!c[1]) return &rt_arr_Z; break;
        case 'B': if (!c[1]) return &rt_arr_B; break;
        case 'C': if (!c[1]) return &rt_arr_C; break;
        case 'S': if (!c[1]) return &rt_arr_S; break;
        case 'I': if (!c[1]) return &rt_arr_I; break;
        case 'J': if (!c[1]) return &rt_arr_J; break;
        case 'F': if (!c[1]) return &rt_arr_F; break;
        case 'D': if (!c[1]) return &rt_arr_D; break;
        case '[': comp = rt_find_class(c); break;
        case 'L': {
            size_t n = strlen(c);
            if (n < 3 || c[n - 1] != ';') return 0;
            char buf[512];
            if (n - 2 >= sizeof buf) return 0;
            memcpy(buf, c + 1, n - 2);
            buf[n - 2] = 0;
            comp = rt_find_class(buf);
            break;
        }
        }
        if (comp) return rt_arrof(rt_cur(), comp);
    }
    return 0;
}

int rt_isinst_slow(const jclass *c, const jclass *k) {
    if (c == k) return 1;
    if (k->flags & F_IFACE) {
        for (int32_t i = 0; i < c->nifc; i++) {
            if (c->ifc[i] == k) return 1;
        }
        return 0;
    }
    if (k->flags & F_ARRAY) {
        if (!(c->flags & F_ARRAY)) return 0;
        if (k->etype != 'L' || c->etype != 'L') return k == c;
        const jclass *cc = c->comp, *kc = k->comp;
        if (cc == kc) return 1;
        if (kc == &K_java_lang_Object) return 1;   /* moi mang tham chieu (ke ca mang nguyen thuy long nhau) */
        if (cc->flags & F_ARRAY) {
            return (kc->flags & F_ARRAY) ? rt_isinst_slow(cc, kc) : 0;
        }
        if (kc->flags & (F_IFACE | F_ARRAY)) return rt_isinst_slow(cc, kc);
        return rt_sub(cc, kc);
    }
    return rt_sub(c, k);
}

int rt_store_ok(jobj arr, jobj v) {
    jclass *comp = arr->cls->comp;
    if (!comp || comp == &K_java_lang_Object) return 1;
    return rt_isinst(v, comp);
}

const itab *rt_ilookup(jctx *t, jobj o, int32_t sel) {
    const jclass *c = o->cls;
    if (c->flags & F_ARRAY) c = &K_java_lang_Object;
    int32_t lo = 0, hi = c->nitab - 1;
    const itab *tb = c->itab;
    while (lo <= hi) {
        int32_t mid = (lo + hi) >> 1;
        if (tb[mid].sel == sel) return &tb[mid];
        if (tb[mid].sel < sel) lo = mid + 1;
        else hi = mid - 1;
    }
    char b[256];
    snprintf(b, sizeof b, "%s (selector %d)", c->name, sel);
    rt_err(t, "java/lang/IncompatibleClassChangeError", b);
    return 0;
}

void rt_clinit(jctx *t, jclass *k) {
    if (__atomic_load_n(&k->istate, __ATOMIC_ACQUIRE) == 2) return;
    pthread_mutex_lock(&init_mu);
    for (;;) {
        int32_t s = k->istate;
        if (s == 2) {
            pthread_mutex_unlock(&init_mu);
            return;
        }
        if (s == 3) {
            pthread_mutex_unlock(&init_mu);
            rt_err(t, "java/lang/NoClassDefFoundError", k->name);
            return;
        }
        if (s == 1) {
            if (k->ithread == (void *)t) {
                pthread_mutex_unlock(&init_mu);
                return;
            }
            pthread_cond_wait(&init_cv, &init_mu);
            continue;
        }
        break;
    }
    k->istate = 1;
    k->ithread = t;
    pthread_mutex_unlock(&init_mu);
    if (k->super && !(k->flags & F_IFACE)) {
        rt_clinit(t, k->super);
    }
    if (!t->exc && k->init) {
        k->init(t);
    }
    pthread_mutex_lock(&init_mu);
    __atomic_store_n(&k->istate, t->exc ? 3 : 2, __ATOMIC_RELEASE);
    k->ithread = 0;
    pthread_cond_broadcast(&init_cv);
    pthread_mutex_unlock(&init_mu);
    if (t->exc) {
        rt_print_exc(t, t->exc, k->name);
    }
}

/* ============================================================ chuoi */
jobj rt_str_utf16(jctx *t, const uint16_t *s, int32_t n) {
    jobj arr = rt_newarr(t, &rt_arr_C, n);
    if (!arr) return 0;
    if (n) memcpy(AD(arr, uint16_t), s, (size_t)n * 2);
    jobj o = rt_new(t, &K_java_lang_String);
    if (!o) return 0;
    STR(o)->f_value__5b = arr;
    STR(o)->f_offset_I = 0;
    STR(o)->f_count_I = n;
    return o;
}

jobj rt_str_latin1(jctx *t, const char *s, int32_t n) {
    jobj arr = rt_newarr(t, &rt_arr_C, n);
    if (!arr) return 0;
    uint16_t *d = AD(arr, uint16_t);
    for (int32_t i = 0; i < n; i++) d[i] = (uint8_t)s[i];
    jobj o = rt_new(t, &K_java_lang_String);
    if (!o) return 0;
    STR(o)->f_value__5b = arr;
    STR(o)->f_count_I = n;
    return o;
}

jobj rt_str_utf8(jctx *t, const char *s) {
    size_t n = strlen(s);
    uint16_t *tmp = (uint16_t *)malloc((n + 1) * 2);
    int32_t k = 0;
    for (size_t i = 0; i < n;) {
        uint8_t c = (uint8_t)s[i];
        uint32_t cp;
        if (c < 0x80) {
            cp = c;
            i += 1;
        } else if ((c & 0xE0) == 0xC0 && i + 1 < n) {
            cp = ((c & 0x1Fu) << 6) | (s[i + 1] & 0x3Fu);
            i += 2;
        } else if ((c & 0xF0) == 0xE0 && i + 2 < n) {
            cp = ((c & 0x0Fu) << 12) | ((s[i + 1] & 0x3Fu) << 6) | (s[i + 2] & 0x3Fu);
            i += 3;
        } else if ((c & 0xF8) == 0xF0 && i + 3 < n) {
            cp = ((c & 0x07u) << 18) | ((s[i + 1] & 0x3Fu) << 12) | ((s[i + 2] & 0x3Fu) << 6) | (s[i + 3] & 0x3Fu);
            i += 4;
        } else {
            cp = 0xFFFD;
            i += 1;
        }
        if (cp >= 0x10000) {
            cp -= 0x10000;
            tmp[k++] = (uint16_t)(0xD800 + (cp >> 10));
            tmp[k++] = (uint16_t)(0xDC00 + (cp & 0x3FF));
        } else {
            tmp[k++] = (uint16_t)cp;
        }
    }
    jobj r = rt_str_utf16(t, tmp, k);
    free(tmp);
    return r;
}

int32_t rt_str_len(jobj s) { return s ? STR(s)->f_count_I : 0; }

const uint16_t *rt_str_chars(jobj s) {
    return AD(STR(s)->f_value__5b, uint16_t) + STR(s)->f_offset_I;
}

char *rt_str_cstr(jobj s) {
    if (!s) return 0;
    int32_t n = STR(s)->f_count_I;
    const uint16_t *c = rt_str_chars(s);
    char *out = (char *)GC_MALLOC_ATOMIC((size_t)n * 3 + 1);
    int32_t k = 0;
    for (int32_t i = 0; i < n; i++) {
        uint32_t cp = c[i];
        if (cp >= 0xD800 && cp < 0xDC00 && i + 1 < n && c[i + 1] >= 0xDC00 && c[i + 1] < 0xE000) {
            cp = 0x10000 + ((cp - 0xD800) << 10) + (c[i + 1] - 0xDC00);
            i++;
            out[k++] = (char)(0xF0 | (cp >> 18));
            out[k++] = (char)(0x80 | ((cp >> 12) & 0x3F));
            out[k++] = (char)(0x80 | ((cp >> 6) & 0x3F));
            out[k++] = (char)(0x80 | (cp & 0x3F));
            continue;
        }
        if (cp < 0x80) {
            out[k++] = (char)cp;
        } else if (cp < 0x800) {
            out[k++] = (char)(0xC0 | (cp >> 6));
            out[k++] = (char)(0x80 | (cp & 0x3F));
        } else {
            out[k++] = (char)(0xE0 | (cp >> 12));
            out[k++] = (char)(0x80 | ((cp >> 6) & 0x3F));
            out[k++] = (char)(0x80 | (cp & 0x3F));
        }
    }
    out[k] = 0;
    return out;
}

/* intern: bang bam mo, luu trong bo nho GC (goc: con tro tinh) */
static jobj *g_itab;
static int32_t g_icap, g_icount;

static uint32_t hash16(const uint16_t *s, int32_t n) {
    uint32_t h = 0;
    for (int32_t i = 0; i < n; i++) h = 31 * h + s[i];
    return h;
}

static jobj intern_locked(jctx *t, const uint16_t *s, int32_t n, jobj existing) {
    if (g_icount * 2 >= g_icap) {
        int32_t nc = g_icap ? g_icap * 2 : 4096;
        jobj *nt = (jobj *)GC_MALLOC((size_t)nc * sizeof(jobj));
        for (int32_t i = 0; i < g_icap; i++) {
            jobj o = g_itab[i];
            if (!o) continue;
            uint32_t h = hash16(rt_str_chars(o), STR(o)->f_count_I) & (uint32_t)(nc - 1);
            while (nt[h]) h = (h + 1) & (uint32_t)(nc - 1);
            nt[h] = o;
        }
        g_itab = nt;
        g_icap = nc;
    }
    uint32_t h = hash16(s, n) & (uint32_t)(g_icap - 1);
    for (;;) {
        jobj o = g_itab[h];
        if (!o) break;
        if (STR(o)->f_count_I == n && memcmp(rt_str_chars(o), s, (size_t)n * 2) == 0) return o;
        h = (h + 1) & (uint32_t)(g_icap - 1);
    }
    jobj o = existing ? existing : rt_str_utf16(t, s, n);
    if (!o) return 0;
    g_itab[h] = o;
    g_icount++;
    return o;
}

jobj rt_intern(jctx *t, jobj s) {
    pthread_mutex_lock(&g_mu);
    jobj r = intern_locked(t, rt_str_chars(s), STR(s)->f_count_I, s);
    pthread_mutex_unlock(&g_mu);
    return r;
}

jobj rt_lit(jctx *t, int32_t idx) {
    pthread_mutex_lock(&g_mu);
    jobj r = rt_lits[idx];
    if (!r) {
        static const uint16_t empty = 0;
        const uint16_t *d = rt_litdat[idx] ? rt_litdat[idx] : &empty;
        r = intern_locked(t, d, rt_litlen[idx], 0);
        __atomic_store_n(&rt_lits[idx], r, __ATOMIC_RELEASE);
    }
    pthread_mutex_unlock(&g_mu);
    return r;
}

/* ============================================================ mirror (java.lang.Class) */
jobj rt_mirror(jctx *t, jclass *k) {
    jobj m = __atomic_load_n(&k->mirror, __ATOMIC_ACQUIRE);
    if (LIKELY(m != 0)) return m;
    jobj o = rt_new(t, &K_java_lang_Class);
    if (!o) return 0;
    ((struct O_java_lang_Class *)o)->f_vmClass_J = (int64_t)(intptr_t)k;
    pthread_mutex_lock(&g_mu);
    if (!k->mirror) __atomic_store_n(&k->mirror, o, __ATOMIC_RELEASE);
    m = k->mirror;
    pthread_mutex_unlock(&g_mu);
    return m;
}

/* ============================================================ ngoai le */
void rt_throw(jctx *t, jclass *k, const char *msg) {
    jobj e = rt_new(t, k);
    if (!e) return;
    if (msg) THR(e)->f_detailMessage_L = rt_str_utf8(t, msg);
    t->exc = e;
}

void rt_err(jctx *t, const char *cls, const char *msg) {
    jclass *k = rt_find_class(cls);
    if (!k) {
        rt_log("[rt] thieu lop ngoai le %s (%s)", cls, msg ? msg : "");
        k = &K_java_lang_Error;
    }
    rt_throw(t, k, msg);
}

void rt_npe(jctx *t) { rt_throw(t, &K_java_lang_NullPointerException, 0); }
void rt_arith(jctx *t) { rt_throw(t, &K_java_lang_ArithmeticException, "/ by zero"); }
void rt_ase(jctx *t) { rt_throw(t, &K_java_lang_ArrayStoreException, 0); }

void rt_aioobe(jctx *t, int32_t i) {
    char b[32];
    snprintf(b, sizeof b, "%d", i);
    rt_throw(t, &K_java_lang_ArrayIndexOutOfBoundsException, b);
}

void rt_cce(jctx *t, jobj o, const jclass *k) {
    char b[512];
    snprintf(b, sizeof b, "%s -> %s", o->cls->name, k->name);
    rt_throw(t, &K_java_lang_ClassCastException, b);
}

void rt_abstract(jctx *t) { rt_err(t, "java/lang/AbstractMethodError", 0); }

void rt_print_exc(jctx *t, jobj e, const char *where) {
    (void)t;
    if (!e) return;
    jobj m = THR(e)->f_detailMessage_L;
    char *ms = m ? rt_str_cstr(m) : 0;
    rt_log("[java] ngoai le %s%s%s (%s)", e->cls->name, ms ? ": " : "", ms ? ms : "", where ? where : "");
}

/* ============================================================ monitor */
typedef struct monitor {
    pthread_mutex_t m;
    pthread_cond_t enter;
    pthread_cond_t wait;
    jctx *owner;
    int32_t count;
} monitor;

static monitor *mon_of(jobj o) {
    monitor *mo = (monitor *)__atomic_load_n(&o->mon, __ATOMIC_ACQUIRE);
    if (LIKELY(mo != 0)) return mo;
    monitor *n = (monitor *)GC_MALLOC_ATOMIC(sizeof(monitor));
    memset(n, 0, sizeof *n);
    pthread_mutex_init(&n->m, 0);
    pthread_cond_init(&n->enter, 0);
    pthread_cond_init(&n->wait, 0);
    void *expect = 0;
    if (__atomic_compare_exchange_n(&o->mon, &expect, n, 0, __ATOMIC_ACQ_REL, __ATOMIC_ACQUIRE)) return n;
    return (monitor *)expect;
}

void rt_menter(jctx *t, jobj o) {
    monitor *mo = mon_of(o);
    pthread_mutex_lock(&mo->m);
    if (mo->owner == t) {
        mo->count++;
    } else {
        while (mo->owner) pthread_cond_wait(&mo->enter, &mo->m);
        mo->owner = t;
        mo->count = 1;
    }
    pthread_mutex_unlock(&mo->m);
}

static int mexit(jctx *t, jobj o) {
    monitor *mo = (monitor *)__atomic_load_n(&o->mon, __ATOMIC_ACQUIRE);
    if (!mo) return 0;
    pthread_mutex_lock(&mo->m);
    if (mo->owner != t) {
        pthread_mutex_unlock(&mo->m);
        return 0;
    }
    if (--mo->count == 0) {
        mo->owner = 0;
        pthread_cond_signal(&mo->enter);
    }
    pthread_mutex_unlock(&mo->m);
    return 1;
}

void rt_mexit(jctx *t, jobj o) {
    if (!mexit(t, o)) rt_err(t, "java/lang/IllegalMonitorStateException", 0);
}

void rt_mexit_keep(jctx *t, jobj o) { (void)mexit(t, o); }

void rt_wait(jctx *t, jobj o, int64_t ms) {
    monitor *mo = mon_of(o);
    pthread_mutex_lock(&mo->m);
    if (mo->owner != t) {
        pthread_mutex_unlock(&mo->m);
        rt_err(t, "java/lang/IllegalMonitorStateException", 0);
        return;
    }
    int32_t saved = mo->count;
    mo->owner = 0;
    mo->count = 0;
    pthread_cond_signal(&mo->enter);
    if (ms <= 0) {
        pthread_cond_wait(&mo->wait, &mo->m);
    } else {
        struct timespec ts;
        clock_gettime(CLOCK_REALTIME, &ts);
        ts.tv_sec += (time_t)(ms / 1000);
        ts.tv_nsec += (long)(ms % 1000) * 1000000L;
        if (ts.tv_nsec >= 1000000000L) {
            ts.tv_sec++;
            ts.tv_nsec -= 1000000000L;
        }
        pthread_cond_timedwait(&mo->wait, &mo->m, &ts);
    }
    while (mo->owner) pthread_cond_wait(&mo->enter, &mo->m);
    mo->owner = t;
    mo->count = saved;
    pthread_mutex_unlock(&mo->m);
}

void rt_notify(jctx *t, jobj o, int all) {
    monitor *mo = mon_of(o);
    pthread_mutex_lock(&mo->m);
    if (mo->owner != t) {
        pthread_mutex_unlock(&mo->m);
        rt_err(t, "java/lang/IllegalMonitorStateException", 0);
        return;
    }
    if (all) pthread_cond_broadcast(&mo->wait);
    else pthread_cond_signal(&mo->wait);
    pthread_mutex_unlock(&mo->m);
}

/* ============================================================ luong */
typedef struct tstart {
    jctx *ctx;
} tstart;

#ifdef __APPLE__
#include <pthread/qos.h>
#endif
/* luong Java tren iOS: uu tien tuong tac (khong bi gom hen gio / ha xung nhip nhu luong mac dinh) */
void rt_thread_qos(void) {
#ifdef __APPLE__
    pthread_set_qos_class_self_np(QOS_CLASS_USER_INTERACTIVE, 0);
#endif
}

static void *thread_main(void *arg) {
    tstart *st = (tstart *)arg;
    jctx *c = st->ctx;
    free(st);
    rt_thread_qos();
    tl_ctx = c;
    if (rt_thread_run_hook) rt_thread_run_hook(c, c->thread);
    if (c->exc) {
        rt_print_exc(c, c->exc, "luong ket thuc");
        c->exc = 0;
    }
    tl_ctx = 0;
    c->thread = 0;
    GC_FREE(c);
    return 0;
}

void rt_thread_start(jctx *t, jobj thread) {
    jctx *c = new_ctx(thread);
    tstart *st = (tstart *)malloc(sizeof *st);
    st->ctx = c;
    pthread_attr_t at;
    pthread_attr_init(&at);
    pthread_attr_setstacksize(&at, 8u << 20);
    pthread_attr_setdetachstate(&at, PTHREAD_CREATE_DETACHED);
    pthread_t th;
    int r = pthread_create(&th, &at, thread_main, st);
    pthread_attr_destroy(&at);
    if (r != 0) {
        free(st);
        GC_FREE(c);
        rt_err(t, "java/lang/OutOfMemoryError", "khong tao duoc luong");
    }
}

void rt_sleep(jctx *t, int64_t ms) {
    (void)t;
    if (ms <= 0) {
        sched_yield();
        return;
    }
    struct timespec ts = {(time_t)(ms / 1000), (long)(ms % 1000) * 1000000L};
    while (nanosleep(&ts, &ts) != 0 && errno == EINTR) {
    }
}

void rt_yield(void) { sched_yield(); }

int64_t rt_now_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_REALTIME, &ts);
    return (int64_t)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

void rt_gc(void) { GC_gcollect(); }
int64_t rt_free_memory(void) { return (int64_t)GC_get_free_bytes() + (int64_t)(64u << 20); }
int64_t rt_total_memory(void) { return (int64_t)GC_get_heap_size() + (int64_t)(64u << 20); }

int rt_attach_thread(jctx *ctx) {
    struct GC_stack_base sb;
    if (GC_get_stack_base(&sb) != GC_SUCCESS) return 0;
    GC_register_my_thread(&sb);
    tl_ctx = ctx;
    return 1;
}

/* ============================================================ khoi dong */
static jctx *g_main;

/* canh bao / loi cua GC: ghi thang (khong khoa, khong NSLog) vi co the dang dung "the gioi" */
static void GC_CALLBACK gc_warn(char *msg, GC_word arg) {
    char f[256], b[512];
    snprintf(f, sizeof f, "[gc] %s", msg);
    snprintf(b, sizeof b, f, (unsigned long)arg);
    plat_log_raw(b);
}

static void GC_CALLBACK gc_abort(const char *msg) {
    char b[512];
    snprintf(b, sizeof b, "[gc] LOI NANG (abort): %s", msg ? msg : "?");
    plat_log_raw(b);
}

#ifdef __APPLE__
#include <mach-o/getsect.h>
#include <mach-o/ldsyms.h>
/* Chi quet du lieu tinh cua chinh app. Mac dinh Boehm GC dang ky __DATA cua MOI thu vien he thong
 * (hang tram cai, ~3 vung moi cai) lam goc; tren iPhone that, mo ban phim nap them rat nhieu thu vien
 * -> vuot gioi han 2048 vung goc -> GC abort ("Too many root sets") = van app. Runtime khong de con tro
 * vao heap GC trong bo nho cua thu vien he thong (moi du lieu chuyen sang ObjC/CF deu duoc chep). */
static uintptr_t g_roots_lo, g_roots_hi;
static unsigned g_roots_seen, g_roots_kept;
static int GC_CALLBACK rt_roots_filter(const char *name, void *start, size_t size) {
    (void)name;
    (void)size;
    uintptr_t a = (uintptr_t)start;
    g_roots_seen++;
    if (a >= g_roots_lo && a < g_roots_hi) {
        g_roots_kept++;
        return 1;
    }
    return 0;
}
#endif

#ifdef __ANDROID__
/* Android: tien trinh app co hang tram thu vien he thong (ART...) - chi quet du lieu cua libarmy2.so */
static unsigned g_roots_seen, g_roots_kept;
static int GC_CALLBACK rt_roots_filter(const char *name, void *start, size_t size) {
    (void)start;
    (void)size;
    g_roots_seen++;
    if (name && strstr(name, "libarmy2.so")) {
        g_roots_kept++;
        return 1;
    }
    return 0;
}
#endif

void rt_gc_roots_stats(unsigned *seen, unsigned *kept) {
#if defined(__APPLE__) || defined(__ANDROID__)
    *seen = g_roots_seen;
    *kept = g_roots_kept;
#else
    *seen = *kept = 0;
#endif
}

void rt_init(void) {
    GC_set_warn_proc(gc_warn);
    GC_set_abort_func(gc_abort);
#ifdef __ANDROID__
    GC_register_has_static_roots_callback(rt_roots_filter);
#endif
#ifdef __APPLE__
    {
        unsigned long sz = 0;
        uint8_t *p = getsegmentdata(&_mh_execute_header, "__DATA", &sz);
        if (p && sz) {
            g_roots_lo = (uintptr_t)p;
            g_roots_hi = (uintptr_t)p + sz;
            GC_register_has_static_roots_callback(rt_roots_filter);
        }
    }
#endif
    GC_INIT();
    GC_allow_register_threads();
    init_prim_arrays();
    g_main = new_ctx(0);
    tl_ctx = g_main;
    g_oom = rt_new(g_main, &K_java_lang_OutOfMemoryError);
}
