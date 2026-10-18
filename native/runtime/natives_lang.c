/* natives_lang.c - java.lang: Object, Class, String, System, Runtime, Thread, Math, Float, Double */
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "natives.h"

#define CLS(o) ((struct O_java_lang_Class *)(o))
#define THREAD(o) ((struct O_java_lang_Thread *)(o))

jobj rt_intern(jctx *t, jobj s);

/* ------------------------------------------------------------ Object */
jobj N_java_lang_Object_getClass(jctx *t, jobj self) { return rt_mirror(t, self->cls); }

int32_t N_java_lang_Object_hashCode(jctx *t, jobj self) {
    (void)t;
    uintptr_t p = (uintptr_t)self;
    return (int32_t)((p >> 4) ^ (p >> 20));
}

void N_java_lang_Object_notify(jctx *t, jobj self) { rt_notify(t, self, 0); }
void N_java_lang_Object_notifyAll(jctx *t, jobj self) { rt_notify(t, self, 1); }

void N_java_lang_Object_wait_e5ca(jctx *t, jobj self, int64_t ms) {
    if (ms < 0) {
        rt_err(t, "java/lang/IllegalArgumentException", "timeout value is negative");
        return;
    }
    rt_wait(t, self, ms);
}

/* ------------------------------------------------------------ Class */
static jclass *kof(jobj mirror) { return (jclass *)(intptr_t)CLS(mirror)->f_vmClass_J; }

jobj N_java_lang_Class_name0(jctx *t, jobj self) { return rt_str_utf8(t, kof(self)->name); }

jobj N_java_lang_Class_forName0(jctx *t, jobj name) {
    char *n = rt_str_cstr(name);
    jclass *k = rt_find_class(n);
    if (!k) return 0;
    if (!(k->flags & F_ARRAY)) {
        rt_clinit(t, k);
        if (t->exc) return 0;
    }
    return rt_mirror(t, k);
}

jobj N_java_lang_Class_newInstance0(jctx *t, jobj self) {
    jclass *k = kof(self);
    if (!k->newinst) return 0;
    rt_clinit(t, k);
    if (t->exc) return 0;
    return k->newinst(t);
}

int32_t N_java_lang_Class_isInstance(jctx *t, jobj self, jobj o) {
    (void)t;
    return o ? rt_isinst(o, kof(self)) : 0;
}

int32_t N_java_lang_Class_isAssignableFrom(jctx *t, jobj self, jobj other) {
    if (!other) {
        rt_npe(t);
        return 0;
    }
    jclass *a = kof(self), *b = kof(other);
    if (a == b) return 1;
    if (a->flags & (F_IFACE | F_ARRAY)) return rt_isinst_slow(b, a);
    return rt_sub(b, a);
}

int32_t N_java_lang_Class_isInterface(jctx *t, jobj self) {
    (void)t;
    return (kof(self)->flags & F_IFACE) != 0;
}

int32_t N_java_lang_Class_isArray(jctx *t, jobj self) {
    (void)t;
    return (kof(self)->flags & F_ARRAY) != 0;
}

/* ------------------------------------------------------------ String */
jobj N_java_lang_String_intern(jctx *t, jobj self) { return rt_intern(t, self); }

/* ------------------------------------------------------------ System */
void N_java_lang_System_arraycopy(jctx *t, jobj src, int32_t sp, jobj dst, int32_t dp, int32_t n) {
    if (!src || !dst) {
        rt_npe(t);
        return;
    }
    jclass *sc = src->cls, *dc = dst->cls;
    if (!(sc->flags & F_ARRAY) || !(dc->flags & F_ARRAY)) {
        rt_err(t, "java/lang/ArrayStoreException", "not an array");
        return;
    }
    if (sc->etype != dc->etype) {
        rt_err(t, "java/lang/ArrayStoreException", "type mismatch");
        return;
    }
    if (n < 0 || sp < 0 || dp < 0 || (int64_t)sp + n > ALEN(src) || (int64_t)dp + n > ALEN(dst)) {
        char b[96];
        snprintf(b, sizeof b, "arraycopy: %d+%d/%d -> %d+%d/%d", sp, n, ALEN(src), dp, n, ALEN(dst));
        rt_err(t, "java/lang/ArrayIndexOutOfBoundsException", b);
        return;
    }
    if (n == 0) return;
    size_t es = (size_t)sc->esize;
    if (sc->etype == 'L' && sc != dc && dc->comp != &K_java_lang_Object) {
        /* kiem tra kieu tung phan tu */
        jobj *s = AD(src, jobj), *d = AD(dst, jobj);
        if (src == dst && sp < dp) {
            for (int32_t i = n - 1; i >= 0; i--) {
                jobj v = s[sp + i];
                if (v && !rt_isinst(v, dc->comp)) {
                    rt_err(t, "java/lang/ArrayStoreException", 0);
                    return;
                }
                d[dp + i] = v;
            }
        } else {
            for (int32_t i = 0; i < n; i++) {
                jobj v = s[sp + i];
                if (v && !rt_isinst(v, dc->comp)) {
                    rt_err(t, "java/lang/ArrayStoreException", 0);
                    return;
                }
                d[dp + i] = v;
            }
        }
        return;
    }
    memmove((char *)dst + sizeof(jarray) + (size_t)dp * es, (char *)src + sizeof(jarray) + (size_t)sp * es,
            (size_t)n * es);
}

