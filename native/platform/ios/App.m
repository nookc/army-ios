/*
 * App.m - vo app iOS cho client Army 2 Coffee dich san (khong bo thong dich).
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
#include "platform.h"
#import "PadView.h"

static int gW = 320, gH = 466;
static uint32_t *gFront;
static pthread_mutex_t gFrontMu = PTHREAD_MUTEX_INITIALIZER;
static volatile int gFrameNo, gShownNo;
static NSString *gDataDir;
static NSString *gJar;

@class GameVC;
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
    [self presentViewController:a animated:YES completion:nil];
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

/* nhat ky ra Documents/log.txt (xem trong app Tep: "Tren iPhone" > Army 2 Coffee) */
static FILE *gLog;
static pthread_mutex_t gLogMu = PTHREAD_MUTEX_INITIALIZER;
static int gCrashFd = -1;

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
    NSString *c = [docs stringByAppendingPathComponent:@"crash.txt"];
    gCrashFd = open(c.fileSystemRepresentation, O_WRONLY | O_CREAT | O_APPEND, 0644);
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

static struct sigaction gOld[32];

static void on_crash(int sig, siginfo_t *si, void *uc) {
    if (gCrashFd >= 0) {
        char b[128];
        time_t now = time(NULL);
        int n = snprintf(b, sizeof b, "\n=== SAP: tin hieu %d dia chi %p luc %ld\n", sig, si ? si->si_addr : 0, (long)now);
        write(gCrashFd, b, (size_t)n);
        void *bt[64];
        int k = backtrace(bt, 64);
        backtrace_symbols_fd(bt, k, gCrashFd);
    }
    sigaction(sig, &gOld[sig], NULL);
    raise(sig);
}

static void crash_install(void) {
    int sigs[] = {SIGSEGV, SIGBUS, SIGILL, SIGFPE, SIGABRT, SIGTRAP};
    for (size_t i = 0; i < sizeof sigs / sizeof sigs[0]; i++) {
        struct sigaction sa;
        memset(&sa, 0, sizeof sa);
        sa.sa_sigaction = on_crash;
        sa.sa_flags = SA_SIGINFO | SA_ONSTACK;
        sigaction(sigs[i], &sa, &gOld[sigs[i]]);
    }
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
        /* go chu vao o ten (phim ky tu nhu ban phim QWERTY) */
        const char *name = "tbot6";
        for (const char *c = name; *c; c++) {
            j2n_post(J2N_KEY_PRESS, *c, 0);
            usleep(80000);
            j2n_post(J2N_KEY_RELEASE, *c, 0);
            usleep(80000);
        }
        usleep(1500000);
        save_png(@"t5_go_ten");
        /* do toc do khi Canvas dang hien (truoc khi mo hop nhap chu): dem khung trong 10 giay */
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
        /* cham 2 lan vao o mat khau -> game mo TextBox -> hop nhap chu cua iOS (anh simctl o cuoi) */
        for (int k = 0; k < 2; k++) {
            j2n_post(J2N_POINTER_PRESS, 188, gH - 89);
            usleep(80000);
            j2n_post(J2N_POINTER_RELEASE, 188, gH - 89);
            usleep(700000);
        }
        usleep(1500000);
        save_png(@"t6_cham_mat_khau");
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
    gJar = [NSBundle.mainBundle pathForResource:@"Army2Coffee" ofType:@"jar"];
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
    j2n_start("coreLG.TerrainMidlet", gW, gH);
    crash_install();
    if ([NSProcessInfo.processInfo.arguments containsObject:@"-autotest"]) autotest();
    return YES;
}
@end

int main(int argc, char *argv[]) {
    @autoreleasepool {
        return UIApplicationMain(argc, argv, nil, NSStringFromClass(AppDelegate.class));
    }
}
