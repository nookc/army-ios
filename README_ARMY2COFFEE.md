# Army 2 Coffee cho iOS

App iOS chạy client Army 2 Coffee (file J2ME `.jar`). Dựng trên **phoneME iOS** (bộ chạy J2ME mã nguồn mở, giấy phép GPL-3.0, tác giả Duy Pham: https://github.com/phd051199/phoneME-iOS).

So với bản gốc, repo này thêm:

- Gói sẵn client `Bundled/Army2Coffee.jar`. Lần đầu mở app sẽ tự nhập client vào thư viện, mỗi lần mở app là vào game luôn.
  - Màn hình dọc, rộng 320, cao vừa phần trên bàn phím. Dùng cảm ứng, vuốt lên xuống để cuộn danh sách.
  - Bàn phím ảo 4×2 dưới màn hình: `F1 ▲ LỰC MAX F2` / `◀ ▼ ▶ OK`. Mỗi nút nhấn khi chạm, nhả khi buông như phím thật (giữ OK để tăng lực, buông là bắn). LỰC MAX mở hộp "Lực bắn" của game ngay trong trận (giống Menu > LỰC MAX): gõ lực rồi OK.
  - Thanh công cụ của app được ẩn. Vuốt từ mép trên màn hình game xuống để hiện lại.
  - Ô nhập tên và mật khẩu dùng bàn phím iPhone.
- Tên app **Army 2 Coffee**, icon lấy từ client, bundle id `vn.army2coffee.game`.
- Bỏ quyền iCloud, để công cụ ký (eSign, Scarlet, AltStore, Sideloadly) không bị lỗi.
- Workflow GitHub Actions build file IPA trên máy Mac của GitHub.

## Build ra file IPA

1. Vào tab **Actions** → **Build Army 2 Coffee iOS** → **Run workflow**, rồi chọn loại:
   - `thuong`: IPA chưa ký, để ký bằng eSign, Scarlet, AltStore hoặc Sideloadly.
   - `trollstore`: file `.tipa` có JIT cho TrollStore (chạy mượt hơn).
   - `ca-hai`: build cả hai.
2. Chạy xong (khoảng 15–30 phút), tải **Army2Coffee-iOS** ở mục Artifacts.

Mỗi lần push lên nhánh `main` cũng tự build bản `thuong`.

## Đổi client mới

Thay file `Bundled/Army2Coffee.jar` rồi push. App mới sẽ tự cập nhật client khi mở, vẫn giữ đăng nhập đã lưu.

## Bản "thuần" (không giả lập): iOS, Android, Windows

Thư mục `native/` dịch sẵn client J2ME (`Bundled/Army2Coffee.jar`) sang C rồi sang mã máy (`tools/j2c.py` + runtime C + Boehm GC), không cần máy ảo Java nên chạy mượt và nhẹ hơn nhiều.

| Bản | Workflow | Kết quả |
| --- | --- | --- |
| iOS (IPA) | **Build Army 2 Coffee iOS (thuan)** (`build-native.yml`) | release `ios-thuan-moi-nhat` |
| Android (APK, arm64-v8a + armeabi-v7a + x86_64, Android 5+) | **Build Army 2 Coffee Android + Windows (thuan)** (`build-native-khac.yml`) | nhánh `ban-build-android`: `Army2Coffee-unsigned.apk` + ảnh chụp/nhật ký chạy thử trên emulator |
| Windows (EXE 64-bit, Windows 7 SP1+) | cùng workflow trên | nhánh `ban-build-windows` (và artifact `Army2Coffee-Windows`) |

Cả hai workflow tự chạy khi push thay đổi trong `native/` hoặc `Bundled/Army2Coffee.jar` lên `main`.

**Ký APK:** repo công khai nên khóa ký **không** nằm trong repo/CI. CI chỉ ra APK chưa ký (và một APK ký khóa thử để chạy emulator). Bản phát hành ký ở máy có khóa:

```
python3 native/tools/ky_apk.py Army2Coffee-unsigned.apk Army2Coffee.apk army2coffee.p12 army2coffee matkhau.txt
```

(cần Python 3 + `pip install cryptography` + JDK cho `jarsigner`; hoặc dùng `apksigner` của Android SDK). Phải luôn ký cùng một khóa thì người chơi mới cài đè được bản mới mà giữ dữ liệu.

**Chạy thử:** `native/tools/android_ci_test.sh` cài APK lên emulator rồi thao tác thật bằng `adb input` (chạm bàn phím ảo, ô nhập tên/mật khẩu, gõ chữ, cho Android hủy rồi tạo lại màn hình), chụp màn hình và đo khung hình/RAM/CPU. Bản Windows có chế độ tự kiểm thử: `Army2Coffee.exe --lenh <tệp lệnh> --ra <thư mục ảnh> --du-lieu <thư mục dữ liệu>`.

## Giấy phép

Mã nguồn app là GPL-3.0 (xem `LICENSE`). Nếu phát file IPA cho người khác thì phải cho họ lấy được mã nguồn tương ứng, tức là repo này. Client `Army2Coffee.jar` là phần riêng của server.
