#import "PadView.h"

static const int kCols = 4, kRows = 2;
/* ma phim MIDP: -6/-7 phim mem, -1..-4 huong, -5 OK, -40 LUC MAX (client mo hop "Luc ban") */
static const int kCodes[8] = {-6, -1, -40, -7, -3, -2, -4, -5};
static const BOOL kDir[8] = {NO, YES, NO, NO, YES, YES, YES, NO};

@implementation PadView {
    NSMutableArray<UIView *> *_boxes;
    NSMutableArray<UIImageView *> *_labels;
    NSMutableArray *_images;
    NSMapTable<UITouch *, NSNumber *> *_touchCell;
    int _cellCount[8];
    int _keyOwners[8];
    UIImpactFeedbackGenerator *_haptic;
    UIColor *_normal, *_pressed, *_line, *_arrow;
}

- (instancetype)initWithFrame:(CGRect)frame {
    if ((self = [super initWithFrame:frame])) [self setup];
    return self;
}

static UIImage *b64img(NSString *s) {
    NSData *d = [[NSData alloc] initWithBase64EncodedString:s options:0];
    return d ? [UIImage imageWithData:d scale:1] : nil;
}

- (void)setup {
    _normal = UIColor.whiteColor;
    _pressed = [UIColor colorWithRed:0.80 green:0.91 blue:1.0 alpha:1];
    _line = [UIColor colorWithRed:0.63 green:0.81 blue:0.93 alpha:1];
    _arrow = [UIColor colorWithRed:0.27 green:0.30 blue:0.36 alpha:1];
    self.backgroundColor = _normal;
    self.multipleTouchEnabled = YES;
    _boxes = [NSMutableArray array];
    _labels = [NSMutableArray array];
    _images = [NSMutableArray array];
    _touchCell = [NSMapTable strongToStrongObjectsMapTable];
    _haptic = [[UIImpactFeedbackGenerator alloc] initWithStyle:UIImpactFeedbackStyleLight];
    NSArray *labs = @[kLabF1, @"chevron.up", kLabLuc, kLabF2, @"chevron.left", @"chevron.down", @"chevron.right", kLabOK];
    for (int i = 0; i < 8; i++) {
        UIView *box = [UIView new];
        box.userInteractionEnabled = NO;
        box.backgroundColor = _normal;
        box.layer.borderColor = _line.CGColor;
        box.layer.borderWidth = 0.5;
        UIImageView *iv = [UIImageView new];
        iv.userInteractionEnabled = NO;
        NSString *l = labs[i];
        UIImage *img = nil;
        if ([l hasPrefix:@"chevron"]) {
            UIImageSymbolConfiguration *cfg = [UIImageSymbolConfiguration configurationWithPointSize:20 weight:UIImageSymbolWeightLight];
            iv.image = [[UIImage systemImageNamed:l withConfiguration:cfg] imageWithRenderingMode:UIImageRenderingModeAlwaysTemplate];
            iv.tintColor = _arrow;
            iv.contentMode = UIViewContentModeCenter;
        } else {
            img = b64img(l);
            iv.image = img;
            iv.contentMode = UIViewContentModeScaleToFill;
            iv.layer.magnificationFilter = kCAFilterNearest;
            iv.layer.minificationFilter = kCAFilterNearest;
        }
        [box addSubview:iv];
        [self addSubview:box];
        [_boxes addObject:box];
        [_labels addObject:iv];
        [_images addObject:img ? (id)img : (id)NSNull.null];
    }
    [_haptic prepare];
}

