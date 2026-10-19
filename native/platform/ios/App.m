/*
 * App.m - vo app iOS cho client Army2.5.0 dich san (khong bo thong dich).
 *   - man hinh game: Canvas 320 x H (H vua phan tren ban phim), phong to giu ti le, diem anh net
 *   - ban phim 4x2 (PadView), cham man hinh = con tro MIDP
 *   - hop nhap chu (TextBox) = UIAlertController voi ban phim iPhone
 *   - am thanh WAV qua AVAudioPlayer
 *   - "-autotest": tu bam phim + chup man hinh vao Documents (CI iOS Simulator)
 */
#import <UIKit/UIKit.h>
#import <AVFoundation/AVFoundation.h>
#import <AudioToolbox/AudioToolbox.h>
#include <pthread.h>
#include <unistd.h>
#include <execinfo.h>
#include <fcntl.h>
#include <signal.h>
#include <time.h>
#include <mach/mach.h>
#include <mach-o/dyld.h>
#include <sys/ucontext.h>
#include "platform.h"
#import "PadView.h"

static int gW = 320, gH = 466;
static uint32_t *gFront;
static pthread_mutex_t gFrontMu = PTHREAD_MUTEX_INITIALIZER;
static volatile int gFrameNo, gShownNo;
static NSString *gDataDir;
static NSString *gJar;

@class GameVC;
static void log_roots(const char *when);
static __weak GameVC *gVC;

/* ============================================================ man hinh */
@interface ScreenView : UIView
@end

@implementation ScreenView {
    BOOL _down;
}

- (instancetype)initWithFrame:(CGRect)f {
    if ((self = [super initWithFrame:f])) {
        self.layer.magnificationFilter = kCAFilterNearest;
        self.layer.minificationFilter = kCAFilterLinear;
        self.backgroundColor = UIColor.blackColor;
        self.multipleTouchEnabled = NO;
        self.layer.contentsGravity = kCAGravityResize;
    }
    return self;
}

- (CGPoint)gamePoint:(UITouch *)t {
    CGPoint p = [t locationInView:self];
    CGSize s = self.bounds.size;
    int x = (int)(p.x * gW / MAX(s.width, 1));
    int y = (int)(p.y * gH / MAX(s.height, 1));
    return CGPointMake(MAX(0, MIN(gW - 1, x)), MAX(0, MIN(gH - 1, y)));
}

- (void)touchesBegan:(NSSet<UITouch *> *)touches withEvent:(UIEvent *)e {
    CGPoint p = [self gamePoint:touches.anyObject];
    _down = YES;
    j2n_post(J2N_POINTER_PRESS, (int)p.x, (int)p.y);
}

- (void)touchesMoved:(NSSet<UITouch *> *)touches withEvent:(UIEvent *)e {
    if (!_down) return;
    CGPoint p = [self gamePoint:touches.anyObject];
    j2n_post(J2N_POINTER_DRAG, (int)p.x, (int)p.y);
}

- (void)touchesEnded:(NSSet<UITouch *> *)touches withEvent:(UIEvent *)e {
    if (!_down) return;
    _down = NO;
    CGPoint p = [self gamePoint:touches.anyObject];
    j2n_post(J2N_POINTER_RELEASE, (int)p.x, (int)p.y);
}

- (void)touchesCancelled:(NSSet<UITouch *> *)touches withEvent:(UIEvent *)e {
    [self touchesEnded:touches withEvent:e];
}
@end

static CGImageRef make_image(const uint32_t *px, int w, int h) {
    CGColorSpaceRef cs = CGColorSpaceCreateDeviceRGB();
    CFDataRef data = CFDataCreate(NULL, (const UInt8 *)px, (CFIndex)w * h * 4);
    CGDataProviderRef dp = CGDataProviderCreateWithCFData(data);
    CGImageRef img = CGImageCreate(w, h, 8, 32, w * 4, cs,
                                   kCGBitmapByteOrder32Little | kCGImageAlphaNoneSkipFirst, dp, NULL, false,
                                   kCGRenderingIntentDefault);
    CGDataProviderRelease(dp);
    CFRelease(data);
    CGColorSpaceRelease(cs);
    return img;
}

/* ============================================================ view controller */
@interface GameVC : UIViewController
- (void)showTextBox:(NSString *)title text:(NSString *)text max:(int)max cons:(int)cons;
@end

