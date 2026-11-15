/*
 * App.m - app iOS "thuan" cho client Avatar 2.5.8 (Ban Ca 3D): client J2ME da dich san sang ma may ARM64,
 * KHONG chay qua gia lap / bo thong dich.
 *   - man hinh game phu vung an toan: Canvas rong 240 / 320 / 360 (cai dat), cao theo ti le man hinh
 *   - ban ban ca ve gap doi (vd 480 x 930) cho net
 *   - nut "Aa": cham = bat/tat ban phim iPhone (go thang vao o chu cua game), giu 1 giay = cai dat
 *   - mo app: hoi dia chi may chu (client noi 127.0.0.1 -> runtime chuyen sang may chu nay)
 *   - hop nhap chu cua game (Form / TextBox) = UIAlertController
 *   - "-autotest": tu dang nhap + chup man hinh (CI iOS Simulator), "-avatar.server host:cong" dat may chu
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

#define MIDLET "main.GameMidlet"
#define HD_CANVAS "FishGame"

static int gW = 240, gH = 426;          /* Canvas co so (man phong to = gap 2) */
static uint32_t *gFront;
static int gFrontW, gFrontH;
static size_t gFrontCap;
static pthread_mutex_t gFrontMu = PTHREAD_MUTEX_INITIALIZER;
static volatile int gFrameNo, gShownNo;
static NSString *gDataDir;
static NSString *gJar;
static BOOL gStarted, gAutotest;
static NSString *gArgServer;

@class GameVC;
static void log_roots(const char *when);
static void autotest(void);
static __weak GameVC *gVC;

static const int SIZES[] = {240, 320, 360};
static NSString *const SIZE_NAMES[] = {@"To (240)", @"Vừa (320)", @"Nhỏ (360)"};

/* ============================================================ cai dat */
static NSUserDefaults *prefs(void) { return NSUserDefaults.standardUserDefaults; }

static NSString *bundledServer(void) {
    NSString *p = [NSBundle.mainBundle pathForResource:@"server" ofType:@"txt"];
    NSString *s = p ? [NSString stringWithContentsOfFile:p encoding:NSUTF8StringEncoding error:nil] : nil;
    return [s stringByTrimmingCharactersInSet:NSCharacterSet.whitespaceAndNewlineCharacterSet] ?: @"";
}

static NSString *savedServer(void) {
    if (gArgServer.length) return gArgServer;
    NSString *s = [prefs() stringForKey:@"server"];
    return s ?: bundledServer();
}

/* bo http://, duong dan, khoang trang neu nguoi choi dan ca link */
static NSString *normalizeServer(NSString *s) {
    s = [s stringByTrimmingCharactersInSet:NSCharacterSet.whitespaceAndNewlineCharacterSet];
    NSRange r = [s rangeOfString:@"://"];
    if (r.location != NSNotFound) s = [s substringFromIndex:r.location + 3];
    r = [s rangeOfString:@"/"];
    if (r.location != NSNotFound) s = [s substringToIndex:r.location];
    return [s stringByReplacingOccurrencesOfString:@" " withString:@""];
}

static int savedWidth(void) {
    NSInteger v = [prefs() integerForKey:@"vw"];
    for (int i = 0; i < 3; i++) {
        if (SIZES[i] == v) return (int)v;
    }
    return SIZES[0];
}

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
    if (!gStarted) return;
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

/* ============================================================ ban phim iPhone -> phim cua game
 * Client da va: phim -1000 = o chu chuyen sang go thang ky tu (khong T9). Ky tu gui bang ma Unicode,
 * xoa = -8, xuong dong = -5 (chon). */
static void key_tap(int code) {
    j2n_post(J2N_KEY_PRESS, code, 0);
    j2n_post(J2N_KEY_RELEASE, code, 0);
}