- (void)layoutSubviews {
    [super layoutSubviews];
    CGFloat cw = self.bounds.size.width / kCols, ch = self.bounds.size.height / kRows;
    CGFloat sc = MAX(self.window.screen.scale ?: UIScreen.mainScreen.scale, 1);
    CGFloat widest = 1;
    for (id im in _images) if (im != NSNull.null) widest = MAX(widest, ((UIImage *)im).size.width);
    CGFloat dev = MAX(1, floor(cw * 0.8 * sc / widest));
    CGFloat ls = dev / sc;
    for (int i = 0; i < 8; i++) {
        int c = i % kCols, r = i / kCols;
        UIView *box = _boxes[i];
        box.frame = CGRectMake(round(c * cw), round(r * ch), round((c + 1) * cw) - round(c * cw), round((r + 1) * ch) - round(r * ch));
        UIImageView *iv = _labels[i];
        id im = _images[i];
        if (im != NSNull.null) {
            CGSize s = CGSizeMake(((UIImage *)im).size.width * ls, ((UIImage *)im).size.height * ls);
            iv.frame = CGRectMake(round((box.bounds.size.width - s.width) / 2 * sc) / sc, round((box.bounds.size.height - s.height) / 2 * sc) / sc, s.width, s.height);
        } else {
            iv.frame = box.bounds;
        }
    }
}

- (int)cellAt:(CGPoint)p {
    CGSize b = self.bounds.size;
    if (b.width <= 0 || b.height <= 0 || p.x < 0 || p.y < 0 || p.x >= b.width || p.y >= b.height) return -1;
    int c = MIN(kCols - 1, MAX(0, (int)(p.x / (b.width / kCols))));
    int r = MIN(kRows - 1, MAX(0, (int)(p.y / (b.height / kRows))));
    return r * kCols + c;
}

- (void)setPressed:(int)i on:(BOOL)on {
    _boxes[i].backgroundColor = on ? _pressed : _normal;
}

- (void)keyDown:(int)i {
    if (_keyOwners[i]++ == 0 && self.onKey) self.onKey(kCodes[i], YES);
}

- (void)keyUp:(int)i {
    if (_keyOwners[i] <= 0) return;
    if (--_keyOwners[i] == 0 && self.onKey) self.onKey(kCodes[i], NO);
}

- (void)press:(int)i {
    if (++_cellCount[i] == 1) {
        [self setPressed:i on:YES];
        [_haptic impactOccurred];
        [_haptic prepare];
    }
    [self keyDown:i];
}

- (void)releaseCell:(int)i {
    if (_cellCount[i] <= 0) return;
    _cellCount[i]--;
    [self keyUp:i];
    if (_cellCount[i] == 0) [self setPressed:i on:NO];
}

- (void)touchesBegan:(NSSet<UITouch *> *)touches withEvent:(UIEvent *)event {
    for (UITouch *t in touches) {
        int i = [self cellAt:[t locationInView:self]];
        if (i < 0) continue;
        [_touchCell setObject:@(i) forKey:t];
        [self press:i];
    }
}

- (void)touchesMoved:(NSSet<UITouch *> *)touches withEvent:(UIEvent *)event {
    for (UITouch *t in touches) {
        NSNumber *o = [_touchCell objectForKey:t];
        if (!o) continue;
        int old = o.intValue, now = [self cellAt:[t locationInView:self]];
        if (now < 0 || now == old || !kDir[old] || !kDir[now]) continue;
        [self releaseCell:old];
        [_touchCell setObject:@(now) forKey:t];
        [self press:now];
    }
}

- (void)finish:(NSSet<UITouch *> *)touches {
    for (UITouch *t in touches) {
        NSNumber *o = [_touchCell objectForKey:t];
        if (!o) continue;
        [_touchCell removeObjectForKey:t];
        [self releaseCell:o.intValue];
    }
}

- (void)touchesEnded:(NSSet<UITouch *> *)touches withEvent:(UIEvent *)event { [self finish:touches]; }
- (void)touchesCancelled:(NSSet<UITouch *> *)touches withEvent:(UIEvent *)event { [self finish:touches]; }

- (void)releaseAll {
    [_touchCell removeAllObjects];
    for (int i = 0; i < 8; i++) {
        _cellCount[i] = 0;
        [self setPressed:i on:NO];
        if (_keyOwners[i] > 0) {
            _keyOwners[i] = 0;
            if (self.onKey) self.onKey(kCodes[i], NO);
        }
    }
}

- (void)didMoveToWindow {
    [super didMoveToWindow];
    if (!self.window) [self releaseAll];
    else [self setNeedsLayout];
}
@end
