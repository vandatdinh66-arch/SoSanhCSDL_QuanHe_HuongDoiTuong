/* BẢN DÀNH CHO pgAdmin Query Tool / DBeaver: các lệnh \echo của psql đã đổi thành chú thích.
   Cách dùng: chạy từ đầu file đến hết mục 5 (tạo schema, bảng, trigger, dữ liệu, function, procedure).
   Phần 6 (kiểm thử) nên chạy từng khối: Query Tool chỉ hiện kết quả của câu SELECT cuối cùng
   trong lần chạy; thông báo RAISE NOTICE xem ở tab Messages. */
/* =====================================================================
   ĐỒ ÁN 5 - CSDL QUAN HỆ cho cấu trúc sản phẩm (BOM) - PostgreSQL 12+
   Sản phẩm mẫu: Laptop Gaming Pro (đúng cây 4 cấp trong đề cương nhóm)
   TV2 - Tuần 1: DDL, ràng buộc, chỉ mục
   TV2 - Tuần 2: Recursive CTE, Function/Procedure, dữ liệu mẫu, kiểm thử
   Chạy toàn bộ file một lần bằng psql (tự dọn và tạo lại schema csdl_pre).
   Tên bảng/cột giữ đúng schema nhóm (PostgreSQL tự đổi tên không đặt trong
   ngoặc kép thành chữ thường: MaThanhPhan -> mathanhphan).
   ===================================================================== */

DROP SCHEMA IF EXISTS csdl_pre CASCADE;
CREATE SCHEMA csdl_pre;
SET search_path TO csdl_pre;

/* ---------------------------------------------------------------------
   1. DDL
   --------------------------------------------------------------------- */
CREATE TABLE LOAI_THANH_PHAN (
    MaLoai   VARCHAR(50)  PRIMARY KEY,
    TenLoai  VARCHAR(255) NOT NULL
    -- Nếu nhóm chốt TenLoai là duy nhất: ALTER TABLE ... ADD UNIQUE (TenLoai);
);

CREATE TABLE THANH_PHAN (
    MaThanhPhan   VARCHAR(50)   PRIMARY KEY,
    TenThanhPhan  VARCHAR(255)  NOT NULL,
    MaLoai        VARCHAR(50)   NOT NULL
                  REFERENCES LOAI_THANH_PHAN (MaLoai) ON UPDATE CASCADE,
    DonGia        NUMERIC(18,2),                  -- đơn giá linh kiện lá; cụm để NULL (chi phí cụm = tổng chi phí con)
    DonViTinh     VARCHAR(50)   NOT NULL,
    CONSTRAINT CK_THANH_PHAN_DON_GIA CHECK (DonGia IS NULL OR DonGia >= 0)
);

