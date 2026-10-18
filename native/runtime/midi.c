/*
 * midi.c - dung tep MIDI (SMF 0/1) thanh WAV PCM 16 bit mono 22050 Hz bang bo tong hop don gian
 * (song co ban + bao hinh theo nhom nhac cu GM) - kieu am thanh may dien thoai doi cu.
 * iOS khong co bo am GM san cho AVAudioPlayer, nen am nhac cua game (0.mid) duoc dung san luc tai.
 */
#include <math.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#define SR 22050
#define MAXV 48
#define MAXEV 20000

typedef struct ev {
    uint32_t tick;
    uint8_t st, a, b;
    uint8_t meta;     /* 1 = tempo */
    uint32_t tempo;
} ev;

typedef struct voice {
    int on, ch, note, vel, prog;
    double phase, freq, t, rel_t, rel_lvl;
    int releasing;
    uint32_t noise;
} voice;

static uint32_t rd32(const uint8_t *p) { return ((uint32_t)p[0] << 24) | ((uint32_t)p[1] << 16) | ((uint32_t)p[2] << 8) | p[3]; }
static uint16_t rd16(const uint8_t *p) { return (uint16_t)((p[0] << 8) | p[1]); }

static int vlq(const uint8_t *b, int n, int *i, uint32_t *out) {
    uint32_t v = 0;
    for (int k = 0; k < 4; k++) {
        if (*i >= n) return 0;
        uint8_t c = b[(*i)++];
        v = (v << 7) | (c & 0x7F);
        if (!(c & 0x80)) {
            *out = v;
            return 1;
        }
    }
    return 0;
}

static int cmp_ev(const void *x, const void *y) {
    const ev *a = (const ev *)x, *b = (const ev *)y;
    if (a->tick != b->tick) return a->tick < b->tick ? -1 : 1;
    /* tat not truoc khi bat not cung thoi diem */
    int ao = (a->st & 0xF0) == 0x80 || ((a->st & 0xF0) == 0x90 && a->b == 0);
    int bo = (b->st & 0xF0) == 0x80 || ((b->st & 0xF0) == 0x90 && b->b == 0);
    return bo - ao;
}

/* nhom nhac cu: 0 piano/go, 1 day keo / ken, 2 tieu / phim dien, 3 trong / go goc, 4 tieng on */
static int family(int prog, int ch) {
    if (ch == 9) return 3;
    if (prog <= 15) return 0;                  /* piano, chromatic perc */
    if (prog <= 23) return 2;                  /* organ / accordion */
    if (prog <= 31) return 0;                  /* guitar */
    if (prog <= 39) return 0;                  /* bass */
    if (prog <= 55) return prog == 47 ? 3 : 1; /* strings, ensemble (47 timpani) */
    if (prog <= 79) return 1;                  /* brass, reed, pipe */
    if (prog <= 103) return 2;                 /* synth */
    if (prog <= 111) return 0;                 /* ethnic */
    if (prog <= 119) return 3;                 /* percussive */
    return 4;                                  /* sound effects */
}

static double osc(voice *v, int fam) {
    double p = v->phase;
    switch (fam) {
    case 0: /* tam giac + hoa am nhe */
        return (p < 0.5 ? 4 * p - 1 : 3 - 4 * p) * 0.8 + sin(2 * M_PI * p * 2) * 0.2;
    case 1: /* rang cua mem */
        return (2 * p - 1) * 0.6 + sin(2 * M_PI * p) * 0.4;
    case 2: /* vuong mem */
        return (p < 0.5 ? 0.7 : -0.7) + sin(2 * M_PI * p) * 0.3;
    case 3: /* sin tut cao do */
        return sin(2 * M_PI * p);
    default: { /* on trang */
        v->noise = v->noise * 1664525u + 1013904223u;
        return ((int32_t)v->noise) / 2147483648.0;
    }
    }
}

static double env(voice *v, int fam) {
    double a, d, s;
    switch (fam) {
    case 0: a = 0.005; d = 0.6; s = 0.25; break;
    case 1: a = 0.04; d = 0.3; s = 0.7; break;
    case 2: a = 0.01; d = 0.2; s = 0.6; break;
    case 3: a = 0.002; d = 0.25; s = 0.0; break;
    default: a = 0.002; d = 0.15; s = 0.0; break;
    }
    double t = v->t, lv;
    if (t < a) lv = t / a;
    else if (t < a + d) lv = 1.0 - (1.0 - s) * (t - a) / d;
    else lv = s;
    if (fam == 0) lv *= exp(-t * 0.8);
    if (v->releasing) {
        double r = fam == 1 ? 0.15 : 0.08;
        double k = 1.0 - (v->t - v->rel_t) / r;
        if (k <= 0) {
            v->on = 0;
            return 0;
        }
        lv = v->rel_lvl * k;
    }
    return lv;
}