@implementation GameVC {
    ScreenView *_screen;
    PadView *_pad;
    CADisplayLink *_link;
}

- (BOOL)prefersStatusBarHidden { return NO; }
- (BOOL)prefersHomeIndicatorAutoHidden { return YES; }
- (UIInterfaceOrientationMask)supportedInterfaceOrientations { return UIInterfaceOrientationMaskPortrait; }
- (UIRectEdge)preferredScreenEdgesDeferringSystemGestures { return UIRectEdgeAll; }

- (void)viewDidLoad {
    [super viewDidLoad];
    self.view.backgroundColor = [UIColor colorWithRed:0.39 green:0.80 blue:1.0 alpha:1];
    _screen = [[ScreenView alloc] initWithFrame:CGRectZero];
    [self.view addSubview:_screen];
    _pad = [[PadView alloc] initWithFrame:CGRectZero];
    _pad.onKey = ^(int code, BOOL pressed) {
        j2n_post(pressed ? J2N_KEY_PRESS : J2N_KEY_RELEASE, code, 0);
    };
    [self.view addSubview:_pad];
    _link = [CADisplayLink displayLinkWithTarget:self selector:@selector(tick)];
    if (@available(iOS 15.0, *)) {
        _link.preferredFrameRateRange = CAFrameRateRangeMake(30, 60, 60);
    }
    [_link addToRunLoop:NSRunLoop.mainRunLoop forMode:NSRunLoopCommonModes];
}

- (void)viewDidLayoutSubviews {
    [super viewDidLayoutSubviews];
    UIEdgeInsets in = self.view.safeAreaInsets;
    CGSize s = self.view.bounds.size;
    CGFloat w = s.width;
    CGFloat padH = round(w / 2);
    CGFloat bottom = in.bottom;
    _pad.frame = CGRectMake(0, s.height - bottom - padH, w, padH);
    CGFloat top = in.top;
    CGFloat avail = s.height - top - bottom - padH;
    CGFloat sh = MIN(avail, w * gH / (CGFloat)gW);
    CGFloat sw = sh * gW / (CGFloat)gH;
    _screen.frame = CGRectMake(round((w - sw) / 2), top + round((avail - sh) / 2), round(sw), round(sh));
}

- (void)tick {
    int f = gFrameNo;
    if (f == gShownNo || !gFront) return;
    gShownNo = f;
    pthread_mutex_lock(&gFrontMu);
    CGImageRef img = make_image(gFront, gW, gH);
    pthread_mutex_unlock(&gFrontMu);
    [CATransaction begin];
    [CATransaction setDisableActions:YES];
    _screen.layer.contents = (__bridge id)img;
    [CATransaction commit];
    CGImageRelease(img);
}

- (void)showTextBox:(NSString *)title text:(NSString *)text max:(int)max cons:(int)cons {
    UIAlertController *a = [UIAlertController alertControllerWithTitle:(title.length ? title : @"Nhập")
                                                               message:nil
                                                        preferredStyle:UIAlertControllerStyleAlert];
    [a addTextFieldWithConfigurationHandler:^(UITextField *tf) {
        tf.text = text;
        int c = cons & 0xFFFF;
        if (c == 2) tf.keyboardType = UIKeyboardTypeNumberPad;
        else if (c == 5) tf.keyboardType = UIKeyboardTypeDecimalPad;
        else if (c == 3) tf.keyboardType = UIKeyboardTypePhonePad;
        else if (c == 1) tf.keyboardType = UIKeyboardTypeEmailAddress;
        else if (c == 4) tf.keyboardType = UIKeyboardTypeURL;
        tf.secureTextEntry = (cons & 0x10000) != 0;
        tf.autocorrectionType = UITextAutocorrectionTypeNo;
        tf.autocapitalizationType = UITextAutocapitalizationTypeNone;
        tf.clearButtonMode = UITextFieldViewModeWhileEditing;
    }];
    __weak UIAlertController *wa = a;
    [a addAction:[UIAlertAction actionWithTitle:@"Hủy" style:UIAlertActionStyleCancel handler:^(UIAlertAction *x) {
        j2n_text_done(0, NULL);
    }]];
    [a addAction:[UIAlertAction actionWithTitle:@"OK" style:UIAlertActionStyleDefault handler:^(UIAlertAction *x) {
        NSString *s = wa.textFields.firstObject.text ?: @"";
        if ((int)s.length > max && max > 0) s = [s substringToIndex:max];
        j2n_text_done(1, s.UTF8String);
    }]];
    [_pad releaseAll];
    plat_log([[NSString stringWithFormat:@"[app] hop nhap chu (kieu %d, toi da %d)", cons, max] UTF8String]);
    [self presentViewController:a animated:YES completion:nil];
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, 3 * NSEC_PER_SEC), dispatch_get_main_queue(), ^{
        log_roots("sau khi mo ban phim");
    });
}
@end