static void type_text(NSString *text) {
    if (!gStarted || !text.length) return;
    BOOL mode = NO;
    for (NSUInteger i = 0; i < text.length; i++) {
        unichar c = [text characterAtIndex:i];
        if (c == '\n' || c == '\r') {
            key_tap(-5);
            continue;
        }
        if (c < 32) continue;
        if (!mode) {
            key_tap(-1000);
            mode = YES;
        }
        key_tap((int)c);
    }
}

@interface KeyView : UIView <UIKeyInput>
@end

@implementation KeyView
- (BOOL)canBecomeFirstResponder { return YES; }
- (BOOL)hasText { return YES; }   /* luon cho nut xoa hoat dong */
- (void)insertText:(NSString *)text { type_text(text); }
- (void)deleteBackward { if (gStarted) key_tap(-8); }
- (UIKeyboardType)keyboardType { return UIKeyboardTypeDefault; }
- (UIReturnKeyType)returnKeyType { return UIReturnKeyDefault; }
- (UITextAutocorrectionType)autocorrectionType { return UITextAutocorrectionTypeNo; }
- (UITextAutocapitalizationType)autocapitalizationType { return UITextAutocapitalizationTypeNone; }
- (UITextSpellCheckingType)spellCheckingType { return UITextSpellCheckingTypeNo; }
- (UITextSmartQuotesType)smartQuotesType { return UITextSmartQuotesTypeNo; }
- (UITextSmartDashesType)smartDashesType { return UITextSmartDashesTypeNo; }
- (UITextSmartInsertDeleteType)smartInsertDeleteType { return UITextSmartInsertDeleteTypeNo; }
- (UIKeyboardAppearance)keyboardAppearance { return UIKeyboardAppearanceDark; }
@end

/* ============================================================ man cai dat (may chu, co giao dien) */
@interface SettingsVC : UIViewController <UITextFieldDelegate>
@property (nonatomic) BOOL starting;
@property (nonatomic, copy) void (^onDone)(BOOL go);
- (void)fillServer:(NSString *)s;
- (void)pressGo;
@end

@implementation SettingsVC {
    UITextField *_server;
    UISegmentedControl *_size;
    UISwitch *_skip;
    UILabel *_err;
    UIScrollView *_scroll;
}

- (UILabel *)label:(NSString *)t size:(CGFloat)sz bold:(BOOL)bold {
    UILabel *l = [UILabel new];
    l.text = t;
    l.numberOfLines = 0;
    l.textColor = UIColor.whiteColor;
    l.font = bold ? [UIFont boldSystemFontOfSize:sz] : [UIFont systemFontOfSize:sz];
    return l;
}

- (UIButton *)button:(NSString *)t primary:(BOOL)primary action:(SEL)sel {
    UIButton *b = [UIButton buttonWithType:UIButtonTypeSystem];
    [b setTitle:t forState:UIControlStateNormal];
    b.titleLabel.font = [UIFont boldSystemFontOfSize:18];
    b.layer.cornerRadius = 10;
    b.backgroundColor = primary ? [UIColor colorWithRed:0.16 green:0.55 blue:0.95 alpha:1] : [UIColor colorWithWhite:1 alpha:0.15];
    [b setTitleColor:UIColor.whiteColor forState:UIControlStateNormal];
    [b.heightAnchor constraintEqualToConstant:48].active = YES;
    [b addTarget:self action:sel forControlEvents:UIControlEventTouchUpInside];
    return b;
}