CREATE TABLE CUM_LAP_RAP (
    MaThanhPhan VARCHAR(50) PRIMARY KEY
                REFERENCES THANH_PHAN (MaThanhPhan) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE LINH_KIEN_CO_KHI (
    MaThanhPhan VARCHAR(50) PRIMARY KEY
                REFERENCES THANH_PHAN (MaThanhPhan) ON DELETE CASCADE ON UPDATE CASCADE,
    ChatLieu    VARCHAR(100),
    KichThuoc   VARCHAR(100)
);

CREATE TABLE LINH_KIEN_DIEN_TU (
    MaThanhPhan VARCHAR(50) PRIMARY KEY
                REFERENCES THANH_PHAN (MaThanhPhan) ON DELETE CASCADE ON UPDATE CASCADE,
    DienAp      NUMERIC(10,2),
    CongSuat    NUMERIC(10,2),
    CONSTRAINT CK_DT_DIEN_AP   CHECK (DienAp   IS NULL OR DienAp   >= 0),
    CONSTRAINT CK_DT_CONG_SUAT CHECK (CongSuat IS NULL OR CongSuat >= 0)
);

CREATE TABLE PHIEN_BAN_THIET_KE (
    MaPhienBan      VARCHAR(50)  PRIMARY KEY,
    MaThanhPhanGoc  VARCHAR(50)  NOT NULL
                    REFERENCES CUM_LAP_RAP (MaThanhPhan) ON UPDATE CASCADE,  -- gốc phải là cụm
    TenPhienBan     VARCHAR(255) NOT NULL,
    NgayTao         DATE DEFAULT CURRENT_DATE
);

CREATE TABLE QUAN_HE_CAU_THANH (
    MaPhienBan  VARCHAR(50) NOT NULL
                REFERENCES PHIEN_BAN_THIET_KE (MaPhienBan) ON UPDATE CASCADE,
    MaCha       VARCHAR(50) NOT NULL
                REFERENCES CUM_LAP_RAP (MaThanhPhan) ON UPDATE CASCADE,       -- cha phải là cụm
    MaCon       VARCHAR(50) NOT NULL
                REFERENCES THANH_PHAN (MaThanhPhan) ON UPDATE CASCADE,
    SoLuong     INT NOT NULL,
    PRIMARY KEY (MaPhienBan, MaCha, MaCon),
    CONSTRAINT CK_QHCT_SO_LUONG    CHECK (SoLuong > 0),
    CONSTRAINT CK_QHCT_KHONG_TU_CHUA CHECK (MaCha <> MaCon)
);

-- PostgreSQL KHÔNG tự tạo chỉ mục cho cột khóa ngoại nên phải tạo tay
CREATE INDEX IDX_THANH_PHAN_MALOAI    ON THANH_PHAN (MaLoai);
CREATE INDEX IDX_PHIEN_BAN_SP_GOC     ON PHIEN_BAN_THIET_KE (MaThanhPhanGoc);
CREATE INDEX IDX_QHCT_PHIENBAN_MACON  ON QUAN_HE_CAU_THANH (MaPhienBan, MaCon); -- truy vấn ngược (lên cha)
CREATE INDEX IDX_QHCT_MACON           ON QUAN_HE_CAU_THANH (MaCon);

/* ---------------------------------------------------------------------
   2. TRIGGER
   2.1 Disjoint: một thành phần chỉ thuộc đúng một bảng con
   2.2 Chống chu trình BOM (theo từng phiên bản)
   Dùng advisory lock để hai giao dịch song song không cùng vượt kiểm tra.
   (Đúng với READ COMMITTED mặc định.)
   --------------------------------------------------------------------- */
CREATE OR REPLACE FUNCTION trg_fn_disjoint() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    PERFORM pg_advisory_xact_lock(hashtext('tp:' || NEW.MaThanhPhan));
    IF (TG_TABLE_NAME <> 'cum_lap_rap'
        AND EXISTS (SELECT 1 FROM CUM_LAP_RAP WHERE MaThanhPhan = NEW.MaThanhPhan))
    OR (TG_TABLE_NAME <> 'linh_kien_co_khi'
        AND EXISTS (SELECT 1 FROM LINH_KIEN_CO_KHI WHERE MaThanhPhan = NEW.MaThanhPhan))
    OR (TG_TABLE_NAME <> 'linh_kien_dien_tu'
        AND EXISTS (SELECT 1 FROM LINH_KIEN_DIEN_TU WHERE MaThanhPhan = NEW.MaThanhPhan))
    THEN
        RAISE EXCEPTION 'Vi pham Disjoint: thanh phan % da thuoc mot loai khac', NEW.MaThanhPhan
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER TRG_CUM_DISJOINT BEFORE INSERT OR UPDATE OF MaThanhPhan ON CUM_LAP_RAP
    FOR EACH ROW EXECUTE FUNCTION trg_fn_disjoint();
CREATE TRIGGER TRG_CK_DISJOINT  BEFORE INSERT OR UPDATE OF MaThanhPhan ON LINH_KIEN_CO_KHI
    FOR EACH ROW EXECUTE FUNCTION trg_fn_disjoint();
CREATE TRIGGER TRG_DT_DISJOINT  BEFORE INSERT OR UPDATE OF MaThanhPhan ON LINH_KIEN_DIEN_TU
    FOR EACH ROW EXECUTE FUNCTION trg_fn_disjoint();

CREATE OR REPLACE FUNCTION trg_fn_chong_chu_trinh() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    PERFORM pg_advisory_xact_lock(hashtext('bom:' || NEW.MaPhienBan));

    IF NEW.MaCha = NEW.MaCon THEN
        RAISE EXCEPTION 'Vi pham: BOM co chu trinh (cha trung con)'
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;

    -- Từ con đi xuống; nếu gặp lại cha => thêm cạnh này sẽ tạo chu trình.
    -- Khi UPDATE, bỏ qua chính dòng cũ để không báo nhầm.
    IF EXISTS (
        WITH RECURSIVE duong_di AS (
            SELECT q.MaCon
            FROM QUAN_HE_CAU_THANH q
            WHERE q.MaPhienBan = NEW.MaPhienBan AND q.MaCha = NEW.MaCon
              AND (TG_OP = 'INSERT'
                   OR (q.MaPhienBan, q.MaCha, q.MaCon) IS DISTINCT FROM (OLD.MaPhienBan, OLD.MaCha, OLD.MaCon))
            UNION
            SELECT q.MaCon
            FROM QUAN_HE_CAU_THANH q
            JOIN duong_di d ON q.MaCha = d.MaCon
            WHERE q.MaPhienBan = NEW.MaPhienBan
              AND (TG_OP = 'INSERT'
                   OR (q.MaPhienBan, q.MaCha, q.MaCon) IS DISTINCT FROM (OLD.MaPhienBan, OLD.MaCha, OLD.MaCon))
        )
        SELECT 1 FROM duong_di WHERE MaCon = NEW.MaCha
    ) THEN
        RAISE EXCEPTION 'Vi pham: BOM co chu trinh (phien ban %, % -> %)', NEW.MaPhienBan, NEW.MaCha, NEW.MaCon
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER TRG_QHCT_CHONG_CHU_TRINH
    BEFORE INSERT OR UPDATE OF MaPhienBan, MaCha, MaCon ON QUAN_HE_CAU_THANH
    FOR EACH ROW EXECUTE FUNCTION trg_fn_chong_chu_trinh();

-- Kiểm tra Total / Disjoint sau khi nạp dữ liệu (Total không ép bằng trigger)
CREATE VIEW V_KIEM_TRA_TOTAL AS
SELECT tp.MaThanhPhan, tp.TenThanhPhan
FROM THANH_PHAN tp
WHERE NOT EXISTS (SELECT 1 FROM CUM_LAP_RAP c  WHERE c.MaThanhPhan  = tp.MaThanhPhan)
  AND NOT EXISTS (SELECT 1 FROM LINH_KIEN_CO_KHI k WHERE k.MaThanhPhan = tp.MaThanhPhan)
  AND NOT EXISTS (SELECT 1 FROM LINH_KIEN_DIEN_TU d WHERE d.MaThanhPhan = tp.MaThanhPhan);

CREATE VIEW V_KIEM_TRA_DISJOINT AS
SELECT MaThanhPhan, COUNT(*) AS SoLoai
FROM (SELECT MaThanhPhan FROM CUM_LAP_RAP
      UNION ALL SELECT MaThanhPhan FROM LINH_KIEN_CO_KHI
      UNION ALL SELECT MaThanhPhan FROM LINH_KIEN_DIEN_TU) x
GROUP BY MaThanhPhan HAVING COUNT(*) > 1;

-- MaLoai (đề cương: Cụm lắp ráp / Linh kiện cơ khí / Linh kiện điện tử) phải khớp bảng con thực tế
CREATE VIEW V_KIEM_TRA_LOAI AS
SELECT tp.MaThanhPhan, tp.MaLoai,
       CASE WHEN c.MaThanhPhan IS NOT NULL THEN 'CUM'
            WHEN k.MaThanhPhan IS NOT NULL THEN 'CK'
            WHEN d.MaThanhPhan IS NOT NULL THEN 'DT' END AS loai_theo_bang_con
FROM THANH_PHAN tp
LEFT JOIN CUM_LAP_RAP c ON c.MaThanhPhan = tp.MaThanhPhan
LEFT JOIN LINH_KIEN_CO_KHI k ON k.MaThanhPhan = tp.MaThanhPhan
LEFT JOIN LINH_KIEN_DIEN_TU d ON d.MaThanhPhan = tp.MaThanhPhan
WHERE tp.MaLoai IS DISTINCT FROM
      CASE WHEN c.MaThanhPhan IS NOT NULL THEN 'CUM'
           WHEN k.MaThanhPhan IS NOT NULL THEN 'CK'
           WHEN d.MaThanhPhan IS NOT NULL THEN 'DT' END;

-- Quy tắc nghiệp vụ "cụm có ít nhất 1 thành phần con" (kiểm tra sau khi nạp, không ép bằng trigger
-- vì phải cho phép tạo cụm trước rồi mới thêm con)
CREATE VIEW V_CUM_CHUA_CO_CON AS
SELECT c.MaThanhPhan
FROM CUM_LAP_RAP c
WHERE NOT EXISTS (SELECT 1 FROM QUAN_HE_CAU_THANH q WHERE q.MaCha = c.MaThanhPhan);

/* ---------------------------------------------------------------------
   3. DỮ LIỆU MẪU: LAPTOP GAMING PRO v1.0 (cây 4 cấp theo đề cương)
   Ốc vít M2x3 dùng chung ở 3 cụm (4 + 8 + 10 = 22 cái).
   Đơn giá là số minh họa. Số lượng Cụm RAM trong Cụm mainboard giả định = 1
   (đề cương không ghi).
   Mã phiên bản kèm mã sản phẩm (GP-v1.0) vì MaPhienBan là khóa chính toàn cục.
   --------------------------------------------------------------------- */
INSERT INTO LOAI_THANH_PHAN VALUES
 ('CUM', 'Cụm lắp ráp'),
 ('CK',  'Linh kiện cơ khí'),
 ('DT',  'Linh kiện điện tử');

INSERT INTO THANH_PHAN (MaThanhPhan, TenThanhPhan, MaLoai, DonGia, DonViTinh) VALUES
 -- cụm lắp ráp (DonGia NULL: chi phí = tổng chi phí con)
 ('LAPTOP_GP',       'Laptop Gaming Pro',            'CUM', NULL, 'Cái'),
 ('CUM_MANHINH',     'Cụm màn hình',                 'CUM', NULL, 'Cụm'),
 ('CUM_MAINBOARD',   'Cụm mainboard',                'CUM', NULL, 'Cụm'),
 ('CUM_RAM',         'Cụm RAM',                      'CUM', NULL, 'Cụm'),
 ('CUM_VO_TANNHIET', 'Cụm vỏ và tản nhiệt',          'CUM', NULL, 'Cụm'),
 -- linh kiện điện tử
 ('OLED_156',     'Tấm nền OLED 15.6 inch',         'DT', 4500000, 'Cái'),
 ('CAP_DP',       'Cáp màn hình DisplayPort',       'DT',  150000, 'Cái'),
 ('CPU_I7_13700H','CPU Intel Core i7-13700H',       'DT', 8500000, 'Cái'),
 ('GPU_RTX4060',  'GPU NVIDIA RTX 4060',            'DT', 9000000, 'Cái'),
 ('GPU_RTX4070',  'GPU NVIDIA RTX 4070',            'DT',12000000, 'Cái'),
 ('RAM_DDR5_16',  'Thanh RAM DDR5 16GB',            'DT', 1100000, 'Thanh'),
 ('SSD_1TB',      'SSD NVMe 1TB',                   'DT', 1700000, 'Cái'),
 ('SSD_2TB',      'SSD NVMe 2TB',                   'DT', 3000000, 'Cái'),
 ('QUAT_TN',      'Quạt tản nhiệt GPU/CPU',         'DT',  120000, 'Cái'),
 -- linh kiện cơ khí
 ('BAN_LE',       'Bản lề thép trái/phải',          'CK',   80000, 'Cái'),
 ('OC_M2X3',      'Ốc vít M2x3mm',                  'CK',     300, 'Cái'),
 ('VO_NHOM',      'Vỏ nhôm nguyên khối',            'CK', 1200000, 'Cái'),
 ('ONG_DONG',     'Ống đồng dẫn nhiệt',             'CK',   60000, 'Cái');

INSERT INTO CUM_LAP_RAP VALUES
 ('LAPTOP_GP'), ('CUM_MANHINH'), ('CUM_MAINBOARD'), ('CUM_RAM'), ('CUM_VO_TANNHIET');

INSERT INTO LINH_KIEN_CO_KHI (MaThanhPhan, ChatLieu, KichThuoc) VALUES
 ('BAN_LE',   'Thép không gỉ',   '2 chiếc trái/phải'),
 ('OC_M2X3',  'Thép mạ',         'M2x3 mm'),
 ('VO_NHOM',  'Nhôm nguyên khối','360x250x20 mm'),
 ('ONG_DONG', 'Đồng',            'phi 6 mm');

INSERT INTO LINH_KIEN_DIEN_TU (MaThanhPhan, DienAp, CongSuat) VALUES
 ('OLED_156',      5,    8),
 ('CAP_DP',        NULL, NULL),
 ('CPU_I7_13700H', 1.2,  45),
 ('GPU_RTX4060',   12,  115),
 ('GPU_RTX4070',   12,  140),
 ('RAM_DDR5_16',   1.1,   5),
 ('SSD_1TB',       3.3,   8),
 ('SSD_2TB',       3.3,   9),
 ('QUAT_TN',       5,     2);

INSERT INTO PHIEN_BAN_THIET_KE (MaPhienBan, MaThanhPhanGoc, TenPhienBan, NgayTao) VALUES
 ('GP-v1.0', 'LAPTOP_GP', 'Laptop Gaming Pro v1.0', DATE '2026-09-01');

INSERT INTO QUAN_HE_CAU_THANH (MaPhienBan, MaCha, MaCon, SoLuong) VALUES
 ('GP-v1.0', 'LAPTOP_GP',       'CUM_MANHINH',     1),
 ('GP-v1.0', 'LAPTOP_GP',       'CUM_MAINBOARD',   1),
 ('GP-v1.0', 'LAPTOP_GP',       'CUM_VO_TANNHIET', 1),
 ('GP-v1.0', 'CUM_MANHINH',     'OLED_156',        1),
 ('GP-v1.0', 'CUM_MANHINH',     'CAP_DP',          1),
 ('GP-v1.0', 'CUM_MANHINH',     'BAN_LE',          2),
 ('GP-v1.0', 'CUM_MANHINH',     'OC_M2X3',         4),
 ('GP-v1.0', 'CUM_MAINBOARD',   'CPU_I7_13700H',   1),
 ('GP-v1.0', 'CUM_MAINBOARD',   'GPU_RTX4060',     1),
 ('GP-v1.0', 'CUM_MAINBOARD',   'CUM_RAM',         1),
 ('GP-v1.0', 'CUM_MAINBOARD',   'SSD_1TB',         1),
 ('GP-v1.0', 'CUM_MAINBOARD',   'OC_M2X3',         8),
 ('GP-v1.0', 'CUM_RAM',         'RAM_DDR5_16',     2),
 ('GP-v1.0', 'CUM_VO_TANNHIET', 'VO_NHOM',         1),
 ('GP-v1.0', 'CUM_VO_TANNHIET', 'QUAT_TN',         2),
 ('GP-v1.0', 'CUM_VO_TANNHIET', 'ONG_DONG',        2),
 ('GP-v1.0', 'CUM_VO_TANNHIET', 'OC_M2X3',        10);

/* ---------------------------------------------------------------------
   4. TRUY VẤN ĐỆ QUY (Recursive CTE) đóng gói thành function
   --------------------------------------------------------------------- */

-- 4.1 Tất cả thành phần của sản phẩm theo phiên bản: cấp, số lượng nhân dồn, đường đi
CREATE OR REPLACE FUNCTION fn_tat_ca_thanh_phan(p_ma_phien_ban VARCHAR)
RETURNS TABLE (cap INT, ma_cha VARCHAR, ma_con VARCHAR, ten_con VARCHAR,
               ten_loai VARCHAR, so_luong_nhanh NUMERIC, duong TEXT)
LANGUAGE sql STABLE AS $$
    WITH RECURSIVE bom AS (
        SELECT 1 AS cap, q.MaCha, q.MaCon, q.SoLuong::NUMERIC AS sl_nhanh,
               ARRAY[q.MaCha, q.MaCon]::VARCHAR[] AS duong
        FROM QUAN_HE_CAU_THANH q
        JOIN PHIEN_BAN_THIET_KE pb
          ON pb.MaPhienBan = q.MaPhienBan AND pb.MaThanhPhanGoc = q.MaCha
        WHERE q.MaPhienBan = p_ma_phien_ban
        UNION ALL
        SELECT b.cap + 1, q.MaCha, q.MaCon, b.sl_nhanh * q.SoLuong, b.duong || q.MaCon
        FROM bom b
        JOIN QUAN_HE_CAU_THANH q ON q.MaPhienBan = p_ma_phien_ban AND q.MaCha = b.MaCon
    )
    SELECT b.cap, b.MaCha, b.MaCon, t.TenThanhPhan, l.TenLoai, b.sl_nhanh,
           array_to_string(b.duong, ' > ')
    FROM bom b
    JOIN THANH_PHAN t ON t.MaThanhPhan = b.MaCon
    JOIN LOAI_THANH_PHAN l ON l.MaLoai = t.MaLoai
    ORDER BY b.duong;
$$;

-- 4.2 Tổng lượng cần dùng của từng linh kiện lá (gộp mọi nhánh dùng chung)
CREATE OR REPLACE FUNCTION fn_tong_luong_can_dung(p_ma_phien_ban VARCHAR)
RETURNS TABLE (ma_thanh_phan VARCHAR, ten_thanh_phan VARCHAR, don_vi_tinh VARCHAR, tong_so_luong NUMERIC)
LANGUAGE sql STABLE AS $$
    WITH RECURSIVE bom AS (
        SELECT q.MaCon, q.SoLuong::NUMERIC AS sl
        FROM QUAN_HE_CAU_THANH q
        JOIN PHIEN_BAN_THIET_KE pb
          ON pb.MaPhienBan = q.MaPhienBan AND pb.MaThanhPhanGoc = q.MaCha
        WHERE q.MaPhienBan = p_ma_phien_ban
        UNION ALL
        SELECT q.MaCon, b.sl * q.SoLuong
        FROM bom b
        JOIN QUAN_HE_CAU_THANH q ON q.MaPhienBan = p_ma_phien_ban AND q.MaCha = b.MaCon
    )
    SELECT t.MaThanhPhan, t.TenThanhPhan, t.DonViTinh, SUM(b.sl)
    FROM bom b
    JOIN THANH_PHAN t ON t.MaThanhPhan = b.MaCon
    WHERE NOT EXISTS (SELECT 1 FROM CUM_LAP_RAP c WHERE c.MaThanhPhan = b.MaCon)  -- chỉ linh kiện lá
    GROUP BY t.MaThanhPhan, t.TenThanhPhan, t.DonViTinh
    ORDER BY t.MaThanhPhan;
$$;

-- 4.3 Chi phí cấu thành sản phẩm = Σ(SL lá nhân dồn × DonGia lá)   (Truy vấn nghiệp vụ 2)
CREATE OR REPLACE FUNCTION fn_chi_phi_cau_thanh(p_ma_phien_ban VARCHAR)
RETURNS TABLE (tong_chi_phi NUMERIC)
LANGUAGE sql STABLE AS $$
    WITH RECURSIVE bom AS (
        SELECT q.MaCon, q.SoLuong::NUMERIC AS sl
        FROM QUAN_HE_CAU_THANH q
        JOIN PHIEN_BAN_THIET_KE pb
          ON pb.MaPhienBan = q.MaPhienBan AND pb.MaThanhPhanGoc = q.MaCha
        WHERE q.MaPhienBan = p_ma_phien_ban
        UNION ALL
        SELECT q.MaCon, b.sl * q.SoLuong
        FROM bom b
        JOIN QUAN_HE_CAU_THANH q ON q.MaPhienBan = p_ma_phien_ban AND q.MaCha = b.MaCon
    )
    SELECT COALESCE(SUM(b.sl * COALESCE(t.DonGia, 0)), 0)
    FROM bom b
    JOIN THANH_PHAN t ON t.MaThanhPhan = b.MaCon
    WHERE NOT EXISTS (SELECT 1 FROM CUM_LAP_RAP c WHERE c.MaThanhPhan = b.MaCon);  -- chỉ cấp lá
$$;

-- 4.3b Chi phí tích lũy của TỪNG cụm trong phiên bản (quy tắc: chi phí cụm = tổng chi phí con)
CREATE OR REPLACE FUNCTION fn_chi_phi_tung_cum(p_ma_phien_ban VARCHAR)
RETURNS TABLE (ma_cum VARCHAR, ten_cum VARCHAR, chi_phi NUMERIC)
LANGUAGE sql STABLE AS $$
    WITH RECURSIVE goc AS (
        SELECT DISTINCT q.MaCha AS ma_goc
        FROM QUAN_HE_CAU_THANH q WHERE q.MaPhienBan = p_ma_phien_ban
    ),
    bom AS (
        SELECT g.ma_goc, q.MaCon, q.SoLuong::NUMERIC AS sl
        FROM goc g
        JOIN QUAN_HE_CAU_THANH q ON q.MaPhienBan = p_ma_phien_ban AND q.MaCha = g.ma_goc
        UNION ALL
        SELECT b.ma_goc, q.MaCon, b.sl * q.SoLuong
        FROM bom b
        JOIN QUAN_HE_CAU_THANH q ON q.MaPhienBan = p_ma_phien_ban AND q.MaCha = b.MaCon
    )
    SELECT g.ma_goc, t.TenThanhPhan, COALESCE(SUM(b.sl * COALESCE(lf.DonGia, 0)), 0)
    FROM goc g
    JOIN THANH_PHAN t ON t.MaThanhPhan = g.ma_goc
    LEFT JOIN bom b ON b.ma_goc = g.ma_goc
                   AND NOT EXISTS (SELECT 1 FROM CUM_LAP_RAP c WHERE c.MaThanhPhan = b.MaCon)
    LEFT JOIN THANH_PHAN lf ON lf.MaThanhPhan = b.MaCon
    GROUP BY g.ma_goc, t.TenThanhPhan
    ORDER BY g.ma_goc;
$$;

-- 4.4 Sản phẩm/phiên bản bị ảnh hưởng khi một thành phần thay đổi (đi NGƯỢC lên cha).
--     Dùng UNION (khử trùng) để cấu trúc dùng chung không làm số dòng đệ quy phình ra.
CREATE OR REPLACE FUNCTION fn_san_pham_bi_anh_huong(p_ma_thanh_phan VARCHAR)
RETURNS TABLE (ma_san_pham VARCHAR, ten_san_pham VARCHAR, ma_phien_ban VARCHAR, ten_phien_ban VARCHAR)
LANGUAGE sql STABLE AS $$
    WITH RECURSIVE len AS (
        SELECT q.MaPhienBan, q.MaCha
        FROM QUAN_HE_CAU_THANH q
        WHERE q.MaCon = p_ma_thanh_phan
        UNION
        SELECT q.MaPhienBan, q.MaCha
        FROM len u
        JOIN QUAN_HE_CAU_THANH q ON q.MaPhienBan = u.MaPhienBan AND q.MaCon = u.MaCha
    )
    SELECT pb.MaThanhPhanGoc, t.TenThanhPhan, pb.MaPhienBan, pb.TenPhienBan
    FROM len u
    JOIN PHIEN_BAN_THIET_KE pb
      ON pb.MaPhienBan = u.MaPhienBan AND pb.MaThanhPhanGoc = u.MaCha   -- tổ tiên là gốc của phiên bản
    JOIN THANH_PHAN t ON t.MaThanhPhan = pb.MaThanhPhanGoc
    ORDER BY pb.MaPhienBan;
$$;

-- 4.5 Các cụm bị ảnh hưởng (mọi tổ tiên) khi một thành phần đổi, trong một phiên bản
CREATE OR REPLACE FUNCTION fn_cum_bi_anh_huong(p_ma_phien_ban VARCHAR, p_ma_thanh_phan VARCHAR)
RETURNS TABLE (ma_cum VARCHAR, ten_cum VARCHAR)
LANGUAGE sql STABLE AS $$
    WITH RECURSIVE len AS (
        SELECT q.MaCha FROM QUAN_HE_CAU_THANH q
        WHERE q.MaPhienBan = p_ma_phien_ban AND q.MaCon = p_ma_thanh_phan
        UNION
        SELECT q.MaCha FROM len u
        JOIN QUAN_HE_CAU_THANH q ON q.MaPhienBan = p_ma_phien_ban AND q.MaCon = u.MaCha
    )
    SELECT t.MaThanhPhan, t.TenThanhPhan
    FROM len u JOIN THANH_PHAN t ON t.MaThanhPhan = u.MaCha
    ORDER BY t.MaThanhPhan;
$$;

/* ---------------------------------------------------------------------
   5. STORED PROCEDURE (thao tác ghi; mỗi CALL là một đơn vị nguyên tử:
      lỗi giữa chừng thì toàn bộ thay đổi của CALL đó bị hủy)
   --------------------------------------------------------------------- */

CREATE OR REPLACE PROCEDURE sp_them_quan_he_cau_thanh(
    p_ma_phien_ban VARCHAR, p_ma_cha VARCHAR, p_ma_con VARCHAR, p_so_luong INT)
LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO QUAN_HE_CAU_THANH (MaPhienBan, MaCha, MaCon, SoLuong)
    VALUES (p_ma_phien_ban, p_ma_cha, p_ma_con, p_so_luong);   -- trigger chặn chu trình
END $$;

CREATE OR REPLACE PROCEDURE sp_sao_chep_phien_ban(
    p_pb_cu VARCHAR, p_pb_moi VARCHAR, p_ten_moi VARCHAR)
LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO PHIEN_BAN_THIET_KE (MaPhienBan, MaThanhPhanGoc, TenPhienBan, NgayTao)
    SELECT p_pb_moi, MaThanhPhanGoc, p_ten_moi, CURRENT_DATE
    FROM PHIEN_BAN_THIET_KE WHERE MaPhienBan = p_pb_cu;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Khong tim thay phien ban %', p_pb_cu;
    END IF;

    INSERT INTO QUAN_HE_CAU_THANH (MaPhienBan, MaCha, MaCon, SoLuong)
    SELECT p_pb_moi, MaCha, MaCon, SoLuong
    FROM QUAN_HE_CAU_THANH WHERE MaPhienBan = p_pb_cu;
END $$;

-- Thay một thành phần bằng thành phần khác ở MỌI cụm trong một phiên bản
CREATE OR REPLACE PROCEDURE sp_thay_linh_kien(
    p_ma_phien_ban VARCHAR, p_ma_cu VARCHAR, p_ma_moi VARCHAR)
LANGUAGE plpgsql AS $$
DECLARE v_so_dong INT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM THANH_PHAN WHERE MaThanhPhan = p_ma_moi) THEN
        RAISE EXCEPTION 'Thanh phan moi % khong ton tai', p_ma_moi;
    END IF;
    UPDATE QUAN_HE_CAU_THANH SET MaCon = p_ma_moi
    WHERE MaPhienBan = p_ma_phien_ban AND MaCon = p_ma_cu;
    GET DIAGNOSTICS v_so_dong = ROW_COUNT;
    IF v_so_dong = 0 THEN
        RAISE EXCEPTION 'Phien ban % khong dung thanh phan %', p_ma_phien_ban, p_ma_cu;
    END IF;
    RAISE NOTICE 'Da doi % dong: % -> % (phien ban %)', v_so_dong, p_ma_cu, p_ma_moi, p_ma_phien_ban;