/* ============================================================ nen tang */
void plat_present(const uint32_t *fb, int w, int h) {
    pthread_mutex_lock(&gFrontMu);
    if (!gFront) gFront = (uint32_t *)malloc((size_t)w * h * 4);
    memcpy(gFront, fb, (size_t)w * h * 4);
    pthread_mutex_unlock(&gFrontMu);
    __atomic_add_fetch(&gFrameNo, 1, __ATOMIC_RELEASE);
}

void plat_show_textbox(const char *title, const char *text, int max, int cons) {
    @autoreleasepool {
    NSString *t = [NSString stringWithUTF8String:title ?: ""] ?: @"";
    NSString *x = [NSString stringWithUTF8String:text ?: ""] ?: @"";
    dispatch_async(dispatch_get_main_queue(), ^{
        GameVC *vc = gVC;
        if (vc) [vc showTextBox:t text:x max:max cons:cons];
        else j2n_text_done(0, NULL);
    });
    }
}

int plat_open_url(const char *url) {
    @autoreleasepool {
    NSString *s = [NSString stringWithUTF8String:url ?: ""];
    NSURL *u = [NSURL URLWithString:s];
    if (!u) return 0;
    dispatch_async(dispatch_get_main_queue(), ^{
        [UIApplication.sharedApplication openURL:u options:@{} completionHandler:nil];
    });
    return 1;
    }
}

int plat_sms(const char *number, const char *text) {
    @autoreleasepool {
    NSString *n = [NSString stringWithUTF8String:number ?: ""];
    NSString *b = [[NSString stringWithUTF8String:text ?: ""] stringByAddingPercentEncodingWithAllowedCharacters:NSCharacterSet.URLQueryAllowedCharacterSet];
    NSString *u = [NSString stringWithFormat:@"sms:%@&body=%@", n, b ?: @""];
    return plat_open_url(u.UTF8String);
    }
}

void plat_vibrate(int ms) {
    dispatch_async(dispatch_get_main_queue(), ^{
        AudioServicesPlaySystemSound(kSystemSoundID_Vibrate);
    });
}

void plat_exit(void) {
    exit(0);
}

/* duong dan C chep san mot lan (fileSystemRepresentation la bo nho autorelease) */
static char gDataDirC[1024], gJarC[1024];
const char *plat_data_dir(void) { return gDataDirC; }
const char *plat_jar_path(void) { return gJarC[0] ? gJarC : NULL; }
const uint8_t *plat_jar_mem(size_t *len) { (void)len; return NULL; }

/* nhat ky ra Documents/log.txt, loi sap ra Documents/crash.txt (xem trong app Tep: "Tren iPhone" > Army2.5.0) */
static FILE *gLog;
static pthread_mutex_t gLogMu = PTHREAD_MUTEX_INITIALIZER;
static int gCrashFd = -1, gLogFd = -1;
static uintptr_t gImgBase;
static char gImgInfo[200];

static void raw_write(int fd, const char *s) {
    if (fd < 0 || !s) return;
    size_t n = strlen(s);
    while (n > 0) {
        ssize_t w = write(fd, s, n);
        if (w <= 0) return;
        s += w;
        n -= (size_t)w;
    }
}