- (void)viewDidLoad {
    [super viewDidLoad];
    self.view.backgroundColor = [UIColor colorWithRed:0.05 green:0.16 blue:0.30 alpha:0.97];
    _scroll = [UIScrollView new];
    _scroll.translatesAutoresizingMaskIntoConstraints = NO;
    _scroll.keyboardDismissMode = UIScrollViewKeyboardDismissModeInteractive;
    [self.view addSubview:_scroll];
    UIStackView *st = [UIStackView new];
    st.axis = UILayoutConstraintAxisVertical;
    st.spacing = 12;
    st.translatesAutoresizingMaskIntoConstraints = NO;
    [_scroll addSubview:st];
    UILayoutGuide *g = self.view.safeAreaLayoutGuide;
    [NSLayoutConstraint activateConstraints:@[
        [_scroll.topAnchor constraintEqualToAnchor:g.topAnchor],
        [_scroll.bottomAnchor constraintEqualToAnchor:self.view.keyboardLayoutGuide.topAnchor],
        [_scroll.leadingAnchor constraintEqualToAnchor:g.leadingAnchor],
        [_scroll.trailingAnchor constraintEqualToAnchor:g.trailingAnchor],
        [st.topAnchor constraintEqualToAnchor:_scroll.contentLayoutGuide.topAnchor constant:24],
        [st.bottomAnchor constraintEqualToAnchor:_scroll.contentLayoutGuide.bottomAnchor constant:-24],
        [st.leadingAnchor constraintEqualToAnchor:_scroll.frameLayoutGuide.leadingAnchor constant:22],
        [st.trailingAnchor constraintEqualToAnchor:_scroll.frameLayoutGuide.trailingAnchor constant:-22],
    ]];

    [st addArrangedSubview:[self label:@"Avatar - Bắn Cá 3D" size:26 bold:YES]];
    NSString *hint = self.starting
        ? @"Địa chỉ máy chủ (IP hoặc tên miền, có thể kèm :cổng)\nVí dụ 123.45.67.89:19128"
        : @"Địa chỉ máy chủ mới có hiệu lực khi kết nối lại (thoát ra màn đăng nhập hoặc mở lại app).\nVí dụ 123.45.67.89:19128";
    [st addArrangedSubview:[self label:hint size:15 bold:NO]];
    _server = [UITextField new];
    _server.borderStyle = UITextBorderStyleRoundedRect;
    _server.backgroundColor = UIColor.whiteColor;
    _server.textColor = UIColor.blackColor;
    _server.font = [UIFont systemFontOfSize:20];
    _server.keyboardType = UIKeyboardTypeURL;
    _server.autocorrectionType = UITextAutocorrectionTypeNo;
    _server.autocapitalizationType = UITextAutocapitalizationTypeNone;
    _server.spellCheckingType = UITextSpellCheckingTypeNo;
    _server.clearButtonMode = UITextFieldViewModeWhileEditing;
    _server.returnKeyType = UIReturnKeyGo;
    _server.delegate = self;
    _server.attributedPlaceholder = [[NSAttributedString alloc] initWithString:@"vd 192.168.1.5:19128"
        attributes:@{NSForegroundColorAttributeName: UIColor.grayColor}];
    _server.text = savedServer();
    [_server.heightAnchor constraintEqualToConstant:46].active = YES;
    [st addArrangedSubview:_server];
    _err = [self label:@"" size:14 bold:YES];
    _err.textColor = [UIColor colorWithRed:1 green:0.55 blue:0.5 alpha:1];
    _err.hidden = YES;
    [st addArrangedSubview:_err];

    [st addArrangedSubview:[self label:@"Cỡ giao diện" size:15 bold:YES]];
    _size = [[UISegmentedControl alloc] initWithItems:@[SIZE_NAMES[0], SIZE_NAMES[1], SIZE_NAMES[2]]];
    int vw = savedWidth();
    _size.selectedSegmentIndex = vw == 320 ? 1 : vw == 360 ? 2 : 0;
    [_size setTitleTextAttributes:@{NSForegroundColorAttributeName: UIColor.whiteColor} forState:UIControlStateNormal];
    [_size setTitleTextAttributes:@{NSForegroundColorAttributeName: UIColor.blackColor} forState:UIControlStateSelected];
    [st addArrangedSubview:_size];

    UIStackView *row = [UIStackView new];
    row.axis = UILayoutConstraintAxisHorizontal;
    row.spacing = 10;
    row.alignment = UIStackViewAlignmentCenter;
    _skip = [UISwitch new];
    _skip.on = [prefs() boolForKey:@"skip"];
    [row addArrangedSubview:_skip];
    [row addArrangedSubview:[self label:@"Không hỏi lại (giữ nút Aa 1 giây để đổi)" size:15 bold:NO]];
    [st addArrangedSubview:row];

    [st setCustomSpacing:22 afterView:row];
    NSString *goTitle = self.starting ? @"Vào game" : @"Lưu";
    NSString *cancelTitle = self.starting ? @"Thoát" : @"Đóng";
    [st addArrangedSubview:[self button:goTitle primary:YES action:@selector(pressGo)]];
    [st addArrangedSubview:[self button:cancelTitle primary:NO action:@selector(pressCancel)]];
    if (!self.starting) {
        [st addArrangedSubview:[self button:@"Thoát game" primary:NO action:@selector(pressQuit)]];
    }
}

