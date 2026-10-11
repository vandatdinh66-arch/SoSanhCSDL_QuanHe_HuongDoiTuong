# Đặc tả nghiệp vụ và quy tắc dữ liệu

**Đồ án 5:** So sánh CSDL quan hệ và CSDL hướng đối tượng trong quản lý cấu trúc sản phẩm
**Sản phẩm minh họa:** Laptop
**Phiên bản tài liệu:** 0.1 (bản nháp, nộp tuần 3)

---

## 1. Mục đích và phạm vi

Hệ thống lưu trữ và truy vấn **cấu trúc cấu thành (BOM, Bill of Materials)** của sản phẩm nhiều cấp: sản phẩm gồm các cụm lắp ráp, cụm lắp ráp gồm cụm con và linh kiện. Cùng một bài toán được cài đặt trên hai mô hình (quan hệ và hướng đối tượng) để so sánh về biểu diễn, truy vấn và thay đổi cấu trúc.

**Trong phạm vi:**
- Danh mục thành phần (cụm lắp ráp, linh kiện cơ khí, linh kiện điện tử).
- Quan hệ cấu thành kèm số lượng.
- Phiên bản thiết kế của sản phẩm.
- Truy vấn: nổ BOM, tổng lượng cần dùng, chi phí cấu thành, where-used, so sánh phiên bản.
- Thay đổi cấu trúc: thêm, xóa, thay thế thành phần, sửa số lượng.

**Ngoài phạm vi:** quản lý kho, mua hàng, nhà cung cấp, sản xuất thực tế, phân quyền người dùng chi tiết, giao diện web.

## 2. Đối tượng sử dụng

| Vai trò | Nhu cầu chính |
|---|---|
| Kỹ sư thiết kế | Tạo và chỉnh sửa cấu trúc sản phẩm, so sánh phiên bản |
| Bộ phận mua hàng / kế hoạch | Biết tổng lượng từng linh kiện cần cho một sản phẩm, tính chi phí |
| Bộ phận chất lượng | Khi một linh kiện lỗi hoặc đổi, biết cụm và sản phẩm nào bị ảnh hưởng |

## 3. Mô tả sản phẩm minh họa: Laptop

```
Laptop LT-100 (sản phẩm)
├── Vỏ máy (cụm)
│   ├── Khung nhôm (cơ khí) x1
│   ├── Bản lề (cơ khí) x2
│   └── Vít M2x3 (cơ khí) x12
├── Bo mạch chủ (cụm)
│   ├── Tụ điện 10uF (điện tử) x40
│   ├── Điện trở 1kΩ (điện tử) x60
│   ├── Chip xử lý (điện tử) x1
│   └── Vít M2x3 (cơ khí) x6
├── Màn hình (cụm)
│   ├── Tấm nền LCD (điện tử) x1
│   └── Vít M2x3 (cơ khí) x4
├── Khối pin (cụm)
│   ├── Cell pin (điện tử) x6
│   └── Mạch bảo vệ pin (cụm)
│       ├── Tụ điện 10uF (điện tử) x4
│       └── Điện trở 1kΩ (điện tử) x8
└── Hệ thống tản nhiệt (cụm)
    ├── Quạt (cụm)
    │   ├── Motor quạt (điện tử) x1
    │   └── Cánh quạt (cơ khí) x1
    └── Ống đồng tản nhiệt (cơ khí) x2
```

Nhận xét quan trọng cho thiết kế:
- **Vít M2x3, tụ điện 10uF, điện trở 1kΩ xuất hiện ở nhiều cụm**, nên cấu trúc là đồ thị có hướng không chu trình (DAG), không phải cây.
- Số lượng gắn với **từng cặp (cụm, thành phần con)**, không gắn với bản thân linh kiện (cùng là vít M2x3 nhưng 12 cái ở vỏ máy, 6 cái ở bo mạch).
- **Quạt vừa có phần cơ khí vừa có phần điện tử**, nên được mô hình hóa là một **cụm** chứa motor (điện tử) và cánh quạt (cơ khí), nhờ vậy mỗi linh kiện chỉ thuộc đúng một loại.

## 4. Thuật ngữ