static void log_open(void) {
    NSString *docs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES).firstObject;
    NSString *p = [docs stringByAppendingPathComponent:@"log.txt"];
    NSDictionary *at = [NSFileManager.defaultManager attributesOfItemAtPath:p error:nil];
    if (at && at.fileSize > 2 * 1024 * 1024) {
        NSString *old = [docs stringByAppendingPathComponent:@"log_cu.txt"];
        [NSFileManager.defaultManager removeItemAtPath:old error:nil];
        [NSFileManager.defaultManager moveItemAtPath:p toPath:old error:nil];
    }
    gLog = fopen(p.fileSystemRepresentation, "a");
    gLogFd = open(p.fileSystemRepresentation, O_WRONLY | O_CREAT | O_APPEND, 0644);
    NSString *c = [docs stringByAppendingPathComponent:@"crash.txt"];
    gCrashFd = open(c.fileSystemRepresentation, O_WRONLY | O_CREAT | O_APPEND, 0644);
    gImgBase = (uintptr_t)_dyld_get_image_header(0);
    snprintf(gImgInfo, sizeof gImgInfo, "app %s build %s: base=0x%lx slide=0x%lx",
             [[NSBundle.mainBundle objectForInfoDictionaryKey:@"CFBundleShortVersionString"] UTF8String] ?: "?",
             [[NSBundle.mainBundle objectForInfoDictionaryKey:@"CFBundleVersion"] UTF8String] ?: "?",
             (unsigned long)gImgBase, (unsigned long)_dyld_get_image_vmaddr_slide(0));
}

void plat_log(const char *s) {
    @autoreleasepool { NSLog(@"%s", s); }
    pthread_mutex_lock(&gLogMu);
    if (gLog) {
        time_t now = time(NULL);
        struct tm tm;
        localtime_r(&now, &tm);
        fprintf(gLog, "%02d/%02d %02d:%02d:%02d %s\n", tm.tm_mday, tm.tm_mon + 1, tm.tm_hour, tm.tm_min, tm.tm_sec, s);
        fflush(gLog);
    }
    pthread_mutex_unlock(&gLogMu);
}

/* khong khoa, khong cap phat: dung duoc trong GC / khi sap */
void plat_log_raw(const char *s) {
    raw_write(2, s);
    raw_write(2, "\n");
    raw_write(gLogFd, s);
    raw_write(gLogFd, "\n");
}

static struct sigaction gOld[32];

static void crash_line(const char *s) {
    raw_write(gCrashFd, s);
    raw_write(gLogFd, s);
}

static void on_crash(int sig, siginfo_t *si, void *ucv) {
    char b[512];
    uintptr_t pc = 0, lr = 0, sp = 0, fp = 0;
#if defined(__arm64__)
    ucontext_t *uc = (ucontext_t *)ucv;
    if (uc && uc->uc_mcontext) {
        pc = (uintptr_t)arm_thread_state64_get_pc(uc->uc_mcontext->__ss);
        lr = (uintptr_t)arm_thread_state64_get_lr(uc->uc_mcontext->__ss);
        sp = (uintptr_t)arm_thread_state64_get_sp(uc->uc_mcontext->__ss);
        fp = (uintptr_t)arm_thread_state64_get_fp(uc->uc_mcontext->__ss);
    }
#endif
    snprintf(b, sizeof b, "\n=== SAP: tin hieu %d ma %d dia chi %p luc %ld, luong %s\n%s\npc=0x%lx (app+0x%lx) lr=0x%lx (app+0x%lx) sp=0x%lx\n",
             sig, si ? si->si_code : 0, si ? si->si_addr : 0, (long)time(NULL),
             pthread_main_np() ? "chinh" : "phu", gImgInfo, (unsigned long)pc, (unsigned long)(pc - gImgBase),
             (unsigned long)lr, (unsigned long)(lr - gImgBase), (unsigned long)sp);
    crash_line(b);
    /* chuoi khung (frame pointer) cua doan ma bi loi */
    uintptr_t hi = (uintptr_t)pthread_get_stackaddr_np(pthread_self());
    uintptr_t lo = hi - pthread_get_stacksize_np(pthread_self());
    for (int i = 0; i < 40 && fp >= lo && fp + 16 <= hi && (fp & 7) == 0; i++) {
        uintptr_t next = ((uintptr_t *)fp)[0], ret = ((uintptr_t *)fp)[1];
        snprintf(b, sizeof b, "  #%d 0x%lx (app+0x%lx)\n", i, (unsigned long)ret, (unsigned long)(ret - gImgBase));
        crash_line(b);
        if (next <= fp) break;
        fp = next;
    }
    void *bt[64];
    int k = backtrace(bt, 64);
    if (gCrashFd >= 0) backtrace_symbols_fd(bt, k, gCrashFd);
    if (gLogFd >= 0) backtrace_symbols_fd(bt, k, gLogFd);
    sigaction(sig, &gOld[sig], NULL);
    raise(sig);
}