- (UIStatusBarStyle)preferredStatusBarStyle { return UIStatusBarStyleLightContent; }

- (void)fillServer:(NSString *)s {
    [self loadViewIfNeeded];
    _server.text = s;
}

- (BOOL)textFieldShouldReturn:(UITextField *)tf {
    [self pressGo];
    return NO;
}

- (void)pressGo {
    NSString *s = normalizeServer(_server.text ?: @"");
    if (!s.length) {
        _err.text = @"Nhập địa chỉ máy chủ (hỏi admin)";
        _err.hidden = NO;
        [_server becomeFirstResponder];
        return;
    }
    NSInteger sel = _size.selectedSegmentIndex;
    int nvw = SIZES[sel >= 0 && sel < 3 ? sel : 0];
    BOOL sizeChanged = nvw != savedWidth();
    [prefs() setObject:s forKey:@"server"];
    [prefs() setBool:_skip.on forKey:@"skip"];
    [prefs() setInteger:nvw forKey:@"vw"];
    gArgServer = nil;
    j2n_set_server(s.UTF8String);
    [self.view endEditing:YES];
    BOOL starting = self.starting;
    void (^done)(BOOL) = self.onDone;
    UIViewController *parent = self.presentingViewController;
    [self dismissViewControllerAnimated:YES completion:^{
        if (!starting && sizeChanged && parent) {
            UIAlertController *a = [UIAlertController alertControllerWithTitle:nil
                message:@"Mở lại app để đổi cỡ giao diện" preferredStyle:UIAlertControllerStyleAlert];
            [a addAction:[UIAlertAction actionWithTitle:@"OK" style:UIAlertActionStyleDefault handler:nil]];
            [parent presentViewController:a animated:YES completion:nil];
        }
        if (done) done(YES);
    }];
}

- (void)pressCancel {
    if (self.starting) exit(0);
    [self.view endEditing:YES];
    void (^done)(BOOL) = self.onDone;
    [self dismissViewControllerAnimated:YES completion:^{
        if (done) done(NO);
    }];
}

- (void)pressQuit {
    exit(0);
}
@end

/* ============================================================ view controller game */
@interface GameVC : UIViewController
@property (nonatomic, readonly) KeyView *keyView;
- (void)showTextBox:(NSString *)title text:(NSString *)text max:(int)max cons:(int)cons;
- (void)showSettings:(BOOL)starting;
@end

@implementation GameVC {
    ScreenView *_screen;
    KeyView *_keys;
    UIButton *_aa;
    CADisplayLink *_link;
    CGFloat _kbTop;          /* mep tren ban phim (toa do view), 0 = khong co ban phim */
    BOOL _settingsShown;
    int _shownW;
}