int64_t N_java_lang_System_currentTimeMillis(jctx *t) {
    (void)t;
    return rt_now_ms();
}

int32_t N_java_lang_System_identityHashCode(jctx *t, jobj o) {
    return o ? N_java_lang_Object_hashCode(t, o) : 0;
}

/* ------------------------------------------------------------ Runtime */
int64_t N_java_lang_Runtime_freeMemory(jctx *t, jobj self) {
    (void)t; (void)self;
    return rt_free_memory();
}

int64_t N_java_lang_Runtime_totalMemory(jctx *t, jobj self) {
    (void)t; (void)self;
    return rt_total_memory();
}

void N_java_lang_Runtime_gc(jctx *t, jobj self) {
    (void)t; (void)self;
    rt_gc();
}

void plat_exit(void);
void N_java_lang_Runtime_exit(jctx *t, jobj self, int32_t code) {
    (void)t; (void)self; (void)code;
    plat_exit();
}

/* ------------------------------------------------------------ Thread */
jobj N_java_lang_Thread_currentThread(jctx *t) {
    if (!t->thread) {
        /* luong goc chua co doi tuong Thread (luong chinh / luong nen tang) */
        jobj o = rt_new(t, &K_java_lang_Thread);
        if (!o) return 0;
        THREAD(o)->f_name_L = rt_str_utf8(t, "main");
        THREAD(o)->f_priority_I = 5;
        THREAD(o)->f_alive_Z = 1;
        THREAD(o)->f_started_Z = 1;
        t->thread = o;
    }
    return t->thread;
}

void N_java_lang_Thread_yield(jctx *t) {
    (void)t;
    rt_yield();
}

void N_java_lang_Thread_sleep0(jctx *t, int64_t ms) { rt_sleep(t, ms); }

void N_java_lang_Thread_start0(jctx *t, jobj self) { rt_thread_start(t, self); }

/* ------------------------------------------------------------ Math */
double N_java_lang_Math_sin(jctx *t, double a) { (void)t; return sin(a); }
double N_java_lang_Math_cos(jctx *t, double a) { (void)t; return cos(a); }
double N_java_lang_Math_tan(jctx *t, double a) { (void)t; return tan(a); }
double N_java_lang_Math_sqrt(jctx *t, double a) { (void)t; return sqrt(a); }
double N_java_lang_Math_ceil(jctx *t, double a) { (void)t; return ceil(a); }
double N_java_lang_Math_floor(jctx *t, double a) { (void)t; return floor(a); }
double N_java_lang_Math_atan(jctx *t, double a) { (void)t; return atan(a); }
double N_java_lang_Math_atan2(jctx *t, double y, double x) { (void)t; return atan2(y, x); }
double N_java_lang_Math_pow(jctx *t, double a, double b) { (void)t; return pow(a, b); }
double N_java_lang_Math_exp(jctx *t, double a) { (void)t; return exp(a); }
double N_java_lang_Math_log(jctx *t, double a) { (void)t; return log(a); }

/* ------------------------------------------------------------ Float / Double */
int32_t N_java_lang_Float_floatToIntBits(jctx *t, float f) {
    (void)t;
    if (f != f) return 0x7fc00000;
    int32_t b;
    memcpy(&b, &f, 4);
    return b;
}