static void on_objc_exception(NSException *e) {
    NSString *s = [NSString stringWithFormat:@"\n=== NGOAI LE ObjC %@: %@\n%@\n", e.name, e.reason,
                                             [e.callStackSymbols componentsJoinedByString:@"\n"]];
    crash_line(s.UTF8String);
}

static void crash_install(void) {
    NSSetUncaughtExceptionHandler(&on_objc_exception);
    int sigs[] = {SIGSEGV, SIGBUS, SIGILL, SIGFPE, SIGABRT, SIGTRAP};
    for (size_t i = 0; i < sizeof sigs / sizeof sigs[0]; i++) {
        struct sigaction sa;
        memset(&sa, 0, sizeof sa);
        sa.sa_sigaction = on_crash;
        sa.sa_flags = SA_SIGINFO | SA_ONSTACK;
        sigaction(sigs[i], &sa, &gOld[sigs[i]]);
    }
}

/* so thu vien da nap + so vung du lieu GC xet (sau khi mo ban phim iOS nap them rat nhieu thu vien) */
static void log_roots(const char *when) {
    unsigned seen = 0, kept = 0;
    rt_gc_roots_stats(&seen, &kept);
    char b[256];
    snprintf(b, sizeof b, "[gc] %s: %u thu vien da nap, GC xet %u vung du lieu (giu %u cua app, bo %u cua he thong)",
             when, _dyld_image_count(), seen, kept, seen - kept);
    plat_log(b);
}

/* ---- am thanh: WAV (AVAudioPlayer). MIDI da duoc runtime/midi.c tong hop san thanh WAV */
static NSMutableDictionary<NSNumber *, AVAudioPlayer *> *gSounds;
static int gSoundNext;
static pthread_mutex_t gSoundMu = PTHREAD_MUTEX_INITIALIZER;

int plat_sound_create(const uint8_t *data, int len, const char *type) {
    @autoreleasepool {
    NSData *d = [NSData dataWithBytes:data length:(NSUInteger)len];
    NSError *err = nil;
    AVAudioPlayer *p = [[AVAudioPlayer alloc] initWithData:d error:&err];
    NSLog(@"[am thanh] %s %d byte: %@", type, len, p ? @"ok" : err.localizedDescription);
    pthread_mutex_lock(&gSoundMu);
    if (!gSounds) gSounds = [NSMutableDictionary dictionary];
    int h = gSoundNext++;
    if (p) {
        [p prepareToPlay];
        gSounds[@(h)] = p;
    }
    pthread_mutex_unlock(&gSoundMu);
    return h;
    }
}

static AVAudioPlayer *snd(int h) {
    pthread_mutex_lock(&gSoundMu);
    AVAudioPlayer *p = gSounds[@(h)];
    pthread_mutex_unlock(&gSoundMu);
    return p;
}

void plat_sound_start(int h, int loops) {
    @autoreleasepool {
    AVAudioPlayer *p = snd(h);
    if (!p) return;
    p.numberOfLoops = loops < 0 ? -1 : MAX(0, loops - 1);
    p.currentTime = 0;
    [p play];
    }
}

void plat_sound_stop(int h) {
    @autoreleasepool { [snd(h) stop];     }
}

void plat_sound_close(int h) {
    @autoreleasepool {
    pthread_mutex_lock(&gSoundMu);
    AVAudioPlayer *p = gSounds[@(h)];
    [p stop];
    [gSounds removeObjectForKey:@(h)];
    pthread_mutex_unlock(&gSoundMu);
    }
}

void plat_sound_volume(int h, int level) {
    @autoreleasepool { snd(h).volume = MAX(0, MIN(100, level)) / 100.0f;     }
}

