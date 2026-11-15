# Avatar cho iOS (nhánh `avatar-ios`)

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

Push lên nhánh `avatar-ios` là GitHub Actions (**Build Avatar iOS**) tự build trên máy Mac của GitHub (khoảng 20–40 phút):

- Release **avatar-ios-moi-nhat**: file `Avatar.ipa` chưa ký, ký bằng eSign, Scarlet, AltStore hoặc Sideloadly.
- Nhánh **avatar-ban-build**: IPA, log build, ảnh chụp chạy thử trên iOS Simulator (`test/*.png`: app tự bấm Đổi tài khoản, gõ tên/mật khẩu bằng đường bàn phím iPhone, chọn thành phố, thử kết nối).
- Chạy tay (Actions → Build Avatar iOS → Run workflow, chọn nhánh `avatar-ios`) thì build thêm file `.tipa` cho TrollStore (có JIT, chạy mượt hơn).

## Đổi client mới

Thay `Bundled/Avatar258.jar` (bản đã vá `-1000`: `java -cp asm-9.7.1.jar:. PatchIosKeys Avatar258.jar Bundled/Avatar258.jar`) rồi push. App mới tự cập nhật client khi mở, giữ tài khoản đã lưu.

## Giấy phép

Mã nguồn app là GPL-3.0 (xem `LICENSE`). Phát file IPA cho người khác thì phải cho họ lấy được mã nguồn tương ứng (nhánh này). Client `Avatar258.jar` là phần riêng của server.
