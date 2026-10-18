/* PadView - ban phim 4x2 cua Army 2 Coffee (chuyen tu Army2PadHostView cua app cu)
 *   F1 (phim mem trai) | ^ | LUC MAX | F2 (phim mem phai)
 *   <                  | v |    >    | OK
 * Nhan khi cham, nha khi buong; truot giua cac phim huong doi phim ngay.
 */
#import <UIKit/UIKit.h>

typedef void (^PadKeyBlock)(int code, BOOL pressed);

@interface PadView : UIView
@property (nonatomic, copy) PadKeyBlock onKey;
- (void)releaseAll;
@end

static NSString *const kLabF1 = @"iVBORw0KGgoAAAANSUhEUgAAAA4AAAANCAYAAACZ3F9/AAAAWklEQVR42rWQsQkAMQwDta7G+H1+nd/ky4BDClW5kCLEoEa+A2PZLkmYsYN9OlU9lIDYn4pvwqcCsxc1Zi8CcEf8EjwVGS6nWYl/giIwp2KrBEVmbK8ekkGmA+NdNXYox4jOAAAAAElFTkSuQmCC";
static NSString *const kLabF2 = @"iVBORw0KGgoAAAANSUhEUgAAABIAAAANCAYAAACkTj4ZAAAAdUlEQVR42p2T2wmAMBAEr92UYT+2Yyd+CitCYAJ3cUMO5sddh5BHtNYUESU9C9vpIx01FH3HishtL6QT8rJznr950UeWexFQYJBsibyEzb5gdvz0YOzUImZJgugGRDmnAwg3RBNx6BEkETkA/5T3hBnz3/f2AvXCn2XyKtfOAAAAAElFTkSuQmCC";
static NSString *const kLabOK = @"iVBORw0KGgoAAAANSUhEUgAAABMAAAANCAYAAABLjFUnAAAAcklEQVR42rWTwQnAMAwDva7G6D5dp5v0WXDRp8LI4EeawD2kJBfycHAByIho4Z7OlJ6U/B3Ko0dC60nNKpz+IlF22SmY5873AazIXLQgM9EO2SWYx05IuC5zYeYtmOeuzSYzxgeUNQFP9ugL1pOSf53NF9V35P4CqFKZAAAAAElFTkSuQmCC";
static NSString *const kLabLuc = @"iVBORw0KGgoAAAANSUhEUgAAAEIAAAASCAMAAADYBPgtAAAAGFBMVEUAAAAAAABpaWn/rAD/gQD8/wD/9AD/1QAKf4Y0AAAAAXRSTlMAQObYZgAAAMZJREFUeNqtlFEKhDAMRPPGrt7/xkvTpGKwsOLOx8yQyCsK1aoEsneiNeyZBChTQGvCpbrPrEHzY0d2a6K5BoPRFM+JsWBOKYg0F4loaIRFxsnBZds2zEYWM593hctzFrclQoASkYLZxGwrhKWI0+peJOE3BDfvKIJgHMdB5rRA5Fyo7vvAV0tEfou6DyFmM/Z9B+RZzBFRYurKNsw4Z6XdIIqJgfi40Cxnc0RvmRdDph4mXDqLre/IxXRzrx35XO8Q//rbfAHXcgiSVBcmkgAAAABJRU5ErkJggg==";
