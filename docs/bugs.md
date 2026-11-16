# Báo cáo lỗi kết nối database

Ngày kiểm tra: 16/11/2026
Dự án: SOF309_SD21308_ty00316_Pham_Gia_Hung
Phạm vi: Các vấn đề đã quan sát trong quá trình kiểm tra kết nối database; chưa phải báo cáo kiểm thử toàn bộ ứng dụng.

## BUG-01 — SQL Server từ chối đăng nhập tài khoản pob_backend

- Mức độ ảnh hưởng: Cao — không lấy được kết nối database, các chức năng phụ thuộc database có thể không hoạt động.
- Database: POB.
- Vị trí cấu hình: src/main/resources/config.properties hoặc các biến môi trường DB_URL, DB_USER, DB_PASSWORD. Biến môi trường được ưu tiên theo ConfigUtil.
- Vị trí mở kết nối: src/main/java/org/example/utils/DBUtil.java, phương thức getConnection().

### Các bước tái hiện đã thực hiện

1. Biên dịch DBUtil và ConfigUtil từ mã nguồn hiện tại vào thư mục kiểm tra riêng.
2. Dùng driver mssql-jdbc-12.8.1.jre11.jar có sẵn trong bản build.
3. Gọi DBUtil.getConnection() với cấu hình dự án.
4. Thử lại bằng mật khẩu người dùng cung cấp, truyền tạm qua biến môi trường DB_PASSWORD.

### Kết quả thực tế ban đầu

- SQL Server phản hồi: Login failed for user 'pob_backend'.
- DBUtil.getConnection() trả về null.
- Chưa chạy được truy vấn kiểm tra do đăng nhập thất bại.
- Lần thử đầu tiên với mật khẩu mới cũng bị từ chối 

### Kết quả sau khi fix

 thành công:

```text
CONNECTION_OK database=POB SELECT_1=1
BASE_TABLE_COUNT=40
```

- Đã đăng nhập được vào POB.
- Truy vấn SELECT DB_NAME(), 1 thành công.
- Truy vấn INFORMATION_SCHEMA.TABLES trả về 40 bảng loại BASE TABLE mà tài khoản nhìn thấy.
- Chỉ chạy truy vấn đọc, không sửa dữ liệu.
- Việc đổi mật khẩu phía SQL Server không được quan sát trực tiếp; kết quả đăng nhập thành công đã được xác minh.

### Trạng thái

Đã xác minh kết nối thành công khi truyền mật khẩu qua biến môi trường tạm thời. Chưa xác minh ứng dụng đang chạy kết nối thành công với cấu hình lưu sẵn.

## BUG-02 — Lỗi kết nối bị bắt rồi trả về null

- Vị trí: src/main/java/org/example/utils/DBUtil.java, phương thức getConnection().
- Quan sát từ mã nguồn: bắt ClassNotFoundException, SQLException và IllegalStateException; chỉ in stack trace rồi trả về biến connection có thể bằng null.
- Bằng chứng runtime: khi đăng nhập thất bại, chương trình kiểm tra nhận null và báo CONNECTION_FAILED.
- Rủi ro: mã gọi có thể gặp NullPointerException nếu sử dụng kết nối mà không kiểm tra null; có thể làm khó việc nhận biết nguyên nhân gốc.
- Chưa ghi nhận NullPointerException thực tế trong phiên kiểm tra này.
- Đề xuất: truyền lỗi kết nối lên tầng gọi hoặc dùng cơ chế xử lý lỗi rõ ràng, có thông báo phù hợp cho người dùng.
- Trạng thái: Chưa sửa; không thay đổi mã nguồn trong phiên kiểm tra.

## Việc còn cần kiểm tra

1. Đồng bộ mật khẩu hợp lệ vào cấu hình mà ứng dụng thực sự sử dụng. Không ghi mật khẩu vào báo cáo hoặc đưa lên Git.
2. Kiểm tra DB_PASSWORD có ghi đè giá trị trong config.properties hay không khi chạy ứng dụng.
3. Khởi động lại ứng dụng sau khi đổi cấu hình vì ConfigUtil và DBUtil nạp cấu hình vào các trường static.
4. Kiểm tra một chức năng đọc dữ liệu trong ứng dụng để xác minh luồng hoạt động đầy đủ. Phiên này mới kiểm tra lớp kết nối và truy vấn chỉ đọc.

## Giới hạn báo cáo

- Không lưu mật khẩu trong file này.
- Không sửa cấu hình, mã nguồn hoặc dữ liệu dự án.
- Các lỗi khác của ứng dụng chưa được kiểm tra; không kết luận toàn bộ ứng dụng đã hết lỗi.