| Thuật ngữ | Ý nghĩa |
|---|---|
| Thành phần | Khái niệm chung cho mọi thứ có thể nằm trong cấu trúc sản phẩm |
| Sản phẩm | Thành phần gốc, không là con của thành phần nào trong cùng phiên bản (ví dụ Laptop LT-100) |
| Cụm lắp ráp | Thành phần được tạo từ các thành phần con |
| Linh kiện | Thành phần lá, không có thành phần con. Gồm linh kiện cơ khí và linh kiện điện tử |
| Quan hệ cấu thành | Bộ ba (cha, con, số lượng) trong một phiên bản |
| Phiên bản | Một bản thiết kế của sản phẩm, có tập quan hệ cấu thành riêng |
| Nổ BOM | Triển khai toàn bộ cấu trúc từ một sản phẩm xuống tới linh kiện |
| Where-used | Truy ngược: một thành phần được dùng trong những cụm và sản phẩm nào |

## 5. Quy tắc dữ liệu

### 5.1 Thành phần

| Mã | Quy tắc |
|---|---|
| BR-01 | Mỗi thành phần có mã duy nhất, tên, loại và mô tả. Mã không đổi sau khi tạo. |
| BR-02 | Mỗi thành phần thuộc đúng **một** loại: cụm lắp ráp, linh kiện cơ khí hoặc linh kiện điện tử (chuyên biệt hóa **rời nhau**, **toàn phần**). |
| BR-03 | Linh kiện cơ khí có thêm: vật liệu, dung sai. Linh kiện điện tử có thêm: điện áp định mức, công suất. |
| BR-04 | Đơn giá chỉ lưu ở **linh kiện**, lớn hơn hoặc bằng 0. Chi phí của cụm và sản phẩm là **giá trị tính được**, không lưu. |
| BR-05 | Không xóa thành phần đang được dùng trong bất kỳ quan hệ cấu thành nào (chặn xóa). |

### 5.2 Quan hệ cấu thành

| Mã | Quy tắc |
|---|---|
| BR-06 | Chỉ **cụm lắp ráp** (hoặc sản phẩm) mới có thể là cha. Linh kiện không có con. |
| BR-07 | Một thành phần có thể là con của **nhiều** cụm (dùng chung). |
| BR-08 | Mỗi quan hệ có **số lượng** là số nguyên dương. |
| BR-09 | Trong cùng một phiên bản, một cặp (cha, con) chỉ xuất hiện **một lần** (muốn đổi số lượng thì sửa, không thêm dòng trùng). |
| BR-10 | **Cấm chu trình**: một thành phần không được trực tiếp hay gián tiếp chứa chính nó. Kiểm tra khi thêm hoặc sửa quan hệ. |
| BR-11 | Không có cấp tối đa cố định, nhưng hệ thống thử nghiệm độ sâu tới 12 cấp. |

### 5.3 Phiên bản

| Mã | Quy tắc |
|---|---|
| BR-12 | Mỗi sản phẩm có một hoặc nhiều phiên bản, mỗi phiên bản có số hiệu duy nhất trong sản phẩm (ví dụ v1.0, v1.1) và ngày tạo. |
| BR-13 | Quan hệ cấu thành thuộc về **một phiên bản cụ thể**. Hai phiên bản có thể dùng cùng thành phần nhưng khác cấu trúc hoặc số lượng. |
| BR-14 | Phiên bản có trạng thái: nháp, phát hành, ngừng dùng. Phiên bản **đã phát hành không được sửa** cấu trúc, muốn đổi phải tạo phiên bản mới (sao chép rồi chỉnh). |
| BR-15 | Cụm lắp ráp trong phiên bản đã phát hành phải có ít nhất một thành phần con. |

### 5.4 Tính toán

| Mã | Quy tắc |
|---|---|
| BR-16 | **Tổng lượng cần dùng** của linh kiện L trong sản phẩm P = tổng, trên mọi đường đi từ P tới L, của tích các số lượng dọc đường. Ví dụ: vít M2x3 trong Laptop = 12 + 6 + 4 = 22. |
| BR-17 | **Chi phí cấu thành** của P = tổng (tổng lượng cần dùng của từng linh kiện x đơn giá). |
| BR-18 | **Where-used** của L trong một phiên bản = mọi cụm và sản phẩm nằm trên các đường đi tới L. |

## 6. Các nghiệp vụ cần hỗ trợ

