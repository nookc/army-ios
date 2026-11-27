/* hdkho.c - kho anh net cao cua may HD (ban "Avatar 250 HD"). Nam trong gfx.c (#include).
 *
 * Anh bat bien cua game (x1 / x2) duoc phong to len he so cua dich ve (K = 2..4). Viec phong to (xBRZ / bicubic + khoi noi)
 * ton vai ms / anh nen KHONG lam trong luong ve: lan dau anh duoc ve thi xep viec cho cac luong phu, trong luc cho anh
 * duoc ve tam bang diem anh goc (vai khung), xong thi ve ban net. Anh nho thi lam ngay (co han muc moi khung).
 *
 * Bo nho: diem anh net cao la malloc (khong qua GC), nam trong bang hd_tab; anh (Image) chi giu (o, the he). Tong bo nho co
 * han muc (hd_budget): vuot thi bo ban lau khong ve nhat (LRU) - anh do lan sau duoc ve se lam lai. Anh chet (GC thu) thi o
 * cua no tu gia di va bi bo khi can cho -> khong can finalizer / tham chieu yeu.
 * An toan luong: moi thao tac bang giu hd_mu; ban dang duoc ve thi "ghim" (pins) de khong bi bo giua chung.
 */
#include <pthread.h>
#include <time.h>
#include <unistd.h>

#define HD_SLOTS 8192
#define HD_BIG_MAX 9000000          /* anh net cao lon nhat (diem anh) - lon hon thi giam he so */
#define HD_SYNC_PX 2500             /* anh logic nho hon muc nay: lam ngay trong luong ve (neu con han muc khung) */
#define HD_SYNC_US 3000             /* han muc lam ngay moi khung (micro giay) */

enum { HD_FREE = 0, HD_WAIT, HD_BUILD, HD_READY, HD_NONE };

typedef struct hd_ent {
    uint32_t *px;               /* diem anh he so k (malloc) */
    uint8_t *shd;               /* mat na bong do (w x (h + shm)), NULL = khong co bong */
    int shm;
    int w, h, k, kind;
    uint32_t gen;               /* the he cua o: Image.bgen phai khop */
    uint32_t last;              /* khung dung gan nhat */
    int state, pins, rq;        /* rq = he so duoc yeu cau */
    const uint32_t *src;        /* viec: nguon (mang GC duoc giu song boi hd_pin) */
    int sw, sh, s, smooth, mode;
    size_t est;
    int next;
} hd_ent;

static hd_ent hd_tab[HD_SLOTS];
static void *hd_pin[HD_SLOTS];      /* vung tinh -> GC quet: giu mang nguon song khi viec con cho / dang lam */
static pthread_mutex_t hd_mu = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t hd_cv = PTHREAD_COND_INITIALIZER;
static int hd_qh = -1, hd_qt = -1, hd_qn;
static int hd_free_top = -2;        /* ngan xep o trong (-2 = chua khoi tao) */
static int hd_free_stk[HD_SLOTS];
static size_t hd_bytes, hd_pend;
static size_t hd_budget = (size_t)192 << 20;
static uint32_t hd_frame_no = 8;
static int hd_nworker, hd_nready;
static int hd_sync_left = HD_SYNC_US;
int gfx_hd_mode;                    /* che do xu ly cho anh duoc ve luc nay (bit 0: khoi noi, bit 1: bong do) - bat khi ve the gioi */
int gfx_hd_fx = 2;                  /* muc hieu ung 3D: 0 tat, 1 khoi noi + bong, 2 them hau ky canh (loa sang, tia nang...) */

void j2n_set_hd_fx(int muc) { gfx_hd_fx = muc < 0 ? 0 : muc > 2 ? 2 : muc; }

void j2n_set_hd_budget(int mb) {
    if (mb < 48) mb = 48;
    if (mb > 1024) mb = 1024;
    hd_budget = (size_t)mb << 20;
}

static void hd_post(uint32_t *px, int w, int h, int k, int mode);
static uint8_t *hd_shadow_make(const uint32_t *px, int w, int h, int k, int *omg);

static int64_t hd_now_us(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000000 + ts.tv_nsec / 1000;
}

/* lam 1 viec (khong giu khoa): tra ve diem anh (malloc) + kich thuoc + kieu alpha */
static uint32_t *hd_do(const uint32_t *src, int sw, int sh, int s, int k, int smooth, int mode, int *ow, int *oh, int *okind,
                       uint8_t **osh, int *oshm) {
    uint32_t *px = hd_make(src, sw, sh, s, k, smooth, ow, oh);
    *osh = 0;
    *oshm = 0;
    if (!px) return 0;
    if (mode) hd_post(px, *ow, *oh, k, mode);
    if (mode & 2) *osh = hd_shadow_make(px, *ow, *oh, k, oshm);
    int kind = 0;
    size_t n = (size_t)*ow * *oh;
    for (size_t i = 0; i < n; i++) {
        uint32_t a = px[i] >> 24;
        if (a != 255) {
            if (a != 0) {
                kind = 2;
                break;
            }
            kind = 1;
        }
    }
    *okind = kind;
    return px;
}