END $$;

/* ---------------------------------------------------------------------
   6. KIỂM THỬ (kết quả kỳ vọng ghi trong chú thích)
   --------------------------------------------------------------------- */
-- --- 6.1 Total / Disjoint / Loại / Cụm rỗng: cả bốn view phải trả 0 dòng
SELECT * FROM V_KIEM_TRA_TOTAL;
SELECT * FROM V_KIEM_TRA_DISJOINT;
SELECT * FROM V_KIEM_TRA_LOAI;
SELECT * FROM V_CUM_CHUA_CO_CON;

-- --- 6.2 Truy vấn 1 - Nổ BOM GP-v1.0: 3 cấp, OC_M2X3 xuất hiện ở 3 nhánh
SELECT * FROM fn_tat_ca_thanh_phan('GP-v1.0');

-- --- 6.3 Tổng lượng lá: OC_M2X3=22, RAM_DDR5_16=2, QUAT_TN=2, ONG_DONG=2, BAN_LE=2, còn lại 1
SELECT * FROM fn_tong_luong_can_dung('GP-v1.0');

-- --- 6.4 Truy vấn 2 - Chi phí GP-v1.0 = 27.776.600
SELECT * FROM fn_chi_phi_cau_thanh('GP-v1.0');
--     Từng cụm: CUM_RAM 2.200.000; CUM_MANHINH 4.811.200; CUM_MAINBOARD 21.402.400; CUM_VO_TANNHIET 1.563.000; LAPTOP_GP 27.776.600
SELECT * FROM fn_chi_phi_tung_cum('GP-v1.0');

