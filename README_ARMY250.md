# Army2.5.0 cho iOS

App iOS chạy client Army2.5.0 (file J2ME `.jar`). Dựng trên **phoneME iOS** (bộ chạy J2ME mã nguồn mở, giấy phép GPL-3.0, tác giả Duy Pham: https://github.com/phd051199/phoneME-iOS).

So với bản gốc, repo này thêm:

- Gói sẵn client `Bundled/Army250.jar`. Lần đầu mở app sẽ tự nhập client vào thư viện, mỗi lần mở app là vào game luôn.
  - Màn hình dọc, rộng 320, cao vừa phần trên bàn phím. Dùng cảm ứng, vuốt lên xuống để cuộn danh sách.
  - Bàn phím ảo 4×2 dưới màn hình: `F1 ▲ LỰC MAX F2` / `◀ ▼ ▶ OK`. Mỗi nút nhấn khi chạm, nhả khi buông như phím thật (giữ OK để tăng lực, buông là bắn). LỰC MAX mở hộp "Lực bắn" của game ngay trong trận (giống Menu > LỰC MAX): gõ lực rồi OK.
  - Thanh công cụ của app được ẩn. Vuốt từ mép trên màn hình game xuống để hiện lại.
  - Ô nhập tên và mật khẩu dùng bàn phím iPhone.
- Tên app **Army2.5.0**, icon lấy từ client, bundle id `vn.army250.game`.
- Bỏ quyền iCloud, để công cụ ký (eSign, Scarlet, AltStore, Sideloadly) không bị lỗi.
- Workflow GitHub Actions build file IPA trên máy Mac của GitHub.

## Build ra file IPA

1. Vào tab **Actions** → **Build Army2.5.0 iOS** → **Run workflow**, rồi chọn loại:
   - `thuong`: IPA chưa ký, để ký bằng eSign, Scarlet, AltStore hoặc Sideloadly.
   - `trollstore`: file `.tipa` có JIT cho TrollStore (chạy mượt hơn).
   - `ca-hai`: build cả hai.
2. Chạy xong (khoảng 15–30 phút), tải **Army250-iOS** ở mục Artifacts.

Mỗi lần push lên nhánh `main` cũng tự build bản `thuong`.

## Đổi client mới

Thay file `Bundled/Army250.jar` rồi push. App mới sẽ tự cập nhật client khi mở, vẫn giữ đăng nhập đã lưu.

Client hiện tại (gửi phiên bản `2.3.0+cx+sl+ka+v250+bang+hp`, hiện 2.5.0) có thêm:

- 4 phòng boss bậc cao: T-Rex Cao Thủ (băng), T-Rex Chiến Thần (lửa), T-Rex Thách Đấu (rồng băng), Bạo Chúa Âm Minh (rồng lửa); chiến trường băng có tuyết rơi.
- 5 hộp quà Tinh Anh → Âm Minh có hiệu ứng động trong rương (Âm Minh có rồng sương bay vòng).
- HP thật khi máu nhân vật trên 32.767 (không còn hiện âm / mất thanh máu đầu trận).
- Quả cầu lửa + rồng đồ Thách Đấu vẫn hiện khi mặc cải trang.
- v8g: nhân vật thứ 12 Hỗn Độn Thú (đạn sao băng nảy như bóng, đường ngắm vẽ cả các lần nảy, bộ trang bị 5 món riêng, cải trang khớp thân); phòng U Minh Giới: boss Hỗn Độn Thú trên map Đảo Bay (đảo bay trên biển dung nham, rồng bay lượn). Client gửi thêm `+nv11+nv12`.
- v8h: tặng trang bị cho bạn bè (đồ chế tác từ Cao Thủ trở lên, mã bảo vệ, bạn đồng ý mới chuyển), mục MÃ BẢO VỆ trong XEM THÔNG TIN; sảnh chờ boss bậc cao thu nhỏ 70%; Hỗn Độn Thú vẽ lại chắc + nét hơn; boss U Minh Giới mới (cánh lửa xanh / đỏ, trúng đầu / chân). Client gửi thêm `+tang`.
- v8h-4: kết nối máy chủ theo tên miền `army250new.com` (danh sách máy chủ `http://army250new.com/s`, mặc định `army250new.com:8122`) - trong app không còn địa chỉ IP; đổi VPS chỉ cần trỏ lại tên miền.
- v8h-4b: Tốc độ trận x2 .. x10 tăng cả hoạt ảnh (nhân vật, cải trang, boss, hiệu ứng) theo tốc độ, không chỉ đạn / di chuyển.
- v8h-5: Tốc độ trận xN = cả trận nhanh gấp N, kể cả thao tác của mình (đi, giữ lực, giữ phím ngắm), chữ bay, hiệu ứng vẽ; bấm nhanh phím ngắm vẫn nhích 1 độ.
- v8h-6: Bỏ hẳn nạp tiền trong app: mục NẠP TIỀN ở menu chính thành MÃ QUÀ TẶNG (mở thẳng ô nhập mã, gửi `giftcode`). ABOUT ME mở thẳng trang web của game, đăng ký biệt đội mở trang lập đội trên web; cả hai không thoát game (link server gửi chỉ dùng nếu cùng tên miền với web).
- v8h-7: Boss 5 phòng bậc cao (T-Rex Cao Thủ / Chiến Thần, rồng Thách Đấu / Âm Minh, Hỗn Độn Thú) vẽ to hơn trong trận theo hệ số server gửi (mss 111 act 25, mặc định x1,3; sảnh chờ giữ nguyên). Máu cộng hiện số xanh lá; nhân vật bị trừ máu camera dừng lại cho rõ, tốc độ xN camera quay mượt như x1. Màn Trang bị cộng đúng chỉ số bộ cải trang đang mặc (act 24) thay cho +5% mỗi món. Map ma: con ma mới mọc vào ô của con đã chết (act 23) - đủ 300 con mỗi trận. Client gửi thêm `+ma`, `+ct`, `+to`.

Cần server Army2.5.0 mới (SerArmy2.jar cùng đợt); client cũ vẫn chơi được nhưng bị nhắc cập nhật khi vào 4 phòng boss mới.

## Bản "thuần" (không giả lập): iOS, Android, Windows

Thư mục `native/` dịch sẵn client J2ME (`Bundled/Army250.jar`) sang C rồi sang mã máy (`tools/j2c.py` + runtime C + Boehm GC), không cần máy ảo Java nên chạy mượt và nhẹ hơn nhiều.

| Bản | Workflow | Kết quả |
| --- | --- | --- |
| iOS (IPA) | **Build Army2.5.0 iOS (thuan)** (`build-native.yml`) | release `ios-thuan-moi-nhat` |
| Android (APK, arm64-v8a + armeabi-v7a + x86_64, Android 5+) | **Build Army2.5.0 Android + Windows (thuan)** (`build-native-khac.yml`) | nhánh `ban-build-android`: `Army250-unsigned.apk` + ảnh chụp/nhật ký chạy thử trên emulator |
| Windows (EXE 64-bit, Windows 7 SP1+) | cùng workflow trên | nhánh `ban-build-windows` (và artifact `Army250-Windows`) |

Cả hai workflow tự chạy khi push thay đổi trong `native/` hoặc `Bundled/Army250.jar` lên `main`.

**Ký APK:** repo công khai nên khóa ký **không** nằm trong repo/CI. CI chỉ ra APK chưa ký (và một APK ký khóa thử để chạy emulator). Bản phát hành ký ở máy có khóa:

```
python3 native/tools/ky_apk.py Army250-unsigned.apk Army250.apk army250.p12 army250 matkhau.txt
```

(cần Python 3 + `pip install cryptography` + JDK cho `jarsigner`; hoặc dùng `apksigner` của Android SDK). Phải luôn ký cùng một khóa thì người chơi mới cài đè được bản mới mà giữ dữ liệu.

**Chạy thử:** `native/tools/android_ci_test.sh` cài APK lên emulator rồi thao tác thật bằng `adb input` (chạm bàn phím ảo, ô nhập tên/mật khẩu, gõ chữ, cho Android hủy rồi tạo lại màn hình), chụp màn hình và đo khung hình/RAM/CPU. Bản Windows có chế độ tự kiểm thử: `Army250.exe --lenh <tệp lệnh> --ra <thư mục ảnh> --du-lieu <thư mục dữ liệu>`.

## Giấy phép

Mã nguồn app là GPL-3.0 (xem `LICENSE`). Nếu phát file IPA cho người khác thì phải cho họ lấy được mã nguồn tương ứng, tức là repo này. Client `Army250.jar` là phần riêng của server.