float N_java_lang_Float_intBitsToFloat(jctx *t, int32_t b) {
    (void)t;
    float f;
    memcpy(&f, &b, 4);
    return f;
}

int64_t N_java_lang_Double_doubleToLongBits(jctx *t, double d) {
    (void)t;
    if (d != d) return INT64_C(0x7ff8000000000000);
    int64_t b;
    memcpy(&b, &d, 8);
    return b;
}

double N_java_lang_Double_longBitsToDouble(jctx *t, int64_t b) {
    (void)t;
    double d;
    memcpy(&d, &b, 8);
    return d;
}

/* dinh dang kieu Java: so ngan nhat doc lai dung; 1e-3 <= |v| < 1e7 -> thap phan, khac -> d.dddE[-]n */
static jobj fmt_java(jctx *t, double v, int is_float) {
    char out[64];
    if (v != v) return rt_str_utf8(t, "NaN");
    if (isinf(v)) return rt_str_utf8(t, v > 0 ? "Infinity" : "-Infinity");
    if (v == 0) return rt_str_utf8(t, signbit(v) ? "-0.0" : "0.0");
    char digits[40];
    int exp10 = 0, nd = 0;
    int maxp = is_float ? 9 : 17;
    for (int p = 1; p <= maxp; p++) {
        char b[48];
        snprintf(b, sizeof b, "%.*e", p - 1, v);
        int ok;
        if (is_float) ok = (float)strtod(b, 0) == (float)v;
        else ok = strtod(b, 0) == v;
        if (ok || p == maxp) {
            /* tach chu so va so mu */
            const char *s = b;
            if (*s == '-') s++;
            nd = 0;
            for (; *s && *s != 'e'; s++) {
                if (*s >= '0' && *s <= '9') digits[nd++] = *s;
            }
            exp10 = atoi(s + 1);
            break;
        }
    }
    while (nd > 1 && digits[nd - 1] == '0') nd--;
    digits[nd] = 0;
    int k = 0;
    if (v < 0) out[k++] = '-';
    double a = fabs(v);
    if (a >= 1e-3 && a < 1e7) {
        if (exp10 >= 0) {
            for (int i = 0; i <= exp10; i++) out[k++] = i < nd ? digits[i] : '0';
            out[k++] = '.';
            if (exp10 + 1 < nd) {
                for (int i = exp10 + 1; i < nd; i++) out[k++] = digits[i];
            } else {
                out[k++] = '0';
            }
        } else {
            out[k++] = '0';
            out[k++] = '.';
            for (int i = 0; i < -exp10 - 1; i++) out[k++] = '0';
            for (int i = 0; i < nd; i++) out[k++] = digits[i];
        }
    } else {
        out[k++] = digits[0];
        out[k++] = '.';
        if (nd > 1) {
            for (int i = 1; i < nd; i++) out[k++] = digits[i];
        } else {
            out[k++] = '0';
        }
        k += snprintf(out + k, sizeof out - (size_t)k, "E%d", exp10);
    }
    out[k] = 0;
    return rt_str_utf8(t, out);
}

jobj N_java_lang_Float_toString_a131(jctx *t, float f) { return fmt_java(t, f, 1); }
jobj N_java_lang_Double_toString_27e7(jctx *t, double d) { return fmt_java(t, d, 0); }

static int parse_num(jctx *t, jobj s, double *out) {
    if (!s) {
        rt_npe(t);
        return 0;
    }
    char *c = rt_str_cstr(s);
    while (*c == ' ' || *c == '\t' || *c == '\n' || *c == '\r') c++;
    char *end;
    double d = strtod(c, &end);
    if (end == c) {
        rt_err(t, "java/lang/NumberFormatException", rt_str_cstr(s));
        return 0;
    }
    while (*end == ' ' || *end == '\t' || *end == '\n' || *end == '\r' || *end == 'f' || *end == 'F' || *end == 'd' ||
           *end == 'D') end++;
    if (*end) {
        rt_err(t, "java/lang/NumberFormatException", rt_str_cstr(s));
        return 0;
    }
    *out = d;
    return 1;
}

float N_java_lang_Float_parseFloat(jctx *t, jobj s) {
    double d = 0;
    if (!parse_num(t, s, &d)) return 0;
    return (float)d;
}

double N_java_lang_Double_parseDouble(jctx *t, jobj s) {
    double d = 0;
    if (!parse_num(t, s, &d)) return 0;
    return d;
}