-- --- 6.5 Truy vấn 3 - Ảnh hưởng khi OC_M2X3 đổi: sản phẩm GP-v1.0; cụm: CUM_MANHINH, CUM_MAINBOARD, CUM_VO_TANNHIET, LAPTOP_GP
SELECT * FROM fn_san_pham_bi_anh_huong('OC_M2X3');
SELECT * FROM fn_cum_bi_anh_huong('GP-v1.0', 'OC_M2X3');
--     Đổi RAM_DDR5_16: CUM_RAM, CUM_MAINBOARD, LAPTOP_GP (đi qua 3 cấp)
SELECT * FROM fn_cum_bi_anh_huong('GP-v1.0', 'RAM_DDR5_16');

-- --- 6.6 Chống chu trình: các lệnh dưới đây PHẢI báo lỗi (được bắt và in NOTICE)
DO $$
BEGIN
    BEGIN CALL sp_them_quan_he_cau_thanh('GP-v1.0', 'CUM_RAM', 'CUM_MAINBOARD', 1);
    EXCEPTION WHEN integrity_constraint_violation THEN RAISE NOTICE 'OK (gian tiep 1 cap): %', SQLERRM; END;
    BEGIN CALL sp_them_quan_he_cau_thanh('GP-v1.0', 'CUM_RAM', 'LAPTOP_GP', 1);
    EXCEPTION WHEN integrity_constraint_violation THEN RAISE NOTICE 'OK (gian tiep 2 cap): %', SQLERRM; END;
    BEGIN INSERT INTO QUAN_HE_CAU_THANH VALUES ('GP-v1.0', 'CUM_RAM', 'CUM_RAM', 1);
    EXCEPTION WHEN OTHERS THEN RAISE NOTICE 'OK (tu chua): %', SQLERRM; END;