- (KeyView *)keyView { return _keys; }
- (BOOL)prefersStatusBarHidden { return YES; }
- (BOOL)prefersHomeIndicatorAutoHidden { return YES; }
- (UIInterfaceOrientationMask)supportedInterfaceOrientations { return UIInterfaceOrientationMaskPortrait; }
- (UIRectEdge)preferredScreenEdgesDeferringSystemGestures { return UIRectEdgeBottom; }

- (void)viewDidLoad {
    [super viewDidLoad];
    self.view.backgroundColor = UIColor.blackColor;
    _screen = [[ScreenView alloc] initWithFrame:CGRectZero];
    [self.view addSubview:_screen];
    _keys = [[KeyView alloc] initWithFrame:CGRectMake(0, 0, 1, 1)];
    _keys.alpha = 0.01;
    [self.view addSubview:_keys];
    _aa = [UIButton buttonWithType:UIButtonTypeCustom];
    [_aa setTitle:@"Aa" forState:UIControlStateNormal];
    _aa.titleLabel.font = [UIFont boldSystemFontOfSize:15];
    [_aa setTitleColor:[UIColor colorWithWhite:1 alpha:0.9] forState:UIControlStateNormal];
    _aa.backgroundColor = [UIColor colorWithWhite:0 alpha:0.42];
    _aa.layer.cornerRadius = 9;
    _aa.layer.borderWidth = 1;
    _aa.layer.borderColor = [UIColor colorWithWhite:1 alpha:0.35].CGColor;
    [_aa addTarget:self action:@selector(toggleKeyboard) forControlEvents:UIControlEventTouchUpInside];
    UILongPressGestureRecognizer *lp = [[UILongPressGestureRecognizer alloc] initWithTarget:self action:@selector(aaHold:)];
    lp.minimumPressDuration = 1.0;
    [_aa addGestureRecognizer:lp];
    _aa.hidden = YES;
    [self.view addSubview:_aa];
    [NSNotificationCenter.defaultCenter addObserver:self selector:@selector(kbChange:)
                                               name:UIKeyboardWillChangeFrameNotification object:nil];
    [NSNotificationCenter.defaultCenter addObserver:self selector:@selector(kbHide:)
                                               name:UIKeyboardWillHideNotification object:nil];
    _link = [CADisplayLink displayLinkWithTarget:self selector:@selector(tick)];
    if (@available(iOS 15.0, *)) {
        _link.preferredFrameRateRange = CAFrameRateRangeMake(30, 60, 60);
    }
    [_link addToRunLoop:NSRunLoop.mainRunLoop forMode:NSRunLoopCommonModes];
}

- (void)viewDidAppear:(BOOL)animated {
    [super viewDidAppear:animated];
    if (gStarted || _settingsShown) return;
    if (!gAutotest && [prefs() boolForKey:@"skip"] && savedServer().length) {
        [self startGame];
    } else {
        [self showSettings:YES];
    }
}

- (void)showSettings:(BOOL)starting {
    if (_settingsShown) return;
    _settingsShown = YES;
    [_keys resignFirstResponder];
    SettingsVC *s = [SettingsVC new];
    s.starting = starting;
    s.modalPresentationStyle = UIModalPresentationOverFullScreen;
    s.modalTransitionStyle = UIModalTransitionStyleCrossDissolve;
    __weak GameVC *ws = self;
    s.onDone = ^(BOOL go) {
        GameVC *me = ws;
        if (!me) return;
        me->_settingsShown = NO;
        if (starting && go) [me startGame];
    };
    [self presentViewController:s animated:!gAutotest completion:nil];
    if (gAutotest && starting) {
        /* tu kiem thu: chup man cai dat roi bam "Vao game" nhu nguoi choi */
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(1.5 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
            UIWindow *w = ws.view.window;
            if (w) {
                UIGraphicsImageRenderer *r = [[UIGraphicsImageRenderer alloc] initWithBounds:w.bounds];
                UIImage *img = [r imageWithActions:^(UIGraphicsImageRendererContext *ctx) {
                    [w drawViewHierarchyInRect:w.bounds afterScreenUpdates:YES];
                }];
                [UIImagePNGRepresentation(img) writeToFile:[gDataDir stringByAppendingPathComponent:@"t0_cai_dat.png"] atomically:YES];
            }
            [s fillServer:savedServer()];
            [s pressGo];
        });
    }
}