int midi_to_wav(const uint8_t *m, int n, uint8_t **out, int *outlen) {
    if (n < 14 || memcmp(m, "MThd", 4) != 0) return 0;
    uint32_t hl = rd32(m + 4);
    uint16_t ntr = rd16(m + 10), div = rd16(m + 12);
    if (div & 0x8000) return 0;  /* SMPTE: khong ho tro */
    ev *evs = (ev *)malloc(sizeof(ev) * MAXEV);
    int ne = 0;
    int o = 8 + (int)hl;
    for (int tr = 0; tr < ntr && o + 8 <= n; tr++) {
        if (memcmp(m + o, "MTrk", 4) != 0) break;
        int ln = (int)rd32(m + o + 4);
        const uint8_t *t = m + o + 8;
        int tn = ln;
        if (o + 8 + ln > n) tn = n - o - 8;
        o += 8 + ln;
        int i = 0;
        uint32_t tick = 0;
        uint8_t run = 0;
        while (i < tn && ne < MAXEV) {
            uint32_t dt;
            if (!vlq(t, tn, &i, &dt)) break;
            tick += dt;
            uint8_t st = t[i];
            if (st & 0x80) {
                i++;
                if (st < 0xF0) run = st;
            } else {
                st = run;
            }
            if (st == 0xFF) {
                if (i >= tn) break;
                uint8_t ty = t[i++];
                uint32_t l;
                if (!vlq(t, tn, &i, &l)) break;
                if (ty == 0x51 && l == 3 && i + 3 <= tn) {
                    ev *e = &evs[ne++];
                    memset(e, 0, sizeof *e);
                    e->tick = tick;
                    e->meta = 1;
                    e->tempo = ((uint32_t)t[i] << 16) | ((uint32_t)t[i + 1] << 8) | t[i + 2];
                }
                i += (int)l;
            } else if (st == 0xF0 || st == 0xF7) {
                uint32_t l;
                if (!vlq(t, tn, &i, &l)) break;
                i += (int)l;
            } else if (st >= 0x80) {
                uint8_t hi = st & 0xF0;
                ev *e = &evs[ne++];
                memset(e, 0, sizeof *e);
                e->tick = tick;
                e->st = st;
                if (hi == 0xC0 || hi == 0xD0) {
                    if (i >= tn) break;
                    e->a = t[i++];
                } else {
                    if (i + 1 >= tn) break;
                    e->a = t[i];
                    e->b = t[i + 1];
                    i += 2;
                }
            } else {
                break;
            }
        }
    }
    qsort(evs, (size_t)ne, sizeof(ev), cmp_ev);
    /* do dai */
    double us_per_tick = 500000.0 / div;
    double sec = 0;
    uint32_t last = 0;
    for (int k = 0; k < ne; k++) {
        sec += (evs[k].tick - last) * us_per_tick / 1e6;
        last = evs[k].tick;
        if (evs[k].meta) us_per_tick = (double)evs[k].tempo / div;
    }
    sec += 1.0;
    if (sec > 600) sec = 600;
    int ns = (int)(sec * SR);
    float *mix = (float *)calloc((size_t)ns, sizeof(float));
    voice vs[MAXV];
    memset(vs, 0, sizeof vs);
    int prog[16] = {0}, vol[16], expr[16];
    double bend[16];
    for (int c = 0; c < 16; c++) {
        vol[c] = 100;
        expr[c] = 127;
        bend[c] = 0;
    }
    us_per_tick = 500000.0 / div;
    double now = 0;
    last = 0;
    int k = 0, pos = 0;
    while (pos < ns) {
        /* xu ly su kien den thoi diem hien tai */
        while (k < ne) {
            double te = now + (evs[k].tick - last) * us_per_tick / 1e6;
            if ((int)(te * SR) > pos) break;
            now = te;
            last = evs[k].tick;
            ev *e = &evs[k++];
            if (e->meta) {
                us_per_tick = (double)e->tempo / div;
                continue;
            }
            int ch = e->st & 0x0F, hi = e->st & 0xF0;
            if (hi == 0x90 && e->b > 0) {
                int slot = -1;
                for (int v = 0; v < MAXV; v++) if (!vs[v].on) { slot = v; break; }
                if (slot < 0) {   /* cuop giong lau nhat */
                    double best = -1;
                    for (int v = 0; v < MAXV; v++) if (vs[v].t > best) { best = vs[v].t; slot = v; }
                }
                voice *v = &vs[slot];
                memset(v, 0, sizeof *v);
                v->on = 1;
                v->ch = ch;
                v->note = e->a;
                v->vel = e->b;
                v->prog = prog[ch];
                v->freq = 440.0 * pow(2.0, (e->a - 69) / 12.0);
                v->noise = 0x12345u + (uint32_t)slot * 7919u;
            } else if (hi == 0x80 || (hi == 0x90 && e->b == 0)) {
                for (int v = 0; v < MAXV; v++) {
                    if (vs[v].on && !vs[v].releasing && vs[v].ch == ch && vs[v].note == e->a) {
                        int fam = family(vs[v].prog, ch);
                        vs[v].rel_lvl = env(&vs[v], fam);
                        vs[v].releasing = 1;
                        vs[v].rel_t = vs[v].t;
                    }
                }
            } else if (hi == 0xC0) {
                prog[ch] = e->a;
            } else if (hi == 0xB0) {
                if (e->a == 7) vol[ch] = e->b;
                else if (e->a == 11) expr[ch] = e->b;
                else if (e->a == 123 || e->a == 120) {
                    for (int v = 0; v < MAXV; v++) if (vs[v].ch == ch) vs[v].on = 0;
                }
            } else if (hi == 0xE0) {
                int val = ((e->b << 7) | e->a) - 8192;
                bend[ch] = val / 8192.0 * 2.0;   /* +-2 cung */
            }
        }
        /* tong hop den su kien ke tiep (toi da 256 mau) */
        int end = pos + 256;
        if (k < ne) {
            double te = now + (evs[k].tick - last) * us_per_tick / 1e6;
            int pe = (int)(te * SR);
            if (pe < end) end = pe;
        }
        if (end > ns) end = ns;
        if (end <= pos) end = pos + 1;
        for (int v = 0; v < MAXV; v++) {
            voice *vc = &vs[v];
            if (!vc->on) continue;
            int fam = family(vc->prog, vc->ch);
            double amp = (vc->vel / 127.0) * (vol[vc->ch] / 127.0) * (expr[vc->ch] / 127.0) * 0.18;
            double f = vc->freq * pow(2.0, bend[vc->ch] / 12.0);
            for (int s = pos; s < end && vc->on; s++) {
                double ff = f;
                if (fam == 3) ff = f * (1.0 + 1.5 * exp(-vc->t * 30));   /* trong: tut cao do */
                double e2 = env(vc, fam);
                if (!vc->on) break;
                mix[s] += (float)(osc(vc, fam) * e2 * amp);
                vc->phase += ff / SR;
                vc->phase -= floor(vc->phase);
                vc->t += 1.0 / SR;
                if (fam >= 3 && vc->t > 1.5) vc->on = 0;
                if (fam == 0 && vc->t > 4) vc->on = 0;
            }
        }
        pos = end;
    }
    free(evs);
    /* cat im lang cuoi + chuan hoa nhe */
    int used = ns;
    while (used > SR / 4 && fabsf(mix[used - 1]) < 1e-4f) used--;
    float peak = 0;
    for (int i = 0; i < used; i++) if (fabsf(mix[i]) > peak) peak = fabsf(mix[i]);
    float g = peak > 0.95f ? 0.95f / peak : 1.0f;
    int bytes = used * 2;
    uint8_t *w = (uint8_t *)malloc((size_t)bytes + 44);
    memcpy(w, "RIFF", 4);
    uint32_t v32 = (uint32_t)bytes + 36;
    memcpy(w + 4, &v32, 4);
    memcpy(w + 8, "WAVEfmt ", 8);
    v32 = 16; memcpy(w + 16, &v32, 4);
    uint16_t v16 = 1; memcpy(w + 20, &v16, 2);       /* PCM */
    v16 = 1; memcpy(w + 22, &v16, 2);                /* mono */
    v32 = SR; memcpy(w + 24, &v32, 4);
    v32 = SR * 2; memcpy(w + 28, &v32, 4);
    v16 = 2; memcpy(w + 32, &v16, 2);
    v16 = 16; memcpy(w + 34, &v16, 2);
    memcpy(w + 36, "data", 4);
    v32 = (uint32_t)bytes; memcpy(w + 40, &v32, 4);
    int16_t *pcm = (int16_t *)(w + 44);
    for (int i = 0; i < used; i++) {
        float x = mix[i] * g;
        if (x > 1) x = 1;
        if (x < -1) x = -1;
        pcm[i] = (int16_t)lrintf(x * 32767.0f);
    }
    free(mix);
    *out = w;
    *outlen = bytes + 44;
    return 1;
}
