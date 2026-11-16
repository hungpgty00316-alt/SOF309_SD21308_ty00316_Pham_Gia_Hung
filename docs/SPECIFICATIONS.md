# ĐẶC TẢ NGHIỆP VỤ WEB CÁ NHÂN (SPECIFICATIONS)
> *Dùng làm ngữ cảnh (Context) gửi cho AI để sinh Test Plan & Test Case chính xác theo dự án riêng của bạn.*

---

## 1. THÔNG TIN DỰ ÁN
* **Tên đề tài:** FoodManage — Website đặt đồ ăn trực tuyến (food ordering & delivery platform).
* **Công nghệ:** Java 17 (Jakarta Servlet 6.0 + JSP/JSTL), chạy trên Tomcat 10.1, CSDL Microsoft SQL Server (JDBC `mssql-jdbc`), WebSocket (Jakarta WebSocket) cho thông báo & tracking đơn hàng theo thời gian thực.
* **URL chạy local:** `http://localhost:8080` (context root `/`, build bằng `mvn cargo:run`).
* **Vai trò người dùng (`Account.roleId`):**
  | roleId | Vai trò | Trang chủ sau đăng nhập |
  | :---: | :--- | :--- |
  | 1 | SUPER_ADMIN (quản trị hệ thống) | `/tong-quan` |
  | 2 | SHOP (chủ cửa hàng) | `/shop` |
  | 3 | USER (khách hàng) | `/user/home` |
  | 4 | SHIPPER (người giao hàng) | `/shipper/donhang` |

## 2. QUY TẮC NGHIỆP VỤ (BUSINESS RULES)

### 2.1 Đăng ký tài khoản (`DangKyServlet` — `/dangky`)
* `username`: 3–30 ký tự, chỉ gồm chữ, số, dấu gạch dưới (`^[a-zA-Z0-9_]{3,30}$`), không được trùng tài khoản đã tồn tại.
* `password`: 8–16 ký tự, không chứa khoảng trắng; `confirmPassword` phải khớp `password`.
* `email`: không được trùng email đã đăng ký.
* Mật khẩu được băm bằng **BCrypt** (`BCrypt.hashpw(password, BCrypt.gensalt(12))`) trước khi lưu — không bao giờ lưu plaintext.
* Sau khi qua hết các kiểm tra, hệ thống gửi **mã OTP qua email** để xác thực trước khi tài khoản được kích hoạt hoàn toàn.

### 2.2 Đăng nhập (`DangNhapServlet` — `/dangnhap`)
* Kiểm tra đúng `username`/`password` (so khớp BCrypt) → thành công thì cấp session mới (`req.changeSessionId()` chống session fixation) và chuyển hướng theo `roleId` (bảng mục 1).
* Sai thông tin → báo lỗi "Tên đăng nhập hoặc mật khẩu không đúng!".
* **Rate limiting chống brute-force:**
  * Theo IP: khoá 15 phút sau **5 lần** sai liên tiếp trong cửa sổ 15 phút.
  * Theo tài khoản (chặn tấn công phân tán nhiều IP vào 1 username): khoá sau **10 lần** sai liên tiếp.
* Tài khoản bị **đình chỉ** (`isDeleted = true`) hoặc **khoá** (`staTus = BLOCKED`, ví dụ do bom hàng nhiều lần) sẽ không đăng nhập được — hiển thị lý do đình chỉ, cho phép gửi **kháng nghị (appeal)**.

### 2.3 Quản lý sản phẩm — CRUD (`ShopProductServlet` — `/shop/products`, chỉ `roleId = 2` và phải sở hữu Shop)
* **Thêm mới:** `productName` bắt buộc (≤ 255 ký tự), `description` ≤ 2000 ký tự, phải chọn `categoryId` hợp lệ, phải nhập **ít nhất 1 size với giá > 0**. Sản phẩm mới luôn ở trạng thái **`PENDING_REVIEW`** (chờ Super Admin duyệt), bất kể status gửi lên từ form.
* **Sửa:** đồng bộ danh sách size theo tên (không phân biệt hoa/thường) — size trùng tên thì cập nhật giá/tồn kho, size mới thì tạo, size bị bỏ thì xoá (trừ khi đã phát sinh trong `Order_Details` thì bỏ qua để không vỡ ràng buộc khoá ngoại).
* **Xoá:** soft-delete (`isDeleted = true`), sản phẩm chuyển vào "Thùng rác" (`?action=trash`) và có thể **khôi phục** (`restore`).
* `stockQuantity`: `null` nghĩa là không giới hạn tồn kho; nếu nhập thì phải `>= 0`.

### 2.4 Giỏ hàng & Thanh toán (`CartServlet`, `CheckoutServlet` — `/checkout`)
* Giỏ hàng gồm các dòng sản phẩm (kèm size, topping tuỳ chọn); giá áp dụng **Flash Sale** nếu sản phẩm đang khuyến mãi.
* Phí giao hàng = phí cố định (15.000đ) + phí theo km (lấy từ `SystemConfig.shippingFeePerKm`, mặc định 5.000đ/km) tính trên khoảng cách từ Shop đến địa chỉ giao; **khoảng cách giao tối đa 20km**, vượt quá thì không cho đặt.
* Có thể áp dụng **Voucher** giảm giá (do Super Admin quản lý tại `/admin/vouchers`, có thể bật/tắt hiệu lực).
* Phương thức thanh toán: **COD** (tiền mặt khi nhận hàng) hoặc **PayOS** (quét QR chuyển khoản qua cổng thanh toán PayOS, gọi REST API bằng `client_key/api_key/checksum_key` riêng của từng Shop).
* Đơn hàng sau khi đặt được theo dõi real-time qua WebSocket (`TrackingEndpoint`, `NotificationEndpoint`).

### 2.5 Vai trò Shipper
* Đăng ký làm shipper (`DangKyShopServlet`/`Dangkyshipperservlet`) cần Super Admin **duyệt hồ sơ** (giấy tờ, bằng lái) trước khi được nhận đơn.
* Shipper nhận đơn (`ShipperAcceptOrderServlet`), cập nhật trạng thái giao hàng, có ví điện tử và có thể **yêu cầu rút tiền** — yêu cầu rút tiền cần Super Admin duyệt (`DuyetRutTienShipperServlet`).

### 2.6 Vai trò Super Admin
* Duyệt/từ chối hồ sơ đăng ký Shop và Shipper.
* Quản lý Voucher, kiểm duyệt bình luận/nội dung vi phạm, xử lý khiếu nại & hoàn tiền (`ComplaintServlet`, `RefundRequestServlet`), duyệt yêu cầu rút tiền của Shop/Shipper.
* Xem nhật ký thao tác hệ thống (`AuditLogServlet`) và báo cáo vận hành/doanh thu.
* Xét duyệt **kháng nghị** của tài khoản bị khoá/đình chỉ (`AppealReviewServlet`).

## 3. YÊU CẦU PHI CHỨC NĂNG (liên quan đến kiểm thử)
* Mật khẩu không bao giờ lưu plaintext (BCrypt); session cookie `HttpOnly` + `SameSite=Lax`.
* Có `CsrfFilter` chống giả mạo yêu cầu (CSRF token) cho các form thay đổi dữ liệu.
* Có `AuditLogAuthFilter`/`AuditLogService` ghi log cho các thao tác nhạy cảm (đăng nhập sai nhiều lần, khoá tài khoản...).
* Trang lỗi chung (`error.html`) không lộ stack trace cho người dùng cuối (400/403/404/405/500).
