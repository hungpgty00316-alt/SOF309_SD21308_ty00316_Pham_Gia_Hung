-- Dữ liệu mẫu cho POB trên SQL Server. Mở riêng file này trong SSMS và nhấn F5.
-- Yêu cầu: schema trong docs/database.md đã được tạo.
-- Mật khẩu chung cho các tài khoản demo: PobDemo@2026. Chỉ dùng ở môi trường thử nghiệm.

USE [POB];
GO

SET NOCOUNT ON;
SET XACT_ABORT ON;

IF DB_NAME() <> N'POB'
    THROW 50003, N'Hãy tạo và chọn database POB trước khi chạy script.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    DECLARE @lockResult INT;
    EXEC @lockResult = sys.sp_getapplock
        @Resource = 'POB.seed-demo', @LockMode = 'Exclusive', @LockOwner = 'Transaction';
    IF @lockResult < 0
        THROW 50001, N'Không thể khóa tác vụ thêm dữ liệu mẫu. Hãy chạy lại.', 1;

    DECLARE @password VARCHAR(60) = '$2a$12$xn1MeD4BEDboDvDOxAaD6uLKUtbsYPWrH76z3dm.dUlOAv.P10Q.S';
    DECLARE @adminRole BIGINT = (SELECT id FROM dbo.Roles WHERE name = 'SUPER_ADMIN');
    IF @adminRole IS NULL
        THROW 50004, N'Thiếu role SUPER_ADMIN trong bảng Roles.', 1;

    IF NOT EXISTS (
        SELECT 1 FROM dbo.Accounts
        WHERE username = 'pob_demo_admin' AND email = 'pob_demo_admin@example.test'
          AND role_id = @adminRole
    )
    BEGIN
        INSERT INTO dbo.Accounts (username, password, email, full_name, role_id)
        VALUES ('pob_demo_admin', @password, 'pob_demo_admin@example.test', N'Quản trị viên mẫu', @adminRole);
        PRINT N'Đã thêm tài khoản quản trị mẫu pob_demo_admin.';
    END;

    IF EXISTS (
        SELECT 1 FROM dbo.Accounts
        WHERE username = 'pob_demo_shop' AND email = 'pob_demo_shop@example.test'
    )
    BEGIN
        COMMIT TRANSACTION;
        PRINT N'Bộ dữ liệu mẫu đã được thêm trước đó.';
        RETURN;
    END;

    DECLARE @shopRole BIGINT = (SELECT id FROM dbo.Roles WHERE name = 'ADMIN');
    DECLARE @userRole BIGINT = (SELECT id FROM dbo.Roles WHERE name = 'USER');
    DECLARE @shipperRole BIGINT = (SELECT id FROM dbo.Roles WHERE name = 'SHIPPER');
    IF @shopRole IS NULL OR @userRole IS NULL OR @shipperRole IS NULL
        THROW 50002, N'Thiếu role ADMIN, USER hoặc SHIPPER trong bảng Roles.', 1;

    INSERT INTO dbo.Accounts (username, password, email, full_name, phone, role_id)
    VALUES
        ('pob_demo_shop', @password, 'pob_demo_shop@example.test', N'Chủ quán mẫu', '0900000101', @shopRole),
        ('pob_demo_user', @password, 'pob_demo_user@example.test', N'Khách hàng mẫu', '0900000102', @userRole),
        ('pob_demo_shipper', @password, 'pob_demo_shipper@example.test', N'Tài xế mẫu', '0900000103', @shipperRole);

    DECLARE @ownerId BIGINT = (SELECT id FROM dbo.Accounts WHERE username = 'pob_demo_shop');
    DECLARE @userId BIGINT = (SELECT id FROM dbo.Accounts WHERE username = 'pob_demo_user');
    DECLARE @shipperId BIGINT = (SELECT id FROM dbo.Accounts WHERE username = 'pob_demo_shipper');

    INSERT INTO dbo.User_Addresses (account_id, label, full_address, receiver_name, receiver_phone, is_default)
    VALUES (@userId, N'Nhà', N'20 Nguyễn Huệ, phường Sài Gòn, TP. Hồ Chí Minh', N'Khách hàng mẫu', '0900000102', 1);
    DECLARE @addressId BIGINT = SCOPE_IDENTITY();

    INSERT INTO dbo.User_Profiles (account_id, default_address_id)
    VALUES (@userId, @addressId);

    INSERT INTO dbo.Shipper_Profiles (account_id, vehicle_type, verification_status, verified_at)
    VALUES (@shipperId, N'Xe máy', 'APPROVED', SYSDATETIME());

    INSERT INTO dbo.Shops (owner_id, shop_name, shop_description, shop_address, shop_phone, status, approved_at)
    VALUES (@ownerId, N'Quán Cà Phê Mẫu', N'Cà phê, trà trái cây và bánh ngọt.',
            N'12 Nguyễn Huệ, phường Sài Gòn, TP. Hồ Chí Minh', '0900000101', 'ACTIVE', SYSDATETIME());
    DECLARE @shopId BIGINT = SCOPE_IDENTITY();

    INSERT INTO dbo.Categories (shop_id, name) VALUES (@shopId, N'Đồ uống');
    DECLARE @drinkCategory BIGINT = SCOPE_IDENTITY();

    INSERT INTO dbo.Categories (shop_id, name) VALUES (@shopId, N'Bánh ngọt');
    DECLARE @cakeCategory BIGINT = SCOPE_IDENTITY();

    INSERT INTO dbo.Products (shop_id, category_id, product_name, description, stock_quantity, sold_count)
    VALUES (@shopId, @drinkCategory, N'Cà phê sữa đá', N'Cà phê phin pha với sữa đặc.', 40, 2);
    DECLARE @coffeeId BIGINT = SCOPE_IDENTITY();

    INSERT INTO dbo.Products (shop_id, category_id, product_name, description, stock_quantity)
    VALUES (@shopId, @drinkCategory, N'Trà đào cam sả', N'Trà đen với đào, cam và sả.', 35);
    DECLARE @teaId BIGINT = SCOPE_IDENTITY();

    INSERT INTO dbo.Products (shop_id, category_id, product_name, description, stock_quantity)
    VALUES (@shopId, @cakeCategory, N'Bánh tiramisu', N'Bánh kem mascarpone vị cà phê.', 15);
    DECLARE @cakeId BIGINT = SCOPE_IDENTITY();

    INSERT INTO dbo.Product_Sizes (product_id, shop_id, size_name, price)
    VALUES (@coffeeId, @shopId, N'M', 29000);
    DECLARE @coffeeSizeId BIGINT = SCOPE_IDENTITY();

    INSERT INTO dbo.Product_Sizes (product_id, shop_id, size_name, price)
    VALUES (@coffeeId, @shopId, N'L', 35000),
           (@teaId, @shopId, N'M', 39000),
           (@teaId, @shopId, N'L', 45000),
           (@cakeId, @shopId, N'Phần', 42000);

    INSERT INTO dbo.Vouchers (code, voucher_type, value, min_order_value, usage_limit)
    VALUES ('POBDEMO10K', 'FIXED', 10000, 50000, 100);

    INSERT INTO dbo.Orders (
        user_id, shop_id, shipper_id, receiver_name, receiver_phone, shipping_address,
        total_price, delivery_fee, payment_method, payment_status, status, created_at, updated_at
    )
    SELECT @userId, @shopId, @shipperId, receiver_name, receiver_phone, full_address,
           73000, 15000, 'COD', 'PAID', 'DONE', DATEADD(DAY, -1, SYSDATETIME()), DATEADD(DAY, -1, SYSDATETIME())
    FROM dbo.User_Addresses WHERE id = @addressId;
    DECLARE @orderId BIGINT = SCOPE_IDENTITY();

    INSERT INTO dbo.Order_Details (order_id, product_id, product_size_id, quantity, price)
    VALUES (@orderId, @coffeeId, @coffeeSizeId, 2, 29000);

    COMMIT TRANSACTION;
    PRINT N'Đã thêm dữ liệu mẫu POB.';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
