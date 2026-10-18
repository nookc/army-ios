# Army 2 Coffee cho iOS

App iOS chạy client Army 2 Coffee (file J2ME `.jar`). Dựng trên **phoneME iOS** (bộ chạy J2ME mã nguồn mở, giấy phép GPL-3.0, tác giả Duy Pham: https://github.com/phd051199/phoneME-iOS).

So với bản gốc, repo này thêm:

- Gói sẵn client `Bundled/Army2Coffee.jar`. Lần đầu mở app sẽ tự nhập client vào thư viện, mỗi lần mở app là vào game luôn.
  - Màn hình ngang, cao 360, rộng theo tỉ lệ máy, dùng cảm ứng, tắt bàn phím ảo.
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

## Giấy phép

Mã nguồn app là GPL-3.0 (xem `LICENSE`). Nếu phát file IPA cho người khác thì phải cho họ lấy được mã nguồn tương ứng, tức là repo này. Client `Army2Coffee.jar` là phần riêng của server.
