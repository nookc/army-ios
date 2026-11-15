# Avatar cho iOS + Android (nhánh `avatar-ios`)

## Bản chính: bản "thuần" (mã máy, không giả lập) – 11/2026

Client J2ME **Avatar 2.5.8** (có Bàn Bắn Cá 3D) được dịch sẵn sang C rồi biên dịch thành **mã máy** của điện thoại
bằng bộ dịch `native/` của repo (cùng bộ với bản Army thuần): không có bộ thông dịch / giả lập J2ME nào chạy trên máy,
nên mượt và nhẹ hơn hẳn bản phoneME bên dưới.

| | iPhone | Android |
|---|---|---|
| Vỏ app | `native/platform/avatar-ios/App.m` | `native/platform/avatar-android/` (Java) + `native/platform/android/jni/android.c` (JNI dùng chung) |
| Build | `native/tools/build_avatar_ios.sh` | `native/tools/build_avatar_android.sh` |
| Chạy thử CI | `native/tools/avatar_sim_test.sh` (iOS Simulator) | `native/tools/avatar_android_test.sh` (emulator Android 11) |
| Kết quả | `Avatar-Thuan.ipa` (ARM64, iOS 15+, chưa ký) | `Avatar-unsigned.apk` (arm64-v8a, armeabi-v7a, x86_64; Android 5.0+) |

- Workflow **Build Avatar iOS + Android (thuan)** (`.github/workflows/build-avatar-native.yml`) tự chạy khi push thay đổi
  trong `native/`, `Bundled/Avatar258.jar`, `Bundled/server.txt`. Kết quả ở nhánh **avatar-thuan-build** (IPA, APK, ảnh chạy thử,
  log) và release **avatar-thuan-moi-nhat** (IPA). Chạy thử trên Simulator / emulator: app tự nhập máy chủ giả của CI,
  bấm "Đổi tài khoản", gõ tên + mật khẩu qua đường bàn phím thật, chọn thành phố và kết nối (máy chủ giả ghi lại gói tin).
- APK trên CI **chưa ký** (`Avatar-test.apk` chỉ ký khóa tạm để chạy emulator). Bản phát hành ký bằng khóa `avatar.jks`
  (giữ ngoài repo vì repo công khai) để cài đè lên bản Android cũ cùng gói `vn.avatar.diemky`.
- Màn hình: Canvas rộng 240 / 320 / 360 (chọn ở màn cài đặt), cao theo tỉ lệ màn hình. **Bàn bắn cá (`FishGame`) vẽ ở
  độ phân giải gấp đôi** (runtime: `j2n_set_hd("FishGame", 2)`), chạm được nhân lên tương ứng.
- Máy chủ: client gốc nối `127.0.0.1`; runtime chuyển sang máy chủ người chơi chọn (`j2n_set_server("host[:cổng]")`, có cổng
  thì mọi thành phố đều vào cổng đó). Địa chỉ điền sẵn: `Bundled/server.txt` (iOS) / `assets/server.txt` (Android, `DoiMayChu.jar`
  ghi được).
- Nút **Aa** mép phải: chạm = bàn phím điện thoại (gửi `-1000` rồi từng ký tự, xóa `-8`, xuống dòng `-5`); giữ (iPhone 1 giây,
  Android 2 giây) = cài đặt máy chủ / cỡ giao diện.
- Thử nhanh trên Linux (không cần điện thoại): `cd native && make JAR=../Bundled/Avatar258.jar`, rồi
  `J2N_SERVER=127.0.0.1:19128 J2N_HD=FishGame:2 build/army2n ../Bundled/Avatar258.jar main.GameMidlet 240 533 lenh.live ra/`
  (lệnh: `tap X Y`, `key N`, `type:abc`, `shot TEN`, `sleep MS`, `quit` – xem `native/platform/linux/main.c`).

---

## Bản cũ: phoneME (giả lập J2ME)

App iOS chạy client J2ME **Avatar 2.5.8** (có Bàn Bắn Cá 3D), dựng trên bộ chạy **phoneME iOS** của repo này (GPL-3.0, tác giả gốc Duy Pham: https://github.com/phd051199/phoneME-iOS). Nhánh này tách riêng, không đụng nhánh `main` (Army2.5.0).

So với bản Army trên `main`:

- Gói sẵn client `Bundled/Avatar258.jar`. Mở app là vào game luôn.
  - Màn hình dọc, rộng 240 (giao diện to như điện thoại Java), cao vừa hết màn hình. Chơi bằng cảm ứng như bản cảm ứng của Avatar, không có bàn phím ảo.
  - Nút **Aa** ở mép phải: chạm để bật/tắt bàn phím iPhone gõ chữ vào ô chữ của game (tên, mật khẩu, chat). **Giữ 1 giây** để đổi địa chỉ máy chủ.
- **Địa chỉ máy chủ**: client gốc kết nối `127.0.0.1`; app tự chuyển sang máy chủ đã cài (`PHONEME_LOOPBACK_REDIRECT_HOST/PORT` trong `Core/src/network/PosixNetworkAdapter.cpp`). Lần đầu mở app sẽ hỏi IP/tên miền (vd `123.45.67.89:19128`).
  - Muốn app có sẵn địa chỉ (người chơi khỏi gõ): ghi `IP:cổng` vào `Bundled/server.txt` rồi push.
- Client đã vá thêm mã phím riêng `-1000` trong `main.a.keyPressed`: chuyển ô chữ sang gõ thẳng ký tự (không T9) trước khi app gửi chữ từ bàn phím iPhone (`PatchIosKeys`). Không có mã này thì gõ số trên bàn phím iPhone sẽ ra chữ T9 (gõ "12345" thành ".adgj").
- Tên app **Avatar**, bundle id `vn.avatar.diemky`, icon `Bundled/AppIcon.png`.

## Build ra file IPA

Workflow **Build Avatar iOS** (bản phoneME) nay **chỉ chạy tay** (Actions → Build Avatar iOS → Run workflow). Nó build trên máy Mac của GitHub (khoảng 20–40 phút):

- Release **avatar-ios-moi-nhat**: file `Avatar.ipa` chưa ký, ký bằng eSign, Scarlet, AltStore hoặc Sideloadly.
- Nhánh **avatar-ban-build**: IPA, log build, ảnh chụp chạy thử trên iOS Simulator (`test/*.png`: app tự bấm Đổi tài khoản, gõ tên/mật khẩu bằng đường bàn phím iPhone, chọn thành phố, thử kết nối).
- Chạy tay (Actions → Build Avatar iOS → Run workflow, chọn nhánh `avatar-ios`) thì build thêm file `.tipa` cho TrollStore (có JIT, chạy mượt hơn).

## Đổi client mới

Thay `Bundled/Avatar258.jar` (bản đã vá `-1000`: `java -cp asm-9.7.1.jar:. PatchIosKeys Avatar258.jar Bundled/Avatar258.jar`) rồi push. App mới tự cập nhật client khi mở, giữ tài khoản đã lưu.

## Giấy phép

Mã nguồn app là GPL-3.0 (xem `LICENSE`). Phát file IPA cho người khác thì phải cho họ lấy được mã nguồn tương ứng (nhánh này). Client `Avatar258.jar` là phần riêng của server.
