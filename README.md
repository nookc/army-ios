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