END $$;

-- --- 6.7 Disjoint: gán OC_M2X3 (cơ khí) vào bảng điện tử PHẢI báo lỗi
DO $$
BEGIN
    BEGIN INSERT INTO LINH_KIEN_DIEN_TU (MaThanhPhan, DienAp, CongSuat) VALUES ('OC_M2X3', 1, 1);
    EXCEPTION WHEN integrity_constraint_violation THEN RAISE NOTICE 'OK: %', SQLERRM; END;
END $$;

-- --- 6.8 Giao dịch nhiều thành phần: tạo v2.0, đổi GPU và SSD trong cùng 1 transaction
BEGIN;
CALL sp_sao_chep_phien_ban('GP-v1.0', 'GP-v2.0', 'Laptop Gaming Pro v2.0');
CALL sp_thay_linh_kien('GP-v2.0', 'GPU_RTX4060', 'GPU_RTX4070');
CALL sp_thay_linh_kien('GP-v2.0', 'SSD_1TB', 'SSD_2TB');
COMMIT;
SELECT * FROM fn_chi_phi_cau_thanh('GP-v2.0');        -- kỳ vọng 32.076.600
SELECT * FROM fn_chi_phi_cau_thanh('GP-v1.0');        -- v1 không đổi: 27.776.600
SELECT * FROM fn_san_pham_bi_anh_huong('GPU_RTX4060');  -- chỉ GP-v1.0
SELECT * FROM fn_san_pham_bi_anh_huong('OC_M2X3');      -- GP-v1.0 và GP-v2.0

-- --- 6.9 Rollback: lỗi giữa chừng thì cả giao dịch bị hủy (v3 không được tồn tại)
BEGIN;
CALL sp_sao_chep_phien_ban('GP-v1.0', 'GP-v3.0', 'Bản thử lỗi');
DO $$ BEGIN CALL sp_thay_linh_kien('GP-v3.0', 'KHONG_TON_TAI', 'GPU_RTX4070'); END $$;  -- gây lỗi
ROLLBACK;
SELECT COUNT(*) AS so_phien_ban_v3 FROM PHIEN_BAN_THIET_KE WHERE MaPhienBan = 'GP-v3.0';  -- 0
