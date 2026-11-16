# Nhật ký AI: Thiết kế & viết Unit Test JUnit 5 cho Controller (Tuần 2 - Y3)

## 1. Ngữ cảnh & Mục tiêu
- Theo checklist Tuần 2: viết Unit Test cho Controller trong `src/test/java/.../controllers/`, dùng
  `@ParameterizedTest` + `@CsvSource` để kiểm tra biên Form, dùng `assertThrows()` để kiểm thử
  ngoại lệ bắt lỗi nghiệp vụ, đạt `mvn test` 100% Pass.
- Phạm vi chọn: 2 controller xác thực `DangKyServlet` (`/dangky`) và `DangNhapServlet`
  (`/dangnhap`), vì đã có sẵn `TC_REG_*`/`TC_AUTH_*` tương ứng trong `docs/TEST_CASES.md` từ
  Tuần 1 — không chọn `ShopProductServlet` (CRUD sản phẩm) để kịp tiến độ tuần này.
- Ràng buộc quan trọng: Controller dùng DAO khởi tạo trực tiếp (`new AccountDAOImpl()`) bên trong
  `doPost`, không inject qua constructor, nên không thể mock tầng DAO mà không sửa code sản phẩm.
  => Quyết định: chỉ viết **Unit Test** cho các nhánh validate chạy **trước khi chạm CSDL thật**
  (độ dài/khoảng trắng mật khẩu, định dạng username, rate-limit), để test chạy nhanh, không phụ
  thuộc mạng/CSDL — khớp với rủi ro đã ghi trong `docs/TEST_PLAN.md`.

## 2. Câu lệnh đã gửi cho AI (Prompt)
> "Tuần 2 (09/11-15/11): Viết Unit Test cho Controller trong controllers/ (Xong Y3).
> @ParameterizedTest + @CsvSource kiểm tra biên Form. assertThrows() kiểm thử ngoại lệ bắt lỗi
> nghiệp vụ. Sản phẩm: thư mục src/test/java/.../controllers/, lệnh mvn test đạt 100% Pass, file
> docs/ai-logs/01_test_design_ai.md." (chụp từ bảng lộ trình 6 tuần của giảng viên)
>
> Theo sau đó: "Xác nhận phạm vi Unit Test Tuần 2: chỉ DangKyServlet + DangNhapServlet" (xác nhận
> phạm vi sau khi AI hỏi lại vì repo có 80+ servlet, không thể test hết trong 1 tuần).

## 3. Kết quả do AI sinh ra (Raw Output)
- Thêm dependency `org.mockito:mockito-core:5.14.2` (scope test) vào `pom.xml` để giả lập
  `HttpServletRequest`/`HttpServletResponse`/`RequestDispatcher` mà không cần Tomcat thật.
- Tạo 2 lớp "harness" trong `src/test/java/org/example/controllers/`
  (`DangKyServletTestHarness.java`, `DangNhapServletTestHarness.java`): `doGet`/`doPost` của
  `HttpServlet` là `protected`, harness nằm cùng package sản phẩm để gọi được, rồi lộ ra 1 hàm
  `public` cho test gọi tới từ package `com.poly.sof309.controllers` (không đụng code `src/main`).
- `DangKyServletTest.java`:
  - `testPasswordBoundary` (`@ParameterizedTest @CsvSource`, 7 dòng dữ liệu): biên độ dài mật khẩu
    (rỗng, toàn khoảng trắng, 7/8/16/17 ký tự) và chứa khoảng trắng giữa chuỗi. Dùng `username="ab"`
    (luôn sai định dạng) làm "lính canh": nếu mật khẩu hợp lệ, servlet dừng ở bước kiểm tra username
    (không chạm DB) thay vì gọi AccountDAO — nhờ đó biết chắc nhánh mật khẩu đã PASS mà vẫn không
    cần CSDL thật.
  - `testPasswordConfirmMismatch`: `password` khác `confirm_password`.
  - `testUsernameBoundary` (`@ParameterizedTest @CsvSource`, 7 dòng dữ liệu): biên 3-30 ký tự, chỉ
    cho phép chữ/số/gạch dưới (2, 3, 30, 31 ký tự; chứa khoảng trắng; chứa dấu `-`; chứa `_`). Vì
    một số dòng có username hợp lệ (sẽ đi tiếp tới bước rate-limit rồi mới tới DB), test **chủ
    động khoá trước** bucket rate-limit gửi OTP (`RateLimitUtil.recordFailure(...)`) cho IP giả
    riêng của từng lần gọi — nhờ vậy servlet luôn dừng lại ở rate-limit, không gọi AccountDAO thật.
  - `testMissingPasswordParamThrowsNpe` (`assertThrows`): phát hiện lỗi thật — xem mục 4.
- `DangNhapServletTest.java`: 3 test cho `doGet` (thông điệp theo query string `?registered=1`,
  `?error=account_blocked`, không có tham số nào), 2 test cho `doPost` dùng cùng kỹ thuật "khoá
  trước rate-limit" để kiểm tra 2 cổng chặn brute-force (theo IP, theo tài khoản có chuẩn hoá
  hoa/thường + khoảng trắng) mà không cần đăng nhập thật vào CSDL.
- Kết quả `mvn test`: `Tests run: 16, Failures: 0` (DangKyServletTest) +
  `Tests run: 5, Failures: 0` (DangNhapServletTest) — cộng với 2 test mẫu có sẵn, tổng 100% Pass.

## 4. Chỉnh sửa của Sinh viên & Lý do sửa
- **Phát hiện và đã sửa lỗi thật trong `DangKyServlet.java`:** dòng `password = password.trim();`
  không kiểm tra `password == null` trước khi gọi `.trim()`. Nếu form gửi lên thiếu hẳn field
  `password` (ví dụ JS phía client bị tắt, hoặc client cố tình bỏ qua), servlet ném
  `NullPointerException` thay vì trả về thông báo nghiệp vụ "Mật khẩu không được để trống!" như
  trường hợp `password=""`. Đã sửa bằng `password = password == null ? "" : password.trim();`
  (tương tự cho `confirm_password`), giống cách các trường `fullname`/`phone` đã được bảo vệ null
  sẵn ở ngay phía dưới trong cùng file. Test tương ứng đổi từ `assertThrows(NullPointerException)`
  sang xác nhận hành vi an toàn sau khi sửa (`testMissingPasswordParamIsHandledGracefully`).
- Để vẫn giữ 1 bài `assertThrows()` thật sự kiểm thử ngoại lệ sau khi lỗi trên đã được vá, đổi
  sang kiểm thử `RateLimitUtil.getClientIp(null)` — tiện ích chống brute-force mà cả 2 controller
  đều gọi ngay những dòng đầu của `doPost()` — phải ném `NullPointerException` khi request null.
- Ban đầu định viết test gọi thẳng `servlet.doPost(request, response)` — AI sinh ra bị lỗi biên
  dịch vì `doPost`/`doGet` là `protected` và test nằm khác package với servlet. Đã sửa bằng cách
  thêm 2 class harness (mục 3) thay vì đổi package test (giữ đúng cấu trúc thư mục
  `src/test/java/com/poly/sof309/controllers/` theo quy chuẩn Tuần 1-6 của đề bài).
- Cân nhắc dùng `assertThrows` cho các trường hợp khác (vd `RateLimitUtil.getClientIp(null)` ném
  NPE, hoặc `BCrypt` ném `IllegalArgumentException` với hash sai định dạng) nhưng không chọn vì
  xa rời ngữ cảnh "lỗi nghiệp vụ của Controller" hơn so với lỗi thiếu tham số bắt buộc ở trên.