/* Canvas: rong theo cai dat, cao theo ti le vung an toan (giong ban Android) */
- (void)startGame {
    if (gStarted) return;
    UIEdgeInsets in = self.view.safeAreaInsets;
    CGSize s = self.view.bounds.size;
    CGFloat sw = MAX(1, s.width - in.left - in.right), sh = MAX(1, s.height - in.top - in.bottom);
    gW = savedWidth();
    int h = (int)lround(gW * sh / sw);
    h = MAX(gW * 4 / 3, MIN(gW * 12 / 5, h));
    gH = h - (h % 2);
    j2n_set_server(savedServer().UTF8String);
    j2n_set_hd(HD_CANVAS, 2);
    plat_log([[NSString stringWithFormat:@"[app] vao game: may chu '%@', man %dx%d (vung an toan %.0fx%.0f diem)",
               savedServer(), gW, gH, sw, sh] UTF8String]);
    gStarted = YES;
    j2n_start(MIDLET, gW, gH);
    log_roots("khoi dong");
    _aa.hidden = NO;
    [self.view setNeedsLayout];
    if (gAutotest) autotest();
}

- (void)viewDidLayoutSubviews {
    [super viewDidLayoutSubviews];
    [self layoutScreen];
}

/* khung game vua phan con thay (ban phim bat thi thu nho lai, khong bi che) */
- (void)layoutScreen {
    UIEdgeInsets in = self.view.safeAreaInsets;
    CGSize s = self.view.bounds.size;
    CGFloat top = in.top, bottom = s.height - in.bottom;
    if (_kbTop > 0 && _kbTop < bottom) bottom = _kbTop;
    CGFloat availW = s.width - in.left - in.right, availH = MAX(1, bottom - top);
    CGFloat k = MIN(availW / gW, availH / gH);
    CGFloat dw = round(gW * k), dh = round(gH * k);
    _screen.frame = CGRectMake(in.left + round((availW - dw) / 2), top + round((availH - dh) / 2), dw, dh);
    CGFloat aw = 38, ah = 34;
    CGFloat ay = in.top + (s.height - in.top - in.bottom) * 0.42;
    if (_kbTop > 0) ay = MIN(ay, _kbTop - ah - 8);
    _aa.frame = CGRectMake(s.width - in.right - aw - 3, ay, aw, ah);
}

- (void)kbChange:(NSNotification *)n {
    CGRect r = [n.userInfo[UIKeyboardFrameEndUserInfoKey] CGRectValue];
    CGRect v = [self.view convertRect:r fromCoordinateSpace:UIScreen.mainScreen.coordinateSpace];
    CGFloat top = v.origin.y;
    _kbTop = (top < self.view.bounds.size.height - 1 && v.size.height > 1) ? top : 0;
    double dur = [n.userInfo[UIKeyboardAnimationDurationUserInfoKey] doubleValue];
    [UIView animateWithDuration:dur animations:^{
        [self layoutScreen];
    }];
}

- (void)kbHide:(NSNotification *)n {
    _kbTop = 0;
    double dur = [n.userInfo[UIKeyboardAnimationDurationUserInfoKey] doubleValue];
    [UIView animateWithDuration:dur animations:^{
        [self layoutScreen];
    }];
}

- (void)toggleKeyboard {
    if (!gStarted) return;
    if (_keys.isFirstResponder) [_keys resignFirstResponder];
    else [_keys becomeFirstResponder];
}

- (void)aaHold:(UILongPressGestureRecognizer *)g {
    if (g.state == UIGestureRecognizerStateBegan) [self showSettings:NO];
}

