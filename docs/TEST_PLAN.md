# KẾ HOẠCH KIỂM THỬ (TEST PLAN)

* **Dự án:** FoodManage — Website đặt đồ ăn trực tuyến (Java Servlet/JSP, SQL Server)
* **Sinh viên:** Pham Gia Hung - ty00316

---

## 1. MỤC TIÊU KIỂM THỬ
* Kiểm thử logic backend (Unit Test với JUnit 5) cho các Controller/Servlet nghiệp vụ cốt lõi: Đăng ký, Đăng nhập, CRUD Sản phẩm, Checkout.
* Kiểm thử giao diện tự động (Automation UI Test với Selenium WebDriver) cho các luồng người dùng chính.
* Tích hợp kiểm thử tự động với GitHub Actions CI/CD (chạy test mỗi lần push code).

## 2. PHẠM VI KIỂM THỬ
* **In-Scope:**
  * Đăng ký tài khoản (ràng buộc username/password/email, trùng lặp, OTP).
  * Đăng nhập (đúng/sai thông tin, rate-limit theo IP & theo tài khoản, tài khoản bị khoá/đình chỉ, chuyển hướng theo vai trò).
  * CRUD sản phẩm của Shop (thêm/sửa/xoá mềm/khôi phục, validate tên/giá/size/tồn kho, trạng thái `PENDING_REVIEW`).
  * Giỏ hàng & Checkout (tính phí giao hàng theo khoảng cách, giới hạn 20km, áp dụng voucher).
* **Out-of-Scope (đợt kiểm thử này):**
  * Kiểm thử hiệu năng (Performance/Load Test).
  * Bảo mật chuyên sâu (penetration test), tích hợp cổng thanh toán PayOS thật, gửi email thật.
  * Luồng nghiệp vụ của Shipper và Super Admin (duyệt hồ sơ, rút tiền, kiểm duyệt nội dung) — để lại cho các tuần sau nếu còn thời gian.

## 3. CHIẾN LƯỢC KIỂM THỬ
* **Unit Test (JUnit 5):** kiểm thử các hàm validate đầu vào và nhánh rẽ nghiệp vụ của Servlet/Util bằng `@ParameterizedTest` (biên dữ liệu) và `assertThrows()` (ngoại lệ nghiệp vụ), DB được giả lập/cô lập khỏi Servlet thật khi có thể.
* **UI Test (Selenium, Page Object Model):** kiểm thử end-to-end qua trình duyệt thật đối với các luồng người dùng: đăng ký, đăng nhập, CRUD sản phẩm trên giao diện Shop.
* **Môi trường kiểm thử:** chạy trên `localhost:8080` (Tomcat 10.1 qua `mvn cargo:run`), CSDL SQL Server dùng chung với môi trường phát triển (không có DB test riêng ở giai đoạn này).

## 4. TIÊU CHÍ HOÀN THÀNH (EXIT CRITERIA)
* 100% test case trong `TEST_CASES.md` được thực thi.
* 100% Unit Test (JUnit) và kịch bản Selenium đạt Pass trên nhánh `main`.
* Không còn lỗi mức **Blocker/Critical** đang mở (ví dụ: không đăng ký/đăng nhập được, tạo sản phẩm sai validate mà vẫn lưu được vào DB).
* Pipeline GitHub Actions báo xanh (Success) ở các tuần có yêu cầu CI/CD.

## 5. RỦI RO & GIẢ ĐỊNH
* Mật khẩu/API key thật (SQL Server, Gmail SMTP) nằm trong `config.properties` (đã `.gitignore`) — test cần tài khoản thật trên DB, không phải mock, nên **không chạy song song nhiều lần đăng ký trùng email/username** để tránh nhiễu dữ liệu.
* Rate-limit đăng nhập (khoá 15 phút sau 5 lần sai) có thể ảnh hưởng đến các lần chạy test lặp lại liên tiếp trên cùng IP/tài khoản — cần dùng tài khoản test riêng hoặc chờ hết thời gian khoá giữa các lần chạy.
* Việc gửi OTP phụ thuộc email thật (SMTP Gmail) — nếu không đọc được hộp thư trong lúc test tự động, chỉ xác minh ở mức "hệ thống chấp nhận yêu cầu và không lỗi 500", chưa xác minh được nội dung OTP.