| # | Nghiệp vụ | Loại |
|---|---|---|
| N1 | Liệt kê thành phần con trực tiếp của một cụm | Truy vấn |
| N2 | Nổ BOM đầy đủ của một sản phẩm (có cấp và số lượng dồn) | Truy vấn đệ quy |
| N3 | Tổng lượng từng linh kiện cần cho một sản phẩm | Truy vấn đệ quy, tổng hợp |
| N4 | Tổng chi phí cấu thành của một sản phẩm hoặc cụm | Truy vấn đệ quy, tổng hợp |
| N5 | Where-used: linh kiện X đổi thì cụm và sản phẩm nào bị ảnh hưởng | Truy vấn đệ quy ngược |
| N6 | Linh kiện được dùng chung ở nhiều cụm nhất | Truy vấn nhóm |
| N7 | Độ sâu lớn nhất của cấu trúc một sản phẩm | Truy vấn đệ quy |
| N8 | So sánh hai phiên bản: thêm, bớt, đổi số lượng | Truy vấn tập hợp |
| N9 | Lọc linh kiện điện tử theo điện áp, linh kiện cơ khí theo vật liệu | Truy vấn đa hình |
| N10 | Tìm cụm không có thành phần con | Truy vấn kiểm tra |
| N11 | Thêm cụm con vào cụm, có kiểm tra chu trình | Cập nhật |
| N12 | Sửa số lượng của một quan hệ cấu thành | Cập nhật |
| N13 | Thay linh kiện A bằng linh kiện B ở mọi cụm trong một phiên bản | Cập nhật hàng loạt (transaction) |
| N14 | Sao chép một phiên bản thành phiên bản mới | Cập nhật hàng loạt (transaction) |

## 7. Giả định

1. Một sản phẩm tại một thời điểm có nhiều phiên bản, nhưng mỗi thành phần chỉ có **một** bản mô tả (không quản lý phiên bản riêng cho từng linh kiện).
2. Đơn giá là một giá trị hiện hành, không lưu lịch sử giá.
3. Số lượng là số nguyên (không xét vật tư tính theo khối lượng hay chiều dài).
4. Đơn vị tiền tệ thống nhất (VND), không xử lý quy đổi.

## 8. Quy mô dữ liệu cho thực nghiệm

| Bộ dữ liệu | Số thành phần | Độ sâu | Mức dùng chung linh kiện |
|---|---|---|---|
| Mẫu (demo, kiểm thử đúng/sai) | khoảng 30 (đúng như cây Laptop ở mục 3) | 3 | Trung bình |
| Nhỏ | 1.000 | 3, 5, 8 | Thấp, trung bình, cao |
| Vừa | 10.000 | 5, 8, 12 | Thấp, trung bình, cao |
| Lớn | 100.000 | 5, 8, 12 | Thấp, trung bình, cao |

Dữ liệu sinh bằng chương trình với tham số (độ sâu, độ rộng, tỷ lệ dùng chung, **seed cố định**) để chạy lại cho kết quả giống nhau.

## 9. Gợi ý ban đầu cho sơ đồ EER

**Thực thể:** `ThanhPhan` (tổng quát) chuyên biệt hóa rời nhau, toàn phần thành `CumLapRap`, `LinhKienCoKhi`, `LinhKienDienTu`; `SanPham` (có thể là một `CumLapRap` đặc biệt hoặc thực thể riêng, cần nhóm quyết định); `PhienBan`.

**Mối quan hệ:** `CauThanh` là quan hệ đệ quy nhiều-nhiều trên `ThanhPhan` (vai trò cha và con), có thuộc tính `so_luong` và gắn với `PhienBan` (có thể mô hình hóa thành quan hệ bậc ba hoặc thực thể kết hợp).

## 10. Câu hỏi cần nhóm thống nhất

1. `SanPham` là thực thể riêng hay chỉ là `CumLapRap` ở gốc? (Ảnh hưởng đến lớp đối tượng và số bảng.)
2. Có cho phép một thành phần là con của cụm trong phiên bản này nhưng đứng riêng (không có cha) trong phiên bản khác không? (Mặc định: có.)
3. Giảng viên có yêu cầu đúng sản phẩm Laptop hay nhóm được tự chọn? (Nếu đã chốt Laptop thì giữ nguyên.)
4. Trạng thái phiên bản (nháp/phát hành/ngừng dùng) có cần cài đặt trong thực nghiệm hay chỉ mô tả trong báo cáo?
