# Avatar 250 (client 2.5.8) – cài lên VPS bằng một lệnh

Chạy trên VPS Ubuntu / Debian (đăng nhập SSH bằng app Termius trên điện thoại cũng được):

    curl -fsSL https://raw.githubusercontent.com/nookc/army-ios/avatar-vps/cai_dat_avatar.sh | sudo bash -s -- MAT_KHAU_GOI

- `goi/avatar.enc.*`: gói server + database + ảnh + trang web, **mã hóa AES-256** (openssl, PBKDF2 300 000 vòng).
  Không có mật khẩu gói thì không mở được; mật khẩu do chủ server giữ, không có trong repo.
- `cai_dat_avatar.sh`: cài Java 17 (+ MariaDB nếu VPS chưa có MySQL/MariaDB), nạp database `avatar_2x`, chạy server game
  (cổng 19128) và trang web + đăng ký tài khoản (cổng 80, bận thì 8080) thành dịch vụ tự khởi động.
  Không xóa / không sửa database khác, không đụng tới web server có sẵn. Chạy lại lệnh = cập nhật, giữ dữ liệu người chơi.

## Dùng tên miền thay cho IP

Thêm `MAY_CHU=ten.mien.cua.ban` trước `bash`: APK / IPA / JAR trên trang tải được ghi tên miền (mã hóa) thay cho IP.

    curl -fsSL https://raw.githubusercontent.com/nookc/army-ios/avatar-vps/cai_dat_avatar.sh | sudo MAY_CHU=ten.mien.cua.ban bash -s -- MAT_KHAU_GOI

Tên miền cho game phải trỏ thẳng về VPS (bản ghi A). Nếu dùng Cloudflare thì để **DNS only** (đám mây xám), vì game chạy cổng TCP 19128.
Trang web có thể dùng tên miền khác bật proxy (đám mây cam); `web.py` tự lấy IP thật của người dùng qua `CF-Connecting-IP`.

## Trang quản trị

Vào `http://ten.mien.cua.ban/quan-tri/` bằng tài khoản admin (mật khẩu in ra lúc cài lần đầu, xem `/opt/avatar/THONG_TIN.txt`).
Giao diện sáng / tối, dùng tốt trên điện thoại. Mọi thao tác được ghi vào **Nhật ký**.

- **Tổng quan**: người online, tài khoản, tiền nạp hôm nay / 30 ngày, đơn nạp đang chờ, thao tác gần đây.
- **Người chơi**: tìm / lọc / sắp xếp; trang từng người: cộng trừ xu, lượng, xèng + tặng đồ, nhắn tin, mời ra khỏi game,
  khóa (có thời hạn, lý do) / mở khóa, đặt mật khẩu mới, xóa tài khoản (sao lưu trước, khôi phục được).
- **Nạp tiền**: gói nạp (giá, xu, lượng, xèng, vật phẩm); người chơi nạp ở trang Tài khoản bằng mã QR chuyển khoản.
  Thanh toán tự động qua SePay: tạo webhook trỏ tới `http(s)://ten.mien.cua.ban/api/pl/sepay`, chứng thực API Key
  (tạo khóa ở tab "Tài khoản nhận & SePay"). Chưa cài SePay thì xác nhận tay từng đơn.
- **Thông báo**: gửi chạy chữ / hộp thông báo cho mọi người đang online. **Máy chủ**: bật / tắt bảo trì (báo trước rồi mời ra,
  chỉ quản trị viên vào được game, khởi động lại vẫn giữ).
- **Cửa hàng & giá**: giá từng vật phẩm, đồ ăn, bộ thời trang. **Cài đặt game**: nông trại, cây khế, auto, sòng bài
  (Bầu Cua, Tiến Lên, Phỏm, Kim Cương). **Nâng cấp**: tỉ lệ / giá đập đồ.
- **Tặng quà**, **Mã quà tặng**, **Sự kiện** đua top, **Người ảo**, **Đấu trường boss**, **Bắn cá**.
- **Trang web**: link nhóm Zalo / Zalo admin / Facebook trên trang chủ; tải APK / JAR mới lên – máy chủ tự gắn địa chỉ (mã hóa) rồi thay file tải.
  iPhone: dán link cài (TestFlight hoặc link tải trực tiếp `https://` / `itms-services://`) – nút iPhone trên trang tải mở link này,
  kèm hướng dẫn cài theo loại link; bỏ link thì quay lại tải file IPA.
  Chạy lại lệnh cài vẫn giữ các link này, và giữ app đã tải lên nếu nó mới hơn bản trong gói.