/* ============================================================ tu kiem thu (CI) */
static void save_png(NSString *name) {
    uint32_t *buf = (uint32_t *)malloc((size_t)gW * gH * 4);
    int f = j2n_snapshot(buf, gW, gH);
    CGImageRef img = make_image(buf, gW, gH);
    free(buf);
    UIImage *ui = [UIImage imageWithCGImage:img];
    CGImageRelease(img);
    NSData *png = UIImagePNGRepresentation(ui);
    NSString *p = [gDataDir stringByAppendingPathComponent:[name stringByAppendingString:@".png"]];
    [png writeToFile:p atomically:YES];
    NSLog(@"[autotest] %@ khung %d", name, f);
}

/* chup ca cua so UIKit (gom hop nhap chu cua iOS) */
static void save_window_png(NSString *name) {
    dispatch_sync(dispatch_get_main_queue(), ^{
        UIWindow *w = gVC.view.window;
        if (!w) return;
        UIGraphicsImageRenderer *r = [[UIGraphicsImageRenderer alloc] initWithBounds:w.bounds];
        UIImage *img = [r imageWithActions:^(UIGraphicsImageRendererContext *ctx) {
            [w drawViewHierarchyInRect:w.bounds afterScreenUpdates:NO];
        }];
        NSString *p = [gDataDir stringByAppendingPathComponent:[name stringByAppendingString:@".png"]];
        [UIImagePNGRepresentation(img) writeToFile:p atomically:YES];
        NSLog(@"[autotest] %@ (cua so)", name);
    });
}

/* dien chu vao hop nhap dang mo roi bam OK (nhu nguoi dung) */
static BOOL autotest_answer(NSString *text) {
    __block BOOL ok = NO;
    dispatch_sync(dispatch_get_main_queue(), ^{
        UIViewController *p = gVC.presentedViewController;
        if ([p isKindOfClass:UIAlertController.class]) {
            UIAlertController *a = (UIAlertController *)p;
            a.textFields.firstObject.text = text;
            [gVC dismissViewControllerAnimated:YES completion:nil];
            j2n_text_done(1, text.UTF8String);
            ok = YES;
        }
    });
    NSLog(@"[autotest] tra loi hop nhap chu '%@': %@", text, ok ? @"ok" : @"KHONG THAY HOP");
    return ok;
}

static void tap(int x, int y) {
    j2n_post(J2N_POINTER_PRESS, x, y);
    usleep(90000);
    j2n_post(J2N_POINTER_RELEASE, x, y);
}

static void autotest(void) {
    dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED, 0), ^{
        struct { int ms; int key; const char *shot; } steps[] = {
            {7000, 0, "t1_cauhinh"}, {300, -5, NULL}, {3500, 0, "t2_amthanh"}, {300, -6, NULL}, /* Bat am thanh (thu nhac MIDI) */
            {5000, 0, "t3_gioithieu"}, {300, -7, NULL}, {5000, 0, "t4_dangnhap"},
        };
        for (size_t i = 0; i < sizeof steps / sizeof steps[0]; i++) {
            usleep((useconds_t)steps[i].ms * 1000);
            if (steps[i].key) {
                j2n_post(J2N_KEY_PRESS, steps[i].key, 0);
                usleep(100000);
                j2n_post(J2N_KEY_RELEASE, steps[i].key, 0);
            }
            if (steps[i].shot) save_png([NSString stringWithUTF8String:steps[i].shot]);
        }
        /* do toc do khi Canvas dang hien: dem khung trong 10 giay */
        int f0 = j2n_frames();
        usleep(10000000);
        int f1 = j2n_frames();
        struct mach_task_basic_info ti;
        mach_msg_type_number_t tn = MACH_TASK_BASIC_INFO_COUNT;
        long ram = task_info(mach_task_self(), MACH_TASK_BASIC_INFO, (task_info_t)&ti, &tn) == KERN_SUCCESS
                       ? (long)(ti.resident_size >> 20) : -1;
        NSString *r = [NSString stringWithFormat:@"fps %.1f\nram %ld MB\n", (f1 - f0) / 10.0, ram];
        [r writeToFile:[gDataDir stringByAppendingPathComponent:@"autotest.txt"] atomically:YES encoding:NSUTF8StringEncoding error:nil];
        NSLog(@"[autotest] toc do %@", r);
        log_roots("truoc khi mo ban phim");

        /* 1) cham 1 lan vao o TEN nhu nguoi dung -> hop nhap chu iOS (ban phim thuong) */
        tap(188, gH - 124);
        usleep(3000000);
        save_window_png(@"t5_hop_nhap_ten");
        j2n_post(J2N_GC, 0, 0); /* GC khi da nap thu vien ban phim */
        usleep(3500000);
        autotest_answer(@"tbot6");
        usleep(2500000);
        save_png(@"t6_da_nhap_ten");

        /* 2) cham vao o MAT KHAU (1 lan, chua mo thi cham lan 2) -> hop nhap mat khau */
        tap(188, gH - 89);
        usleep(1500000);
        __block BOOL shown = NO;
        dispatch_sync(dispatch_get_main_queue(), ^{ shown = gVC.presentedViewController != nil; });
        if (!shown) {
            tap(188, gH - 89);
            usleep(1500000);
        }
        usleep(1500000);
        save_window_png(@"t7_hop_nhap_mat_khau");
        j2n_post(J2N_GC, 0, 0);
        usleep(2000000);
        log_roots("sau 2 hop nhap chu");
        NSLog(@"[autotest] xong");
    });
}