- (void)tick {
    j2n_vsync((int64_t)(_link.timestamp * 1e9));
    int f = gFrameNo;
    if (f == gShownNo || !gFront) return;
    gShownNo = f;
    pthread_mutex_lock(&gFrontMu);
    int w = gFrontW, h = gFrontH;
    CGImageRef img = make_image(gFront, w, h);
    pthread_mutex_unlock(&gFrontMu);
    [CATransaction begin];
    [CATransaction setDisableActions:YES];
    if (w != _shownW) {
        /* man thuong (anh diem anh) phong to sac net; man phong to (ban ban ca) phong min */
        _shownW = w;
        _screen.layer.magnificationFilter = w > gW ? kCAFilterLinear : kCAFilterNearest;
    }
    _screen.layer.contents = (__bridge id)img;
    [CATransaction commit];
    CGImageRelease(img);
}

- (void)showTextBox:(NSString *)title text:(NSString *)text max:(int)max cons:(int)cons {
    [_keys resignFirstResponder];
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
    plat_log([[NSString stringWithFormat:@"[app] hop nhap chu (kieu %d, toi da %d)", cons, max] UTF8String]);
    UIViewController *top = self;
    while (top.presentedViewController) top = top.presentedViewController;
    [top presentViewController:a animated:YES completion:nil];
}
@end

/* ============================================================ nen tang */
void plat_present(const uint32_t *fb, int w, int h) {
    size_t n = (size_t)w * (size_t)h;
    pthread_mutex_lock(&gFrontMu);
    if (!gFront || n > gFrontCap) {
        free(gFront);
        gFront = (uint32_t *)malloc(n * 4);
        gFrontCap = gFront ? n : 0;
    }
    if (gFront) {
        memcpy(gFront, fb, n * 4);
        gFrontW = w;
        gFrontH = h;
    }
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

/* nhat ky ra Documents/log.txt, loi sap ra Documents/crash.txt (xem trong app Tep: "Tren iPhone" > Avatar) */
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
    @autoreleasepool { [snd(h) stop]; }
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
    @autoreleasepool { snd(h).volume = MAX(0, MIN(100, level)) / 100.0f; }
}

/* ============================================================ tu kiem thu (CI) */
static void save_png(NSString *name) {
    int w = 0, h = 0, f = 0;
    uint32_t *buf = j2n_snapshot_any(&w, &h, &f);
    if (!buf) return;
    CGImageRef img = make_image(buf, w, h);
    free(buf);
    UIImage *ui = [UIImage imageWithCGImage:img];
    CGImageRelease(img);
    NSData *png = UIImagePNGRepresentation(ui);
    NSString *p = [gDataDir stringByAppendingPathComponent:[name stringByAppendingString:@".png"]];
    [png writeToFile:p atomically:YES];
    NSLog(@"[autotest] %@ %dx%d khung %d", name, w, h, f);
}

/* chup ca cua so UIKit (gom ban phim / hop nhap chu cua iOS neu co) */
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

static void at_tap(int x, int y) {
    j2n_post(J2N_POINTER_PRESS, x, y);
    usleep(90000);
    j2n_post(J2N_POINTER_RELEASE, x, y);
}

static void at_key(int k) {
    j2n_post(J2N_KEY_PRESS, k, 0);
    usleep(100000);
    j2n_post(J2N_KEY_RELEASE, k, 0);
}

/* go chu qua dung duong ban phim iPhone (KeyView.insertText) */
static void at_type(NSString *s) {
    dispatch_sync(dispatch_get_main_queue(), ^{
        [gVC.keyView becomeFirstResponder];
        [gVC.keyView insertText:s];
    });
}

