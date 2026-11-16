# SCHEMA CSDL — POB (SQL Server)
> Script tạo toàn bộ database cho FoodManage. Mọi thay đổi từng nằm rải rác trong các file
> `migration_*.sql` đã được gộp thẳng vào `CREATE TABLE` tương ứng ở đây — chạy một lần file
> này là đủ để dựng lại DB từ đầu. Phần "Ghi chú rà soát" ở cuối file liệt kê các migration còn
> **chưa** được gộp vào `migration_all.sql` (rủi ro nếu dựng DB bằng file đó thay vì file này).

```sql
-- ===
-- 0. TẠO DATABASE
-- ===
IF NOT EXISTS (SELECT * FROM sys.databases WHERE name = 'POB')
BEGIN
    CREATE DATABASE POB;
END
GO

USE POB;
GO

-- ===
-- XÓA BẢNG CŨ THEO ĐÚNG THỨ TỰ RÀNG BUỘC (từ ngọn đến gốc)
-- ===
DROP TABLE IF EXISTS Refund_Requests;
DROP TABLE IF EXISTS Shop_Withdrawals;
DROP TABLE IF EXISTS Shop_Wallet_Transactions;
DROP TABLE IF EXISTS Shop_Wallets;
DROP TABLE IF EXISTS Flash_Sales;
DROP TABLE IF EXISTS AuditLogs;
DROP TABLE IF EXISTS System_Configs;
DROP TABLE IF EXISTS Shipper_Withdrawals;
DROP TABLE IF EXISTS Shipper_Wallets;
DROP TABLE IF EXISTS Shop_Settlements;
DROP TABLE IF EXISTS FAQs;
DROP TABLE IF EXISTS Vouchers;
DROP TABLE IF EXISTS Complaints;
DROP TABLE IF EXISTS Account_Appeals;
DROP TABLE IF EXISTS Notifications;
DROP TABLE IF EXISTS BannedWords;
DROP TABLE IF EXISTS Feedback_Images;
DROP TABLE IF EXISTS Feedbacks;
DROP TABLE IF EXISTS Order_Logs;
DROP TABLE IF EXISTS Order_Detail_Toppings;
DROP TABLE IF EXISTS Order_Details;
DROP TABLE IF EXISTS Orders;
DROP TABLE IF EXISTS Cart_Item_Toppings;
DROP TABLE IF EXISTS Cart_Items;
DROP TABLE IF EXISTS Carts;
DROP TABLE IF EXISTS Combo_Items;
DROP TABLE IF EXISTS Combos;
DROP TABLE IF EXISTS Product_Images;
DROP TABLE IF EXISTS Toppings;
DROP TABLE IF EXISTS ToppingCategory_ProductCategories;
DROP TABLE IF EXISTS ToppingCategories;
DROP TABLE IF EXISTS Product_Sizes;
DROP TABLE IF EXISTS Products;
DROP TABLE IF EXISTS Categories;
DROP TABLE IF EXISTS Shops;
DROP TABLE IF EXISTS Shipper_Profiles;
DROP TABLE IF EXISTS User_Profiles;
DROP TABLE IF EXISTS User_Addresses;
DROP TABLE IF EXISTS Accounts;
DROP TABLE IF EXISTS Roles;
GO

-- ===
-- 1. ROLES
-- ===
CREATE TABLE Roles (
    id   BIGINT       PRIMARY KEY IDENTITY(1,1),
    name NVARCHAR(50) UNIQUE NOT NULL
);
GO

INSERT INTO Roles (name) VALUES ('SUPER_ADMIN'), ('ADMIN'), ('USER'), ('SHIPPER');
GO

-- ===
-- 2. ACCOUNTS (thông tin đăng nhập chung cho mọi role)
-- ===
CREATE TABLE Accounts (
    id         BIGINT        PRIMARY KEY IDENTITY(1,1),
    username   VARCHAR(100)  UNIQUE NOT NULL,
    password   VARCHAR(MAX)  NOT NULL,
    email      VARCHAR(100)  UNIQUE NOT NULL,
    full_name  NVARCHAR(100),
    phone      VARCHAR(20),
    avatar_url NVARCHAR(MAX),
    role_id    BIGINT        NOT NULL,
    status     VARCHAR(20)   CHECK (status IN ('ACTIVE', 'PENDING', 'BLOCKED')) DEFAULT 'ACTIVE',
    is_deleted BIT           NOT NULL DEFAULT 0,
    is_online  BIT           NOT NULL DEFAULT 0,   -- chỉ dùng cho SHIPPER (bật/tắt sẵn sàng nhận đơn)
    loyalty_points INT       NOT NULL DEFAULT 0,   -- điểm thưởng: 10.000đ đơn hàng thành công = 1 điểm
    logo_url   NVARCHAR(MAX) NULL,                 -- logo riêng của Super Admin (sidebar), tách biệt avatar_url
    bom_count  INT           NOT NULL DEFAULT 0,   -- số lần user bị báo "bom hàng"
    suspend_reason NVARCHAR(500) NULL,             -- lý do đình chỉ tài khoản (soft delete)
    created_at DATETIME2     DEFAULT GETDATE(),
    updated_at DATETIME2     DEFAULT GETDATE(),
    CONSTRAINT FK_Account_Role FOREIGN KEY (role_id) REFERENCES Roles(id)
);
GO

CREATE TRIGGER TR_Accounts_UpdatedAt ON Accounts AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS (SELECT 1 FROM inserted i JOIN deleted d ON i.id = d.id WHERE i.updated_at = d.updated_at) RETURN;
    UPDATE Accounts SET updated_at = GETDATE() WHERE id IN (SELECT id FROM inserted);
END;
GO

-- ===
-- 3. USER_PROFILES (thông tin cá nhân mở rộng cho role USER)
-- ===
CREATE TABLE User_Profiles (
    id                 BIGINT        PRIMARY KEY IDENTITY(1,1),
    account_id         BIGINT        NOT NULL UNIQUE,
    date_of_birth      DATE          NULL,
    gender             VARCHAR(10)   CHECK (gender IN ('MALE', 'FEMALE', 'OTHER')) NULL,
    default_address_id BIGINT        NULL,   -- FK sang User_Addresses, gắn sau khi tạo bảng đó
    created_at         DATETIME2     DEFAULT GETDATE(),
    updated_at         DATETIME2     DEFAULT GETDATE(),
    CONSTRAINT FK_UserProfile_Account FOREIGN KEY (account_id) REFERENCES Accounts(id)
);
GO

CREATE TRIGGER TR_UserProfiles_UpdatedAt ON User_Profiles AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    UPDATE User_Profiles SET updated_at = GETDATE() WHERE id IN (SELECT id FROM inserted);
END;
GO

-- ===
-- 4. USER_ADDRESSES (danh sách địa chỉ giao hàng của USER)
-- ===
CREATE TABLE User_Addresses (
    id             BIGINT        PRIMARY KEY IDENTITY(1,1),
    account_id     BIGINT        NOT NULL,
    label          NVARCHAR(100) NULL,            -- vd 'Nhà', 'Công ty', 'Trường học'
    full_address   NVARCHAR(MAX) NOT NULL,
    receiver_name  NVARCHAR(100) NULL,
    receiver_phone VARCHAR(20)   NULL,
    is_default     BIT           NOT NULL DEFAULT 0,
    is_deleted     BIT           NOT NULL DEFAULT 0,  -- soft delete, dùng bởi UserAddressDAOImpl
    locationX      FLOAT         NULL,                -- toạ độ GPS địa chỉ
    locationY      FLOAT         NULL,
    created_at     DATETIME2     DEFAULT GETDATE(),
    CONSTRAINT FK_UserAddress_Account FOREIGN KEY (account_id) REFERENCES Accounts(id) ON DELETE CASCADE
);
GO

CREATE INDEX IDX_UserAddress_Account ON User_Addresses(account_id);
CREATE INDEX IX_UserAddresses_Account_Deleted_Default ON User_Addresses(account_id, is_deleted, is_default DESC, id);
-- Lệch schema đã biết với DB cũ (đối chiếu 2026-09-29): DB cũ dùng user_id/address thay vì
-- account_id/full_address ở trên. UserAddressDAOImpl tự dò tên cột nên chạy được cả 2 kiểu;
-- chạy migration_user_addresses_rename_columns.sql để đưa hẳn về tên chuẩn (nhớ khởi động lại Tomcat sau đó).
GO

ALTER TABLE User_Profiles
    ADD CONSTRAINT FK_UserProfile_DefaultAddress FOREIGN KEY (default_address_id) REFERENCES User_Addresses(id);
GO

-- ===
-- 5. SHIPPER_PROFILES (thông tin nghề nghiệp & phương tiện của SHIPPER)
-- Ảnh CCCD/GPLX chỉ lưu dạng mặt trước/mặt sau — không còn cột ảnh đơn (id_card_image_url,
-- license_image_url) như các bản schema cũ.
-- ===
CREATE TABLE Shipper_Profiles (
    id             BIGINT        PRIMARY KEY IDENTITY(1,1),
    account_id     BIGINT        NOT NULL UNIQUE,
    cccd           VARCHAR(20)   NULL,
    license_number VARCHAR(30)   NULL,
    vehicle_type   NVARCHAR(50)  NULL,            -- 'Xe máy', 'Ô tô', 'Xe đạp điện', 'Xe đạp'
    vehicle_plate  VARCHAR(20)   NULL,
    vehicle_model  NVARCHAR(100) NULL,
    bank_account   VARCHAR(30)   NULL,
    bank_name      NVARCHAR(100) NULL,
    bank_account_holder NVARCHAR(100) NULL,
    id_card_front_url NVARCHAR(500) NULL,
    id_card_back_url  NVARCHAR(500) NULL,
    license_front_url NVARCHAR(500) NULL,
    license_back_url  NVARCHAR(500) NULL,
    verification_status NVARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (verification_status IN ('PENDING','APPROVED','REJECTED')),
    rejection_reason NVARCHAR(500) NULL,
    verified_by    BIGINT        NULL,            -- account_id Super Admin đã duyệt/từ chối
    verified_at    DATETIME2     NULL,
    created_at     DATETIME2     DEFAULT GETDATE(),
    updated_at     DATETIME2     DEFAULT GETDATE(),
    CONSTRAINT FK_ShipperProfile_Account FOREIGN KEY (account_id) REFERENCES Accounts(id),
    CONSTRAINT FK_ShipperProfile_VerifiedBy FOREIGN KEY (verified_by) REFERENCES Accounts(id)
);
GO

CREATE TRIGGER TR_ShipperProfiles_UpdatedAt ON Shipper_Profiles AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    UPDATE Shipper_Profiles SET updated_at = GETDATE() WHERE id IN (SELECT id FROM inserted);
END;
GO

-- ===
-- 6. SHOPS
-- ===
CREATE TABLE Shops (
    id               BIGINT        PRIMARY KEY IDENTITY(1,1),
    owner_id         BIGINT        NOT NULL,
    shop_name        NVARCHAR(255) NOT NULL,
    shop_description NVARCHAR(MAX),
    shop_address     NVARCHAR(MAX),
    shop_phone       VARCHAR(20),
    shop_logo        NVARCHAR(MAX),
    status           VARCHAR(20)   CHECK (status IN ('PENDING', 'ACTIVE', 'REJECTED', 'BLOCKED')) DEFAULT 'PENDING',
    rejection_reason NVARCHAR(MAX),
    approved_by      BIGINT        NULL,
    approved_at      DATETIME2     NULL,
    client_key       VARCHAR(255)  NULL,
    api_key          VARCHAR(255)  NULL,
    check_sum_key    VARCHAR(255)  NULL,
    locationX        DECIMAL(18,10) NULL,
    locationY        DECIMAL(18,10) NULL,
    open_time        TIME          NULL, -- NULL = mở cả ngày
    close_time       TIME          NULL, -- NULL = mở cả ngày
    commission_rate  DECIMAL(5,2)  NULL, -- % hoa hồng riêng của shop; NULL = dùng mặc định System_Configs.commission_percent
    bank_code            NVARCHAR(20)  NULL, -- mã BIN ngân hàng theo chuẩn VietQR/NAPAS, vd '970436' = Vietcombank
    bank_account_number  VARCHAR(50)   NULL,
    bank_account_name    NVARCHAR(255) NULL,
    is_deleted       BIT           DEFAULT 0,
    created_at       DATETIME2     DEFAULT GETDATE(),
    updated_at       DATETIME2     DEFAULT GETDATE(),
    CONSTRAINT FK_Shop_Account     FOREIGN KEY (owner_id)    REFERENCES Accounts(id),
    CONSTRAINT FK_Shop_Approved_By FOREIGN KEY (approved_by) REFERENCES Accounts(id),
    CONSTRAINT UQ_Shop_Owner UNIQUE (owner_id)
);
GO

CREATE TRIGGER TR_Shops_UpdatedAt ON Shops AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS (SELECT 1 FROM inserted i JOIN deleted d ON i.id = d.id WHERE i.updated_at = d.updated_at) RETURN;
    UPDATE Shops SET updated_at = GETDATE() WHERE id IN (SELECT id FROM inserted);
END;
GO

-- ===
-- 7. CATEGORIES (loại sản phẩm của shop)
-- ===
CREATE TABLE Categories (
    id          BIGINT        PRIMARY KEY IDENTITY(1,1),
    shop_id     BIGINT        NOT NULL,
    name        NVARCHAR(100) NOT NULL,
    description NVARCHAR(MAX),
    is_deleted  BIT           DEFAULT 0,
    CONSTRAINT FK_Category_Shop FOREIGN KEY (shop_id) REFERENCES Shops(id)
);
GO
CREATE INDEX IDX_Category_Shop ON Categories(shop_id);
GO

-- ===
-- 8. PRODUCTS
-- ===
CREATE TABLE Products (
    id             BIGINT        PRIMARY KEY IDENTITY(1,1),
    shop_id        BIGINT        NOT NULL,
    category_id    BIGINT        NOT NULL,
    product_name   NVARCHAR(255) NOT NULL,
    description    NVARCHAR(MAX),
    stock_quantity INT           DEFAULT 0,
    sold_count     INT           DEFAULT 0,
    status         VARCHAR(20)   DEFAULT 'ACTIVE',
    is_deleted     BIT           DEFAULT 0,
    created_at     DATETIME2     DEFAULT GETDATE(),
    updated_at     DATETIME2     DEFAULT GETDATE(),
    CONSTRAINT CHK_Product_Stock     CHECK (stock_quantity >= 0),
    CONSTRAINT CHK_Product_SoldCount CHECK (sold_count >= 0),
    CONSTRAINT CK_Products_Status CHECK (status IN ('ACTIVE', 'OUT_OF_STOCK', 'HIDDEN', 'PENDING_REVIEW')),
    CONSTRAINT FK_Product_Shop     FOREIGN KEY (shop_id)     REFERENCES Shops(id),
    CONSTRAINT FK_Product_Category FOREIGN KEY (category_id) REFERENCES Categories(id),
    CONSTRAINT UQ_Products_Id_Shop UNIQUE (id, shop_id)  -- composite FK cho Product_Sizes
);
GO

CREATE TRIGGER TR_Products_UpdatedAt ON Products AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS (SELECT 1 FROM inserted i JOIN deleted d ON i.id = d.id WHERE i.updated_at = d.updated_at) RETURN;
    UPDATE Products SET updated_at = GETDATE() WHERE id IN (SELECT id FROM inserted);
END;
GO

CREATE INDEX IDX_Product_Name     ON Products(product_name);
CREATE INDEX IDX_Product_Shop     ON Products(shop_id);
CREATE INDEX IDX_Product_Category ON Products(category_id);
GO

-- ===
-- 9. PRODUCT_SIZES (giá theo size)
-- ===
CREATE TABLE Product_Sizes (
    id         BIGINT        PRIMARY KEY IDENTITY(1,1),
    product_id BIGINT        NOT NULL,
    shop_id    BIGINT        NOT NULL,
    size_name  NVARCHAR(50)  NOT NULL,
    price      DECIMAL(12,2) NOT NULL,
    is_out_of_stock BIT NOT NULL DEFAULT 0, -- hết hàng tạm thời theo size
    CONSTRAINT CHK_ProductSize_Price CHECK (price > 0),
    CONSTRAINT FK_ProductSize_Product FOREIGN KEY (product_id) REFERENCES Products(id) ON DELETE CASCADE,
    CONSTRAINT FK_ProductSize_Shop    FOREIGN KEY (shop_id)    REFERENCES Shops(id),
    CONSTRAINT FK_ProductSize_ProductShop FOREIGN KEY (product_id, shop_id) REFERENCES Products(id, shop_id),
    CONSTRAINT UQ_Product_Size UNIQUE (product_id, size_name),
    CONSTRAINT UQ_ProductSizes_Id_Product UNIQUE (id, product_id),
    CONSTRAINT UQ_ProductSizes_Id_Shop    UNIQUE (id, shop_id)
);
GO
CREATE INDEX IDX_ProductSize_Shop ON Product_Sizes(shop_id);
GO

-- ===
-- 10. TOPPING_CATEGORIES (+ bảng trung gian nhiều-nhiều với Categories:
-- 1 loại topping có thể áp dụng cho NHIỀU loại sản phẩm cùng lúc; rỗng = áp dụng cho mọi loại)
-- ===
CREATE TABLE ToppingCategories (
    id          BIGINT        PRIMARY KEY IDENTITY(1,1),
    shop_id     BIGINT        NOT NULL,
    name        NVARCHAR(100) NOT NULL,
    description NVARCHAR(MAX),
    is_deleted  BIT           DEFAULT 0,
    CONSTRAINT FK_ToppingCategory_Shop FOREIGN KEY (shop_id) REFERENCES Shops(id),
    CONSTRAINT UQ_ToppingCategories_Id_Shop UNIQUE (id, shop_id)
);
GO
CREATE INDEX IDX_ToppingCategory_Shop ON ToppingCategories(shop_id);
GO

CREATE TABLE ToppingCategory_ProductCategories (
    topping_category_id BIGINT NOT NULL,
    category_id          BIGINT NOT NULL,
    PRIMARY KEY (topping_category_id, category_id),
    CONSTRAINT FK_TCPC_ToppingCategory FOREIGN KEY (topping_category_id) REFERENCES ToppingCategories(id) ON DELETE CASCADE,
    CONSTRAINT FK_TCPC_Category        FOREIGN KEY (category_id)         REFERENCES Categories(id)
);
GO

-- ===
-- 11. TOPPINGS
-- ===
CREATE TABLE Toppings (
    id                  BIGINT        PRIMARY KEY IDENTITY(1,1),
    topping_category_id BIGINT        NOT NULL,
    shop_id             BIGINT        NOT NULL,
    topping_name        NVARCHAR(100) NOT NULL,
    price               DECIMAL(12,2) NOT NULL DEFAULT 0,
    status              VARCHAR(20)   CHECK (status IN ('ACTIVE', 'OUT_OF_STOCK')) DEFAULT 'ACTIVE',
    is_deleted          BIT           DEFAULT 0,
    CONSTRAINT CHK_Topping_Price CHECK (price >= 0),
    CONSTRAINT FK_Topping_Category FOREIGN KEY (topping_category_id) REFERENCES ToppingCategories(id),
    CONSTRAINT FK_Topping_Shop     FOREIGN KEY (shop_id)             REFERENCES Shops(id),
    CONSTRAINT FK_Topping_CategoryShop FOREIGN KEY (topping_category_id, shop_id) REFERENCES ToppingCategories(id, shop_id)
);
GO
CREATE INDEX IDX_Topping_Shop ON Toppings(shop_id);
GO

-- ===
-- 12. PRODUCT_IMAGES
-- ===
CREATE TABLE Product_Images (
    id         BIGINT        PRIMARY KEY IDENTITY(1,1),
    product_id BIGINT        NOT NULL,
    image_url  NVARCHAR(MAX) NOT NULL,
    is_primary BIT           DEFAULT 0,
    sort_order INT           DEFAULT 0,
    CONSTRAINT FK_Product_Image FOREIGN KEY (product_id) REFERENCES Products(id) ON DELETE CASCADE
);
GO
CREATE UNIQUE INDEX UQ_Product_Primary_Image  ON Product_Images(product_id) WHERE is_primary = 1;
CREATE INDEX        IDX_Product_Image_Product ON Product_Images(product_id);
GO

-- ===
-- 13-14. COMBOS / COMBO_ITEMS (combo sản phẩm của shop; đặt trước Cart_Items vì
-- Cart_Items.combo_id tham chiếu tới Combos)
-- ===
CREATE TABLE Combos (
    id          BIGINT        PRIMARY KEY IDENTITY(1,1),
    shop_id     BIGINT        NOT NULL,
    name        NVARCHAR(200) NOT NULL,
    description NVARCHAR(500) NULL,
    combo_price DECIMAL(12,2) NOT NULL,
    is_active   BIT           NOT NULL DEFAULT 1,
    created_at  DATETIME2     DEFAULT GETDATE(),
    updated_at  DATETIME2     DEFAULT GETDATE(),
    CONSTRAINT FK_Combo_Shop FOREIGN KEY (shop_id) REFERENCES Shops(id) ON DELETE CASCADE
);
GO
CREATE INDEX IDX_Combo_Shop ON Combos(shop_id);
GO

CREATE TABLE Combo_Items (
    id              BIGINT PRIMARY KEY IDENTITY(1,1),
    combo_id        BIGINT NOT NULL,
    product_id      BIGINT NOT NULL,
    product_size_id BIGINT NOT NULL,
    quantity        INT    NOT NULL DEFAULT 1,
    CONSTRAINT FK_ComboItem_Combo   FOREIGN KEY (combo_id)        REFERENCES Combos(id)        ON DELETE CASCADE,
    CONSTRAINT FK_ComboItem_Product FOREIGN KEY (product_id)      REFERENCES Products(id),
    CONSTRAINT FK_ComboItem_Size    FOREIGN KEY (product_size_id) REFERENCES Product_Sizes(id),
    CONSTRAINT FK_ComboItem_ProductSizeProduct FOREIGN KEY (product_size_id, product_id) REFERENCES Product_Sizes(id, product_id)
);
GO

-- ===
-- 15. CARTS
-- ===
CREATE TABLE Carts (
    id         BIGINT    PRIMARY KEY IDENTITY(1,1),
    user_id    BIGINT    NOT NULL UNIQUE,
    created_at DATETIME2 DEFAULT GETDATE(),
    CONSTRAINT FK_Cart_Account FOREIGN KEY (user_id) REFERENCES Accounts(id) ON DELETE CASCADE
);
GO

-- ===
-- 16. CART_ITEMS
-- ===
CREATE TABLE Cart_Items (
    id              BIGINT PRIMARY KEY IDENTITY(1,1),
    cart_id         BIGINT NOT NULL,
    product_id      BIGINT NOT NULL,
    product_size_id BIGINT NOT NULL,
    quantity        INT    NOT NULL,
    combo_id         BIGINT NULL,           -- NULL = món lẻ; khác NULL = item thuộc 1 combo đã thêm
    combo_unit_price DECIMAL(12,2) NULL,    -- giá/đơn vị quy đổi từ Combos.combo_price, khoá tại thời điểm thêm combo
    CONSTRAINT CHK_CartItem_Quantity CHECK (quantity > 0),
    CONSTRAINT FK_Item_Cart    FOREIGN KEY (cart_id)         REFERENCES Carts(id)         ON DELETE CASCADE,
    CONSTRAINT FK_Item_Product FOREIGN KEY (product_id)      REFERENCES Products(id),
    CONSTRAINT FK_Item_Size    FOREIGN KEY (product_size_id) REFERENCES Product_Sizes(id),
    CONSTRAINT FK_CartItem_Combo FOREIGN KEY (combo_id)      REFERENCES Combos(id) ON DELETE SET NULL,
    CONSTRAINT FK_CartItem_ProductSizeProduct FOREIGN KEY (product_size_id, product_id) REFERENCES Product_Sizes(id, product_id)
);
GO
CREATE INDEX IDX_CartItem_Cart ON Cart_Items(cart_id);
GO

-- ===
-- 17. CART_ITEM_TOPPINGS
-- ===
CREATE TABLE Cart_Item_Toppings (
    id           BIGINT PRIMARY KEY IDENTITY(1,1),
    cart_item_id BIGINT NOT NULL,
    topping_id   BIGINT NOT NULL,
    quantity     INT    DEFAULT 1,
    CONSTRAINT FK_CartTopping_Item    FOREIGN KEY (cart_item_id) REFERENCES Cart_Items(id) ON DELETE CASCADE,
    CONSTRAINT FK_CartTopping_Topping FOREIGN KEY (topping_id)   REFERENCES Toppings(id)
);
GO

-- ===
-- 18. ORDERS
-- status dùng chung đúng 8 giá trị với Order_Logs.old_status/new_status (mục 21).
-- ===
CREATE TABLE Orders (
    id                      BIGINT        PRIMARY KEY IDENTITY(1,1),
    user_id                 BIGINT        NOT NULL,
    shop_id                 BIGINT        NOT NULL,
    shipper_id              BIGINT        NULL,
    receiver_name           NVARCHAR(100) NOT NULL,
    receiver_phone          VARCHAR(20)   NOT NULL,
    shipping_address        NVARCHAR(MAX) NOT NULL,
    total_price             DECIMAL(12,2) NOT NULL,
    delivery_fee            DECIMAL(12,2) DEFAULT 0,
    payment_method          VARCHAR(20)   DEFAULT 'COD',
    payment_status          VARCHAR(20)   NOT NULL CHECK (payment_status IN ('UNPAID', 'PENDING', 'PAID')) DEFAULT 'UNPAID',
    status                  VARCHAR(30)   CHECK (status IN (
                                'PENDING', 'CONFIRMED', 'READY_FOR_PICKUP', 'WAITING_FOR_SHIPPER',
                                'ACCEPTED', 'SHIPPING', 'DONE', 'CANCELLED'
                            )) DEFAULT 'PENDING',
    estimated_delivery_time DATETIME2     NULL,
    payos_order_code        BIGINT        NULL,
    locationX               DECIMAL(18,10) NULL,
    locationY               DECIMAL(18,10) NULL,
    voucher_code            VARCHAR(50)   NULL,
    discount_amount         DECIMAL(12,2) NOT NULL DEFAULT 0,
    scheduled_at            DATETIME2     NULL,        -- NULL = giao ngay; có giá trị = đơn hẹn giờ
    cancel_reason           NVARCHAR(500) NULL,
    created_at              DATETIME2     DEFAULT GETDATE(),
    updated_at              DATETIME2     DEFAULT GETDATE(),
    CONSTRAINT CHK_Order_TotalPrice  CHECK (total_price >= 0),
    CONSTRAINT CHK_Order_DeliveryFee CHECK (delivery_fee >= 0),
    CONSTRAINT CK_Orders_PaymentMethod CHECK (payment_method IN ('COD', 'BANK', 'PAYOS', 'MOMO')),
    CONSTRAINT FK_Order_User    FOREIGN KEY (user_id)    REFERENCES Accounts(id),
    CONSTRAINT FK_Order_Shop    FOREIGN KEY (shop_id)    REFERENCES Shops(id),
    CONSTRAINT FK_Order_Shipper FOREIGN KEY (shipper_id) REFERENCES Accounts(id)
);
GO

CREATE TRIGGER TR_Orders_UpdatedAt ON Orders AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS (SELECT 1 FROM inserted i JOIN deleted d ON i.id = d.id WHERE i.updated_at = d.updated_at) RETURN;
    UPDATE Orders SET updated_at = GETDATE() WHERE id IN (SELECT id FROM inserted);
END;
GO

CREATE INDEX IDX_Order_Status ON Orders(status);
CREATE INDEX IDX_Order_User   ON Orders(user_id);
CREATE INDEX IDX_Order_Shop   ON Orders(shop_id);
CREATE INDEX IX_Orders_Shipper_Status_UpdatedAt -- tối ưu /shipper/danh-gia
    ON Orders (shipper_id, status, updated_at DESC)
    INCLUDE (shop_id, receiver_name, receiver_phone, shipping_address, total_price);
GO

-- ===
-- 19. ORDER_DETAILS
-- ===
CREATE TABLE Order_Details (
    id              BIGINT        PRIMARY KEY IDENTITY(1,1),
    order_id        BIGINT        NOT NULL,
    product_id      BIGINT        NOT NULL,
    product_size_id BIGINT        NOT NULL,
    quantity        INT           NOT NULL,
    price           DECIMAL(12,2) NOT NULL,
    CONSTRAINT CHK_OrderDetail_Quantity CHECK (quantity > 0),
    CONSTRAINT CHK_OrderDetail_Price    CHECK (price > 0),
    CONSTRAINT FK_Detail_Order   FOREIGN KEY (order_id)        REFERENCES Orders(id)        ON DELETE CASCADE,
    CONSTRAINT FK_Detail_Product FOREIGN KEY (product_id)      REFERENCES Products(id),
    CONSTRAINT FK_Detail_Size    FOREIGN KEY (product_size_id) REFERENCES Product_Sizes(id),
    CONSTRAINT FK_OrderDetail_ProductSizeProduct FOREIGN KEY (product_size_id, product_id) REFERENCES Product_Sizes(id, product_id)
);
GO
CREATE INDEX IDX_OrderDetail_Order ON Order_Details(order_id);
GO

-- ===
-- 20. ORDER_DETAIL_TOPPINGS
-- ===
CREATE TABLE Order_Detail_Toppings (
    id              BIGINT        PRIMARY KEY IDENTITY(1,1),
    order_detail_id BIGINT        NOT NULL,
    topping_id      BIGINT        NOT NULL,
    quantity        INT           DEFAULT 1,
    price           DECIMAL(12,2) NOT NULL,
    CONSTRAINT FK_OrderTopping_Detail  FOREIGN KEY (order_detail_id) REFERENCES Order_Details(id) ON DELETE CASCADE,
    CONSTRAINT FK_OrderTopping_Topping FOREIGN KEY (topping_id)      REFERENCES Toppings(id)
);
GO

-- ===
-- 21. ORDER_LOGS
-- ===
CREATE TABLE Order_Logs (
    id         BIGINT      PRIMARY KEY IDENTITY(1,1),
    order_id   BIGINT      NOT NULL,
    changed_by BIGINT      NOT NULL,
    old_status VARCHAR(30) NULL  CHECK (old_status IN (
                    'PENDING', 'CONFIRMED', 'READY_FOR_PICKUP', 'WAITING_FOR_SHIPPER',
                    'ACCEPTED', 'SHIPPING', 'DONE', 'CANCELLED'
                )),
    new_status VARCHAR(30) NOT NULL CHECK (new_status IN (
                    'PENDING', 'CONFIRMED', 'READY_FOR_PICKUP', 'WAITING_FOR_SHIPPER',
                    'ACCEPTED', 'SHIPPING', 'DONE', 'CANCELLED'
                )),
    note       NVARCHAR(MAX),
    created_at DATETIME2   DEFAULT GETDATE(),
    CONSTRAINT FK_Log_Order   FOREIGN KEY (order_id)   REFERENCES Orders(id)   ON DELETE CASCADE,
    CONSTRAINT FK_Log_Account FOREIGN KEY (changed_by) REFERENCES Accounts(id)
);
GO

-- ===
-- TRIGGERS BẢO VỆ SOFT DELETE
-- ===
CREATE TRIGGER TR_Accounts_PreventSoftDelete ON Accounts AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS (SELECT 1 FROM inserted i JOIN deleted d ON i.id = d.id WHERE d.is_deleted = 0 AND i.is_deleted = 1) RETURN;
    IF EXISTS (SELECT 1 FROM Orders o JOIN inserted i ON o.user_id = i.id)
    BEGIN
        RAISERROR('Không thể xóa tài khoản đang có đơn hàng.', 16, 1); ROLLBACK TRANSACTION; RETURN;
    END
    IF EXISTS (SELECT 1 FROM Shops s JOIN inserted i ON s.owner_id = i.id WHERE s.is_deleted = 0)
    BEGIN
        RAISERROR('Không thể xóa tài khoản đang sở hữu cửa hàng.', 16, 1); ROLLBACK TRANSACTION; RETURN;
    END
END;
GO

CREATE TRIGGER TR_Shops_PreventSoftDelete ON Shops AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS (SELECT 1 FROM inserted i JOIN deleted d ON i.id = d.id WHERE d.is_deleted = 0 AND i.is_deleted = 1) RETURN;
    IF EXISTS (SELECT 1 FROM Products p JOIN inserted i ON p.shop_id = i.id WHERE p.is_deleted = 0)
    BEGIN
        RAISERROR('Không thể xóa cửa hàng còn sản phẩm đang hoạt động.', 16, 1); ROLLBACK TRANSACTION; RETURN;
    END
    IF EXISTS (SELECT 1 FROM Orders o JOIN inserted i ON o.shop_id = i.id WHERE o.status NOT IN ('DONE', 'CANCELLED'))
    BEGIN
        RAISERROR('Không thể xóa cửa hàng còn đơn hàng đang xử lý.', 16, 1); ROLLBACK TRANSACTION; RETURN;
    END
END;
GO

CREATE TRIGGER TR_Products_PreventSoftDelete ON Products AFTER UPDATE AS
BEGIN
    SET NOCOUNT ON;
    IF NOT EXISTS (SELECT 1 FROM inserted i JOIN deleted d ON i.id = d.id WHERE d.is_deleted = 0 AND i.is_deleted = 1) RETURN;
    IF EXISTS (SELECT 1 FROM Cart_Items ci JOIN inserted i ON ci.product_id = i.id)
    BEGIN
        RAISERROR('Không thể xóa sản phẩm đang có trong giỏ hàng.', 16, 1); ROLLBACK TRANSACTION; RETURN;
    END
    IF EXISTS (SELECT 1 FROM Order_Details od JOIN inserted i ON od.product_id = i.id JOIN Orders o ON od.order_id = o.id WHERE o.status NOT IN ('DONE', 'CANCELLED'))
    BEGIN
        RAISERROR('Không thể xóa sản phẩm đang có trong đơn hàng chưa hoàn thành.', 16, 1); ROLLBACK TRANSACTION; RETURN;
    END
END;
GO

-- ===
-- FEEDBACKS (đánh giá Shop/Shipper sau khi đơn DONE)
-- ===
CREATE TABLE Feedbacks (
    id            BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_id      BIGINT        NOT NULL,
    reviewer_type NVARCHAR(10)  NOT NULL CHECK (reviewer_type IN ('USER','SHIPPER')),
    reviewer_id   BIGINT        NOT NULL,   -- account_id của người đánh giá
    target_type   NVARCHAR(10)  NOT NULL CHECK (target_type IN ('SHOP','SHIPPER')),
    target_id     BIGINT        NOT NULL,   -- shop_id hoặc account_id shipper
    rating        INT           NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment       NVARCHAR(1000),
    is_anonymous  BIT           NOT NULL DEFAULT 0,
    status        NVARCHAR(20)  NOT NULL DEFAULT 'VISIBLE', -- VISIBLE | PENDING_REVIEW | REMOVED
    created_at    DATETIME      NOT NULL DEFAULT GETDATE(),
    reviewed_at   DATETIME2     NULL, -- thời điểm Super Admin phê duyệt/xóa bỏ
    CONSTRAINT UQ_Feedback_Once UNIQUE (order_id, reviewer_type, target_type),
    CONSTRAINT FK_Feedback_Order    FOREIGN KEY (order_id)    REFERENCES Orders(id) ON DELETE CASCADE,
    CONSTRAINT FK_Feedback_Reviewer FOREIGN KEY (reviewer_id) REFERENCES Accounts(id)
);
GO

-- Trigger nghiệp vụ: reviewer/target phải khớp đúng với đơn hàng liên quan (không diễn tả được
-- bằng FK thường vì reviewer/target trỏ tới Shops HOẶC Accounts tùy reviewer_type/target_type)
CREATE TRIGGER TR_Feedbacks_ValidateOrderParties ON Feedbacks
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM inserted f JOIN Orders o ON o.id = f.order_id
        WHERE (f.reviewer_type = N'USER' AND f.reviewer_id <> o.user_id)
           OR (f.reviewer_type = N'SHIPPER' AND (o.shipper_id IS NULL OR f.reviewer_id <> o.shipper_id))
           OR (f.target_type = N'SHOP' AND f.target_id <> o.shop_id)
           OR (f.target_type = N'SHIPPER' AND (o.shipper_id IS NULL OR f.target_id <> o.shipper_id))
    )
        THROW 51012, N'Feedback phai dung reviewer, target va don hang lien quan.', 1;
END;
GO

CREATE TABLE Feedback_Images (
    id          BIGINT        PRIMARY KEY IDENTITY(1,1),
    feedback_id BIGINT        NOT NULL,
    image_url   NVARCHAR(500) NOT NULL,
    created_at  DATETIME2     DEFAULT GETDATE(),
    CONSTRAINT FK_FeedbackImage_Feedback FOREIGN KEY (feedback_id) REFERENCES Feedbacks(id) ON DELETE CASCADE
);
CREATE INDEX IDX_FeedbackImage_Feedback ON Feedback_Images(feedback_id);
GO

-- ===
-- BANNEDWORDS (từ khoá cấm, tự động ẩn Feedback/Product vi phạm)
-- ===
CREATE TABLE BannedWords (
    id         INT IDENTITY(1,1) PRIMARY KEY,
    word       NVARCHAR(100) NOT NULL,
    created_at DATETIME      NOT NULL DEFAULT GETDATE()
);
GO
INSERT INTO BannedWords (word) VALUES (N'lừa đảo'), (N'ngu'), (N'chửi'), (N'địt'), (N'đéo');
GO

-- ===
-- NOTIFICATIONS (thông báo cho Shipper và khách hàng)
-- ===
CREATE TABLE Notifications (
    id         BIGINT        PRIMARY KEY IDENTITY(1,1),
    account_id BIGINT        NOT NULL,
    title      NVARCHAR(255) NOT NULL,
    message    NVARCHAR(MAX) NOT NULL,
    is_read    BIT           NOT NULL DEFAULT 0,
    created_at DATETIME2     DEFAULT GETDATE(),
    CONSTRAINT FK_Notification_Account FOREIGN KEY (account_id) REFERENCES Accounts(id) ON DELETE CASCADE
);
GO
CREATE INDEX IDX_Notification_Account ON Notifications(account_id);
GO

-- ===
-- ACCOUNT_APPEALS (khách kháng nghị tài khoản bị đình chỉ/từ chối)
-- ===
CREATE TABLE Account_Appeals (
    id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    account_id  BIGINT NOT NULL,
    message     NVARCHAR(1000) NOT NULL,
    status      NVARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING | APPROVED | REJECTED
    admin_note  NVARCHAR(500) NULL,
    created_at  DATETIME DEFAULT GETDATE(),
    reviewed_at DATETIME NULL,
    FOREIGN KEY (account_id) REFERENCES Accounts(id)
);
GO

-- ===
-- COMPLAINTS (khách khiếu nại đơn hàng, Super Admin xử lý)
-- ===
CREATE TABLE Complaints (
    id          BIGINT        PRIMARY KEY IDENTITY(1,1),
    order_id    BIGINT        NOT NULL,
    account_id  BIGINT        NOT NULL,          -- khách gửi khiếu nại (Orders.user_id)
    subject     NVARCHAR(255) NOT NULL,
    content     NVARCHAR(MAX) NOT NULL,
    status      VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'PROCESSING', 'RESOLVED', 'REJECTED')),
    admin_reply NVARCHAR(MAX) NULL,
    resolved_by BIGINT NULL,                     -- Accounts.id của admin xử lý
    created_at  DATETIME2     DEFAULT GETDATE(),
    updated_at  DATETIME2     DEFAULT GETDATE(),
    CONSTRAINT FK_Complaint_Order   FOREIGN KEY (order_id)   REFERENCES Orders(id)   ON DELETE CASCADE,
    CONSTRAINT FK_Complaint_Account FOREIGN KEY (account_id) REFERENCES Accounts(id),
    CONSTRAINT FK_Complaint_ResolvedBy FOREIGN KEY (resolved_by) REFERENCES Accounts(id) ON DELETE SET NULL
);
GO
CREATE INDEX IDX_Complaint_Order   ON Complaints(order_id);
CREATE INDEX IDX_Complaint_Account ON Complaints(account_id);
CREATE INDEX IDX_Complaint_Status  ON Complaints(status);
GO

-- Trigger nghiệp vụ: chỉ chủ đơn hàng mới được tạo khiếu nại cho đơn đó
CREATE TRIGGER TR_Complaints_ValidateOrderOwner ON Complaints
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (SELECT 1 FROM inserted c JOIN Orders o ON o.id = c.order_id WHERE c.account_id <> o.user_id)
        THROW 51013, N'Chi chu don hang moi duoc tao khieu nai cho don do.', 1;
END;
GO

-- Trigger nghiệp vụ: địa chỉ mặc định của User_Profiles phải thuộc cùng tài khoản
CREATE TRIGGER TR_UserProfiles_ValidateDefaultAddress ON User_Profiles
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1 FROM inserted up JOIN User_Addresses ua ON ua.id = up.default_address_id
        WHERE ua.account_id <> up.account_id
    )
        THROW 51014, N'Dia chi mac dinh phai thuoc cung tai khoan voi ho so nguoi dung.', 1;
END;
GO

-- ===
-- VOUCHERS (mã giảm giá do Super Admin quản lý, áp dụng toàn sàn)
-- ===
CREATE TABLE Vouchers (
    id              BIGINT        PRIMARY KEY IDENTITY(1,1),
    code            VARCHAR(50)   NOT NULL,
    voucher_type    VARCHAR(20)   NOT NULL CHECK (voucher_type IN ('PERCENT','FIXED','FREESHIP')),
    value           DECIMAL(12,2) NOT NULL DEFAULT 0, -- % (PERCENT) hoặc số tiền (FIXED); FREESHIP luôn = 0
    min_order_value DECIMAL(12,2) NOT NULL DEFAULT 0,
    max_discount    DECIMAL(12,2) NULL,                -- chỉ áp dụng cho PERCENT, NULL = không giới hạn
    usage_limit     INT           NULL,                -- NULL = không giới hạn số lần dùng
    used_count      INT           NOT NULL DEFAULT 0,
    start_date      DATETIME2     NULL,
    end_date        DATETIME2     NULL,
    is_active       BIT           NOT NULL DEFAULT 1,
    created_at      DATETIME2     DEFAULT GETDATE(),
    CONSTRAINT UQ_Voucher_Code UNIQUE (code)
);
GO
-- Orders.voucher_code / discount_amount (đã có trong CREATE TABLE Orders ở mục 18) ghi lại mã
-- đã dùng + số tiền được giảm cho 1 Order.

-- ===
-- FAQS (câu hỏi thường gặp/hướng dẫn, Super Admin quản trị)
-- ===
CREATE TABLE FAQs (
    id             BIGINT        PRIMARY KEY IDENTITY(1,1),
    question       NVARCHAR(500) NOT NULL,
    answer         NVARCHAR(MAX) NOT NULL,
    category       NVARCHAR(100) NULL,
    display_order  INT           NOT NULL DEFAULT 0,
    is_active      BIT           NOT NULL DEFAULT 1,
    is_deleted     BIT           NOT NULL DEFAULT 0,
    created_by     BIGINT        NOT NULL,
    updated_by     BIGINT        NULL,
    created_at     DATETIME2     NOT NULL DEFAULT GETDATE(),
    updated_at     DATETIME2     NOT NULL DEFAULT GETDATE(),
    CONSTRAINT FK_FAQs_CreatedBy FOREIGN KEY (created_by) REFERENCES Accounts(id),
    CONSTRAINT FK_FAQs_UpdatedBy FOREIGN KEY (updated_by) REFERENCES Accounts(id) ON DELETE SET NULL
);
GO
CREATE INDEX IDX_FAQs_Public   ON FAQs(is_deleted, is_active, category, display_order);
CREATE INDEX IDX_FAQs_Category ON FAQs(category);
GO

-- ===
-- SHOP_SETTLEMENTS (đối soát/xác nhận thanh toán doanh thu cho Shop theo kỳ)
-- ===
CREATE TABLE Shop_Settlements (
    id            BIGINT IDENTITY(1,1) PRIMARY KEY,
    shop_id       BIGINT NOT NULL,
    period_start  DATE NOT NULL,
    period_end    DATE NOT NULL,
    gross_revenue DECIMAL(14,2) NOT NULL,
    platform_fee  DECIMAL(14,2) NOT NULL,
    net_payout    DECIMAL(14,2) NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PAID')),
    confirmed_by  BIGINT NULL,
    confirmed_at  DATETIME2 NULL,
    created_at    DATETIME2 NOT NULL DEFAULT GETDATE(),
    updated_at    DATETIME2 NOT NULL DEFAULT GETDATE(),
    CONSTRAINT UQ_ShopSettlement_Period UNIQUE (shop_id, period_start, period_end),
    CONSTRAINT FK_ShopSettlement_Shop FOREIGN KEY (shop_id) REFERENCES Shops(id),
    CONSTRAINT FK_ShopSettlement_Account FOREIGN KEY (confirmed_by) REFERENCES Accounts(id)
);
GO
CREATE INDEX IDX_ShopSettlement_Shop ON Shop_Settlements(shop_id);
GO

-- ===
-- SHIPPER_WALLETS / SHIPPER_WITHDRAWALS (số dư ví & yêu cầu rút tiền của Shipper)
-- Lưu ý: hiện CHƯA có màn hình/servlet nào cho Shipper tự TẠO yêu cầu rút tiền hay tự xem số
-- dư ví — chỉ có phía Admin duyệt (DuyetRutTienShipperServlet). Luồng nghiệp vụ chưa trọn vẹn.
-- ===
CREATE TABLE Shipper_Wallets (
    id                 BIGINT        PRIMARY KEY IDENTITY(1,1),
    shipper_account_id BIGINT        NOT NULL UNIQUE,
    balance            DECIMAL(14,2) NOT NULL DEFAULT 0,
    updated_at         DATETIME2     NOT NULL DEFAULT GETDATE(),
    CONSTRAINT FK_ShipperWallet_Account FOREIGN KEY (shipper_account_id) REFERENCES Accounts(id)
);
GO

CREATE TABLE Shipper_Withdrawals (
    id                   BIGINT        PRIMARY KEY IDENTITY(1,1),
    shipper_account_id   BIGINT        NOT NULL,
    amount               DECIMAL(14,2) NOT NULL,
    bank_name            NVARCHAR(100) NOT NULL,
    bank_account_number  VARCHAR(30)   NOT NULL,
    bank_account_holder  NVARCHAR(100) NOT NULL,
    status               VARCHAR(20)   NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    reject_reason        NVARCHAR(255) NULL,
    requested_at         DATETIME2     NOT NULL DEFAULT GETDATE(),
    processed_at         DATETIME2     NULL,
    processed_by         BIGINT        NULL,
    CONSTRAINT FK_ShipperWithdrawal_Account FOREIGN KEY (shipper_account_id) REFERENCES Accounts(id),
    CONSTRAINT FK_ShipperWithdrawal_ProcessedBy FOREIGN KEY (processed_by) REFERENCES Accounts(id)
);
GO
CREATE INDEX IDX_ShipperWithdrawal_Status   ON Shipper_Withdrawals(status);
CREATE INDEX IDX_ShipperWithdrawal_Shipper  ON Shipper_Withdrawals(shipper_account_id);
GO

-- ===
-- SYSTEM_CONFIGS (tham số vận hành toàn hệ thống — trang "Tham số vận hành", Super Admin)
-- Luôn chỉ có đúng 1 dòng duy nhất (id = 1)
-- ===
CREATE TABLE System_Configs (
    id                         INT PRIMARY KEY DEFAULT 1,
    commission_percent         DECIMAL(5,2)  NOT NULL DEFAULT 10,    -- % hoa hồng thu từ Shop
    fixed_fee_per_order        DECIMAL(10,2) NOT NULL DEFAULT 0,     -- phí cố định trên mỗi đơn (đ)
    shipping_fee_first_2km     DECIMAL(10,2) NOT NULL DEFAULT 15000, -- phí ship 2km đầu tiên (đ)
    shipping_fee_per_km        DECIMAL(10,2) NOT NULL DEFAULT 5000,  -- phí ship mỗi km tiếp theo (đ)
    max_delivery_radius_km     DECIMAL(5,2)  NOT NULL DEFAULT 10,    -- bán kính giao hàng tối đa (km)
    shop_accept_order_minutes  INT           NOT NULL DEFAULT 15,    -- thời gian Shop phải nhận đơn (phút)
    auto_complete_order_hours  INT           NOT NULL DEFAULT 48,    -- thời gian tự động hoàn thành đơn (giờ)
    updated_at                 DATETIME2 NULL,
    CONSTRAINT CK_System_Configs_SingleRow CHECK (id = 1)
);
GO

-- ===
-- AUDITLOGS (nhật ký hệ thống — chỉ Super Admin xem). Ghi lại mọi hành động quan trọng:
-- duyệt/từ chối Shop, khoá/mở tài khoản, xoá/khôi phục sản phẩm, duyệt/từ chối bình luận,
-- duyệt rút tiền, đối soát doanh thu, thay đổi tham số hệ thống...
-- ===
CREATE TABLE AuditLogs (
    id          BIGINT        PRIMARY KEY IDENTITY(1,1),
    account_id  BIGINT        NULL,           -- NULL cho phép log của job/hệ thống không gắn tài khoản
    role_id     BIGINT        NULL,           -- snapshot role tại thời điểm thao tác (không FK sang Roles)
    action      NVARCHAR(200) NOT NULL,       -- vd "DUYET_SHOP", "KHOA_TAI_KHOAN"
    module      NVARCHAR(100) NOT NULL,       -- vd "SHOP", "ACCOUNT", "PRODUCT", "COMMENT", "FINANCE", "SYSTEM"
    description NVARCHAR(MAX) NOT NULL,
    target_id   BIGINT        NULL,           -- id của đối tượng bị tác động (shop_id, product_id,...)
    target_type NVARCHAR(100) NULL,           -- vd "SHOP", "PRODUCT", "ACCOUNT"
    ip_address  VARCHAR(50)   NULL,
    user_agent  NVARCHAR(500) NULL,
    created_at  DATETIME2     NOT NULL DEFAULT GETDATE(),
    CONSTRAINT FK_AuditLogs_Account FOREIGN KEY (account_id) REFERENCES Accounts(id)
);
GO
CREATE INDEX IDX_AuditLogs_Account   ON AuditLogs(account_id);
CREATE INDEX IDX_AuditLogs_Module    ON AuditLogs(module);
CREATE INDEX IDX_AuditLogs_CreatedAt ON AuditLogs(created_at DESC);
GO

-- ===
-- FLASH_SALES (flash sale sản phẩm theo size)
-- ===
CREATE TABLE Flash_Sales (
    id              BIGINT        PRIMARY KEY IDENTITY(1,1),
    shop_id         BIGINT        NOT NULL,
    product_size_id BIGINT        NOT NULL,
    sale_price      DECIMAL(12,2) NOT NULL,
    start_time      DATETIME2     NOT NULL,
    end_time        DATETIME2     NOT NULL,
    is_active       BIT           NOT NULL DEFAULT 1,
    created_at      DATETIME2     DEFAULT GETDATE(),
    CONSTRAINT FK_FlashSale_Shop FOREIGN KEY (shop_id)         REFERENCES Shops(id),
    CONSTRAINT FK_FlashSale_Size FOREIGN KEY (product_size_id) REFERENCES Product_Sizes(id),
    CONSTRAINT FK_FlashSale_ProductSizeShop FOREIGN KEY (product_size_id, shop_id) REFERENCES Product_Sizes(id, shop_id)
);
GO
CREATE INDEX IDX_FlashSale_Shop ON Flash_Sales(shop_id);
CREATE INDEX IDX_FlashSale_Size ON Flash_Sales(product_size_id);
GO

-- ===
-- SHOP_WALLETS / SHOP_WALLET_TRANSACTIONS / SHOP_WITHDRAWALS
-- Ví Shop: cộng tiền khi đơn DONE, trừ khi rút tiền, ghi log giao dịch. Dùng bởi
-- ShopWalletDAOImpl và các servlet ShopWalletServlet/DuyetRutTienShopServlet/
-- ShopBillServlet/ShipperOrderServlet — luồng nghiệp vụ đầy đủ cả 2 phía Shop & Admin.
-- ===
CREATE TABLE Shop_Wallets (
    id           BIGINT        PRIMARY KEY IDENTITY(1,1),
    shop_id      BIGINT        NOT NULL UNIQUE,
    balance      DECIMAL(14,2) NOT NULL DEFAULT 0,
    total_earned DECIMAL(14,2) NOT NULL DEFAULT 0,
    total_withdrawn DECIMAL(14,2) NOT NULL DEFAULT 0,
    updated_at   DATETIME2     DEFAULT GETDATE(),
    CONSTRAINT FK_ShopWallet_Shop FOREIGN KEY (shop_id) REFERENCES Shops(id)
);
GO

CREATE TABLE Shop_Wallet_Transactions (
    id          BIGINT        PRIMARY KEY IDENTITY(1,1),
    shop_id     BIGINT        NOT NULL,
    type        VARCHAR(20)   NOT NULL CHECK (type IN ('EARNING','WITHDRAWAL','REFUND')),
    amount      DECIMAL(14,2) NOT NULL,
    order_id    BIGINT        NULL,
    description NVARCHAR(500) NOT NULL,
    created_at  DATETIME2     DEFAULT GETDATE(),
    CONSTRAINT FK_ShopWalletTx_Shop  FOREIGN KEY (shop_id)  REFERENCES Shops(id),
    CONSTRAINT FK_ShopWalletTx_Order FOREIGN KEY (order_id) REFERENCES Orders(id) ON DELETE SET NULL
);
GO
CREATE INDEX IDX_ShopWalletTx_Shop ON Shop_Wallet_Transactions(shop_id);
CREATE INDEX IDX_ShopWalletTx_Order ON Shop_Wallet_Transactions(order_id);
GO

CREATE TABLE Shop_Withdrawals (
    id                  BIGINT        PRIMARY KEY IDENTITY(1,1),
    shop_id             BIGINT        NOT NULL,
    amount              DECIMAL(14,2) NOT NULL,
    bank_name           NVARCHAR(100) NOT NULL,
    bank_account_number VARCHAR(50)   NOT NULL,
    bank_account_holder NVARCHAR(200) NOT NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    reject_reason       NVARCHAR(500) NULL,
    requested_at        DATETIME2     DEFAULT GETDATE(),
    processed_at        DATETIME2     NULL,
    processed_by        BIGINT        NULL,
    CONSTRAINT FK_ShopWithdrawal_Shop FOREIGN KEY (shop_id) REFERENCES Shops(id),
    CONSTRAINT FK_ShopWithdrawal_Admin FOREIGN KEY (processed_by) REFERENCES Accounts(id)
);
GO
CREATE INDEX IDX_ShopWithdrawal_Shop   ON Shop_Withdrawals(shop_id);
CREATE INDEX IDX_ShopWithdrawal_Status ON Shop_Withdrawals(status);
GO

-- ===
-- REFUND_REQUESTS (hoàn tiền cho khách — RefundRequestServlet tạo yêu cầu,
-- AdminRefundServlet duyệt/từ chối)
-- ===
CREATE TABLE Refund_Requests (
    id                  BIGINT        PRIMARY KEY IDENTITY(1,1),
    order_id            BIGINT        NOT NULL UNIQUE,
    account_id          BIGINT        NOT NULL,
    amount              DECIMAL(14,2) NOT NULL,
    bank_name           NVARCHAR(100) NOT NULL,
    bank_account_number VARCHAR(50)   NOT NULL,
    bank_account_holder NVARCHAR(200) NOT NULL,
    note                NVARCHAR(500) NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN ('PENDING','COMPLETED','REJECTED')),
    reject_reason       NVARCHAR(500) NULL,
    requested_at        DATETIME2     DEFAULT GETDATE(),
    processed_at        DATETIME2     NULL,
    processed_by        BIGINT        NULL,
    CONSTRAINT FK_RefundReq_Order   FOREIGN KEY (order_id)    REFERENCES Orders(id),
    CONSTRAINT FK_RefundReq_Account FOREIGN KEY (account_id)  REFERENCES Accounts(id),
    CONSTRAINT FK_RefundReq_Admin   FOREIGN KEY (processed_by) REFERENCES Accounts(id)
);
GO
CREATE INDEX IDX_RefundReq_Account ON Refund_Requests(account_id);
CREATE INDEX IDX_RefundReq_Status  ON Refund_Requests(status);
GO
```