/* ============================================================ app */
@interface AppDelegate : UIResponder <UIApplicationDelegate>
@property (nonatomic, strong) UIWindow *window;
@end

@implementation AppDelegate
- (BOOL)application:(UIApplication *)app didFinishLaunchingWithOptions:(NSDictionary *)opts {
    NSString *docs = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES).firstObject;
    gDataDir = [docs stringByAppendingPathComponent:@"rms"];
    [NSFileManager.defaultManager createDirectoryAtPath:gDataDir withIntermediateDirectories:YES attributes:nil error:nil];
    gJar = [NSBundle.mainBundle pathForResource:@"Army250" ofType:@"jar"];
    strlcpy(gDataDirC, gDataDir.fileSystemRepresentation, sizeof gDataDirC);
    if (gJar) strlcpy(gJarC, gJar.fileSystemRepresentation, sizeof gJarC);
    [AVAudioSession.sharedInstance setCategory:AVAudioSessionCategoryAmbient error:nil];

    /* kich thuoc Canvas: rong 320, cao vua phan tren ban phim 4x2 (giong app cu) */
    CGSize sz = UIScreen.mainScreen.bounds.size;
    CGFloat shortSide = MIN(sz.width, sz.height), longSide = MAX(sz.width, sz.height);
    UIEdgeInsets in = UIEdgeInsetsZero;
    if (@available(iOS 15.0, *)) {
        UIWindowScene *sc = (UIWindowScene *)UIApplication.sharedApplication.connectedScenes.anyObject;
        if ([sc isKindOfClass:UIWindowScene.class]) in = sc.windows.firstObject.safeAreaInsets;
    }
    CGFloat largest = MAX(MAX(in.top, in.bottom), MAX(in.left, in.right));
    BOOL notch = largest > 24 || longSide / shortSide > 2.0;
    CGFloat topIn = notch ? MAX(largest, 47) : 20, botIn = notch ? 34 : 0;
    CGFloat canvasPts = longSide - topIn - botIn - shortSide / 2;
    int h = (int)lround(320.0 * canvasPts / shortSide);
    h -= h % 2;
    gH = MAX(360, MIN(600, h));
    gW = 320;

    self.window = [[UIWindow alloc] initWithFrame:UIScreen.mainScreen.bounds];
    GameVC *vc = [GameVC new];
    gVC = vc;
    self.window.rootViewController = vc;
    [self.window makeKeyAndVisible];
    app.idleTimerDisabled = YES;
    log_open();
    plat_log([[NSString stringWithFormat:@"[app] khoi dong %@ (%@), man %dx%d, iOS %@",
               [NSBundle.mainBundle objectForInfoDictionaryKey:@"CFBundleShortVersionString"],
               [NSBundle.mainBundle objectForInfoDictionaryKey:@"CFBundleVersion"], gW, gH,
               UIDevice.currentDevice.systemVersion] UTF8String]);
    crash_install();
    j2n_start("coreLG.TerrainMidlet", gW, gH);
    log_roots("khoi dong");
    if ([NSProcessInfo.processInfo.arguments containsObject:@"-autotest"]) autotest();
    return YES;
}
@end

int main(int argc, char *argv[]) {
    @autoreleasepool {
        return UIApplicationMain(argc, argv, nil, NSStringFromClass(AppDelegate.class));
    }
}