static void autotest(void) {
    dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED, 0), ^{
        /* man mo dau: cho game ve xong (giai nen anh, nhac) */
        for (int i = 0; i < 300 && j2n_frames() < 30; i++) usleep(100000);
        usleep(6000000);
        save_png(@"t1_mo_dau");
        int f0 = j2n_frames();
        usleep(5000000);
        int f1 = j2n_frames();
        /* 2 lua chon (Choi moi / Doi tai khoan) o giua man: "Doi tai khoan" -> o dang nhap */
        at_tap(gW / 2, gH / 2 + 5);
        usleep(1500000);
        save_png(@"t2_o_dang_nhap");
        at_type(@"test1");
        usleep(1200000);
        save_window_png(@"t3_ban_phim");
        at_tap(gW / 2 + 28, gH / 2 + 4);       /* o mat khau */
        usleep(700000);
        at_type(@"123456");
        usleep(800000);
        save_png(@"t4_da_go");
        dispatch_sync(dispatch_get_main_queue(), ^{
            [gVC.keyView resignFirstResponder];
        });
        usleep(800000);
        at_key(-5);                             /* luu tai khoan -> "Choi tiep, test1" */
        usleep(1500000);
        save_png(@"t5_choi_tiep");
        at_key(-5);                             /* -> chon thanh pho */
        usleep(1500000);
        at_key(-5);                             /* -> danh sach thanh pho con */
        usleep(1500000);
        save_png(@"t6_thanh_pho");
        at_key(-5);                             /* -> ket noi (may chu gia cua CI) */
        usleep(5000000);
        save_png(@"t7_ket_noi");
        struct mach_task_basic_info ti;
        mach_msg_type_number_t tn = MACH_TASK_BASIC_INFO_COUNT;
        long ram = task_info(mach_task_self(), MACH_TASK_BASIC_INFO, (task_info_t)&ti, &tn) == KERN_SUCCESS
                       ? (long)(ti.resident_size >> 20) : -1;
        NSString *r = [NSString stringWithFormat:@"man %dx%d\nfps man mo dau %.1f\nram %ld MB\n", gW, gH, (f1 - f0) / 5.0, ram];
        [r writeToFile:[gDataDir stringByAppendingPathComponent:@"autotest.txt"] atomically:YES encoding:NSUTF8StringEncoding error:nil];
        NSLog(@"[autotest] %@", r);
        log_roots("sau tu kiem thu");
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
    gJar = [NSBundle.mainBundle pathForResource:@"Avatar258" ofType:@"jar"];
    strlcpy(gDataDirC, gDataDir.fileSystemRepresentation, sizeof gDataDirC);
    if (gJar) strlcpy(gJarC, gJar.fileSystemRepresentation, sizeof gJarC);
    [AVAudioSession.sharedInstance setCategory:AVAudioSessionCategoryAmbient error:nil];

    NSArray<NSString *> *args = NSProcessInfo.processInfo.arguments;
    gAutotest = [args containsObject:@"-autotest"];
    NSUInteger i = [args indexOfObject:@"-avatar.server"];
    if (i != NSNotFound && i + 1 < args.count) gArgServer = normalizeServer(args[i + 1]);

    self.window = [[UIWindow alloc] initWithFrame:UIScreen.mainScreen.bounds];
    GameVC *vc = [GameVC new];
    gVC = vc;
    self.window.rootViewController = vc;
    [self.window makeKeyAndVisible];
    app.idleTimerDisabled = YES;
    log_open();
    plat_log([[NSString stringWithFormat:@"[app] khoi dong Avatar %@ (%@), iOS %@, ban thuan (ma may)",
               [NSBundle.mainBundle objectForInfoDictionaryKey:@"CFBundleShortVersionString"],
               [NSBundle.mainBundle objectForInfoDictionaryKey:@"CFBundleVersion"],
               UIDevice.currentDevice.systemVersion] UTF8String]);
    crash_install();
    return YES;
}
@end

int main(int argc, char *argv[]) {
    @autoreleasepool {
        return UIApplicationMain(argc, argv, nil, NSStringFromClass(AppDelegate.class));
    }
}