## Ghi chú rà soát (2026-09-15)

Toàn bộ bảng/cột ở trên đã được đối chiếu với DAO (`src/main/java/org/example/daos/`) và Servlet
(`src/main/java/org/example/controllers/`) — không có bảng/cột "mồ côi" đáng kể, kể cả các bảng
ví (`Shop_Wallets`, `Shipper_Wallets`...) và hoàn tiền (`Refund_Requests`) tưởng chưa dùng nhưng
thực ra đã có luồng CRUD đầy đủ.

**Gap nghiệp vụ còn thật:** Shipper chưa có màn hình tự tạo yêu cầu rút tiền / xem số dư ví —
chỉ Admin có servlet duyệt (`DuyetRutTienShipperServlet`). Xem ghi chú tại bảng `Shipper_Wallets`.

**Rủi ro khi dựng DB:** nếu dựng database bằng cách chạy **`migration_all.sql`** (thay vì file
này), DB tạo ra sẽ **thiếu** các bảng/cột sau — các migration tương ứng chưa được gộp vào đó:
* Thiếu hẳn bảng: `AuditLogs`, `FAQs`, `System_Configs`, `Vouchers`.
* Thiếu cột: `Accounts.logo_url`, `Accounts.loyalty_points`, `Cart_Items.combo_id`/`combo_unit_price`,
  `Product_Sizes.is_out_of_stock`, `Shipper_Profiles.bank_account_holder`,
  `Shops.bank_code`/`bank_account_number`/`bank_account_name`, `Shops.open_time`/`close_time`,
  `Shops.commission_rate`.
* Thiếu toàn bộ các FK composite + 3 trigger nghiệp vụ (`TR_Feedbacks_ValidateOrderParties`,
  `TR_Complaints_ValidateOrderOwner`, `TR_UserProfiles_ValidateDefaultAddress`) và cột
  `User_Addresses.is_deleted` — đây là gap nghiêm trọng nhất vì các trigger/FK này bảo vệ tính
  toàn vẹn dữ liệu quan trọng.
* `migration_fix_legacy_accepted_status.sql` là script sửa dữ liệu (`UPDATE Orders SET status='CONFIRMED' WHERE status='ACCEPTED'`),
  không phải DDL — chạy tay 1 lần trên DB cũ nếu còn dữ liệu `'ACCEPTED'` tồn đọng.

→ Luôn dựng DB bằng file **`docs/database.md`** này (hoặc file gốc đã gộp mọi thay đổi), không
dùng `migration_all.sql` một mình.