static void *hd_worker(void *arg) {
    (void)arg;
#ifdef __APPLE__
    pthread_set_qos_class_self_np(QOS_CLASS_USER_INITIATED, 0);
#endif
    for (;;) {
        pthread_mutex_lock(&hd_mu);
        while (hd_qh < 0) pthread_cond_wait(&hd_cv, &hd_mu);
        int slot = hd_qh;
        hd_ent *e = &hd_tab[slot];
        hd_qh = e->next;
        if (hd_qh < 0) hd_qt = -1;
        hd_qn--;
        e->state = HD_BUILD;
        const uint32_t *src = e->src;
        int sw = e->sw, sh = e->sh, s = e->s, k = e->k, smooth = e->smooth, mode = e->mode;
        pthread_mutex_unlock(&hd_mu);
        int ow = 0, oh = 0, kind = 0, shm = 0;
        uint8_t *shp = 0;
        uint32_t *px = hd_do(src, sw, sh, s, k, smooth, mode, &ow, &oh, &kind, &shp, &shm);
        pthread_mutex_lock(&hd_mu);
        hd_pend -= e->est;
        hd_pin[slot] = 0;
        e->src = 0;
        if (px) {
            e->px = px;
            e->w = ow;
            e->h = oh;
            e->kind = kind;
            e->shd = shp;
            e->shm = shm;
            hd_bytes += (size_t)ow * oh * 4 + (shp ? (size_t)ow * (oh + shm) : 0);
            hd_nready++;
            __atomic_store_n(&e->state, HD_READY, __ATOMIC_SEQ_CST);
        } else {
            e->state = HD_NONE;
        }
        pthread_mutex_unlock(&hd_mu);
    }
    return 0;
}

static void hd_start_workers(void) {
    int n = (int)sysconf(_SC_NPROCESSORS_ONLN) - 1;
    if (n < 1) n = 1;
    if (n > 3) n = 3;
    for (int i = 0; i < n; i++) {
        pthread_attr_t at;
        pthread_attr_init(&at);
        pthread_attr_setstacksize(&at, 1u << 20);
        pthread_attr_setdetachstate(&at, PTHREAD_CREATE_DETACHED);
        pthread_t th;
        if (pthread_create(&th, &at, hd_worker, 0) == 0) hd_nworker++;
        pthread_attr_destroy(&at);
    }
    if (hd_nworker == 0) hd_nworker = -1;       /* khong tao duoc luong: moi anh lam ngay trong luong ve */
}

/* dang giu khoa: thu hoi o READY / NONE neu khong ai dang ve no. 1 = da thu hoi.
 * Doi trang thai truoc (duong nhanh cua hd_get thoi nhan o nay), roi moi xem so ghim; con ghim thi tra lai. */
static void hd_drop(int slot);
static int hd_drop_try(int slot) {
    hd_ent *e = &hd_tab[slot];
    int st = e->state;
    if (st != HD_READY && st != HD_NONE) return 0;
    __atomic_store_n(&e->state, HD_BUILD, __ATOMIC_SEQ_CST);
    if (__atomic_load_n(&e->pins, __ATOMIC_SEQ_CST) != 0) {
        __atomic_store_n(&e->state, st, __ATOMIC_SEQ_CST);
        return 0;
    }
    hd_drop(slot);
    return 1;
}

static void hd_drop(int slot) {
    hd_ent *e = &hd_tab[slot];
    if (e->px) {
        hd_bytes -= (size_t)e->w * e->h * 4 + (e->shd ? (size_t)e->w * (e->h + e->shm) : 0);
        free(e->px);
        free(e->shd);
        e->px = 0;
        e->shd = 0;
        hd_nready--;
    }
    e->state = HD_FREE;
    e->gen++;
    hd_free_stk[++hd_free_top] = slot;
}

/* tim o READY / NONE lau khong dung nhat (tuoi >= min_age khung), -1 neu khong co */
static int hd_oldest(uint32_t min_age, int only_px) {
    int best = -1;
    uint32_t age = 0;
    for (int i = 0; i < HD_SLOTS; i++) {
        hd_ent *e = &hd_tab[i];
        if ((e->state == HD_READY || (!only_px && e->state == HD_NONE)) && e->pins == 0) {
            uint32_t a = hd_frame_no - e->last;
            if (a >= min_age && (best < 0 || a > age)) {
                age = a;
                best = i;
            }
        }
    }
    return best;
}

/* dang giu khoa: don cho cho 'need' byte. 1 = du cho */
static int hd_room(size_t need) {
    int guard = 0;
    while (hd_bytes + hd_pend + need > hd_budget && guard++ < 512) {
        int v = hd_oldest(3, 1);
        if (v < 0) return 0;
        if (!hd_drop_try(v)) hd_tab[v].last = hd_frame_no;       /* dang duoc ve: coi nhu vua dung */
    }
    return hd_bytes + hd_pend + need <= hd_budget;
}

struct O_javax_microedition_lcdui_Image;
static hd_ent *hd_get(struct O_javax_microedition_lcdui_Image *im, int rq);

static void hd_put(hd_ent *e) { __atomic_sub_fetch(&e->pins, 1, __ATOMIC_SEQ_CST); }

/* moi khung 1 lan (luong ve) */
void gfx_hd_frame(void) {
    pthread_mutex_lock(&hd_mu);
    hd_frame_no++;
    hd_sync_left = HD_SYNC_US;
    pthread_mutex_unlock(&hd_mu);
}

/* thong tin: 0 = so viec dang cho, 1 = MB dang giu, 2 = so anh net cao, 3 = han muc MB */
int gfx_hd_info(int what) {
    switch (what) {
    case 0: return hd_qn + (hd_pend ? 1 : 0);
    case 1: return (int)(hd_bytes >> 20);
    case 2: return hd_nready;
    case 3: return (int)(hd_budget >> 20);
    default: return 0;
    }
}

/* doi he so K luc dang chay (tu ha do net khi may yeu): bo het ban cu khong con dung */
void gfx_hd_set_k(int k) {
    if (k < 2 || k > 4 || k == gfx_hdk) return;
    pthread_mutex_lock(&hd_mu);
    gfx_hdk = k;
    for (int i = 0; i < HD_SLOTS; i++) hd_drop_try(i);
    pthread_mutex_unlock(&hd_mu);
}
