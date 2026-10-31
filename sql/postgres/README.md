# sql/postgres – Script PostgreSQL (TV2)

Mô hình quan hệ cho cấu trúc sản phẩm (BOM). Sản phẩm mẫu: Laptop Gaming Pro.

| File | Nội dung |
|---|---|
| `TV2_BOM_PostgreSQL_pgAdmin.sql` | Toàn bộ DDL, trigger, dữ liệu mẫu, truy vấn đệ quy, stored procedure và kiểm thử. Chạy được trong pgAdmin Query Tool, DBeaver và psql |

Sơ đồ ERD: `docs/erd/TV2_ERD_PostgreSQL.png`. Báo cáo: `report/TV2_Tuan1-2_MoHinhQuanHe.docx`.

## Yêu cầu

- Docker Desktop (đang chạy).
- pgAdmin4 cài trên máy.

## Bước 1. Bật PostgreSQL bằng Docker

Tại thư mục gốc của dự án (nơi có `docker-compose.yml`):

```
docker compose up -d
```

Kiểm tra: `docker compose ps` phải thấy container `csdl_postgres` ở trạng thái running.

## Bước 2. Kết nối pgAdmin

Trong pgAdmin: chuột phải **Servers**, chọn **Register > Server...**

- Tab **General**: Name = `CSDL_NANG_CAO`
- Tab **Connection**:

| Mục | Giá trị |
|---|---|
| Host name/address | `localhost` |
| Port | `5434` |
| Maintenance database | `csdl_nang_cao` |
| Username | `postgres` |
| Password | `csdl` |

## Bước 3. Chạy script

1. Chọn database `csdl_nang_cao`, mở **Query Tool** (Tools > Query Tool).
2. Dán toàn bộ nội dung `TV2_BOM_PostgreSQL_pgAdmin.sql`, nhấn **F5**.
3. Xem thông báo kiểm thử ở tab **Messages**. Có một dòng `ERROR ... GP-v3.0 khong dung thanh phan KHONG_TON_TAI`; đây là lỗi cố ý của bước kiểm thử rollback, không phải lỗi script.
4. Sau lỗi đó, chạy riêng đoạn dưới để đóng giao dịch đang mở và xác nhận rollback:

```sql
ROLLBACK;
SET search_path TO csdl_pre;
SELECT COUNT(*) AS so_phien_ban_v3 FROM PHIEN_BAN_THIET_KE WHERE MaPhienBan = 'GP-v3.0';  -- kỳ vọng 0
```

Script **xóa và tạo lại schema `csdl_pre`** mỗi lần chạy, nên chạy lại nhiều lần được.

## Kết quả cần đối chiếu

Mở Query Tool mới, chạy `SET search_path TO csdl_pre;` rồi chạy từng câu:

| Câu lệnh | Kết quả kỳ vọng |
|---|---|
| `SELECT * FROM fn_tong_luong_can_dung('GP-v1.0');` | Ốc vít M2x3 = 22; RAM, quạt, ống đồng, bản lề = 2; còn lại = 1 |
| `SELECT * FROM fn_chi_phi_cau_thanh('GP-v1.0');` | 27.776.600 |
| `SELECT * FROM fn_chi_phi_cau_thanh('GP-v2.0');` | 32.076.600 |
| `SELECT * FROM fn_san_pham_bi_anh_huong('OC_M2X3');` | GP-v1.0 và GP-v2.0 |

Query Tool chỉ hiện kết quả của câu `SELECT` cuối trong mỗi lần chạy, nên chạy từng câu một.

## Các lệnh Docker hay dùng

| Việc | Lệnh |
|---|---|
| Tắt (giữ dữ liệu) | `docker compose down` |
| Xóa sạch dữ liệu, làm lại từ đầu | `docker compose down -v` |
| Xem log | `docker compose logs postgres` |

Lỗi trùng cổng `5434`: đổi số cổng bên trái trong `docker-compose.yml` (ví dụ `"5435:5432"`) và dùng cổng mới ở bước 2.

## Nạp dữ liệu CSV (kịch bản A–D)

CSV nằm ở `data/scenarios/`. Để nạp bằng `\copy` trong container, thêm hai dòng vào phần `volumes` của dịch vụ `postgres` trong `docker-compose.yml`, rồi chạy lại `docker compose up -d`:

```yaml
      - ./sql/postgres:/sql:ro
      - ./data:/data:ro
```

Nạp theo thứ tự khóa ngoại: LOAI_THANH_PHAN, THANH_PHAN, CUM_LAP_RAP, LINH_KIEN_CO_KHI, LINH_KIEN_DIEN_TU, PHIEN_BAN_THIET_KE, QUAN_HE_CAU_THANH. Với kịch bản lớn, tắt trigger khi nạp rồi bật lại và kiểm tra chu trình bằng truy vấn đệ quy sau khi nạp.

## Lưu ý

- Đơn giá là giá hiện tại, không lưu theo phiên bản.
- Mật khẩu `csdl` chỉ dành cho môi trường học tập trên máy cá nhân.
