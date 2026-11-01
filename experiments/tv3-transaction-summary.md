# TV3 - Bo so lieu thuc nghiem thao tac cap nhat/giao dich

Sinh tu `OodbApplication tx-experiment`, chay tren du lieu thuc cua TV5 (kich ban: A, B, C, D).

## 1. Chuoi thao tac N11-N14 (file `tv3-transaction-log.csv`)

| Kich ban | OK (thanh cong dung mong doi) | OK_CHAN_DUNG (bi chan dung mong doi) | LOI_KHONG_MONG_DOI |
|---|---|---|---|
| A | 6 | 5 | 0 |
| B | 6 | 5 | 0 |
| C | 6 | 5 | 0 |
| D | 6 | 5 | 0 |

`LOI_KHONG_MONG_DOI` phai luon bang 0 - neu khac 0 nghia la logic ngan chan (chu trinh, BR-14, BR-09...) khong hoat dong dung nhu thiet ke tren du lieu that.

## 2. Thuc nghiem gioi han tuong duong / dong thoi (file `tv3-concurrency-log.csv`)

- `KHONG_XUNG_DOT`: N thread ghi dong thoi vao N canh (cha,con) khac nhau - ky vong tat ca thanh cong.
- `XUNG_DOT_CUNG_CANH`: N thread cung sua so luong cua **mot** canh duy nhat voi N gia tri dich khac nhau, khong dung khoa ung dung (chi dua vao transaction cua ObjectDB). Cot `nhat_quan=true` nghia la gia tri cuoi cung luu trong CSDL **trung voi mot trong cac gia tri da thu** (khong bi hong thanh mot gia tri khac/rac), du co the mot so thread bi loi do xung dot.

| Kich ban | Loai kiem tra | So thread | Thanh cong | Loi | Nhat quan |
|---|---|---|---|---|---|
| A | KHONG_XUNG_DOT | 2 | 2 | 0 | true |
| A | XUNG_DOT_CUNG_CANH | 2 | 1 | 1 | true |
| A | KHONG_XUNG_DOT | 5 | 5 | 0 | true |
| A | XUNG_DOT_CUNG_CANH | 5 | 2 | 3 | true |
| A | KHONG_XUNG_DOT | 10 | 10 | 0 | true |
| A | XUNG_DOT_CUNG_CANH | 10 | 2 | 8 | true |
| B | KHONG_XUNG_DOT | 2 | 2 | 0 | true |
| B | XUNG_DOT_CUNG_CANH | 2 | 1 | 1 | true |
| B | KHONG_XUNG_DOT | 5 | 5 | 0 | true |
| B | XUNG_DOT_CUNG_CANH | 5 | 2 | 3 | true |
| B | KHONG_XUNG_DOT | 10 | 10 | 0 | true |
| B | XUNG_DOT_CUNG_CANH | 10 | 3 | 7 | true |
| C | KHONG_XUNG_DOT | 2 | 2 | 0 | true |
| C | XUNG_DOT_CUNG_CANH | 2 | 1 | 1 | true |
| C | KHONG_XUNG_DOT | 5 | 5 | 0 | true |
| C | XUNG_DOT_CUNG_CANH | 5 | 2 | 3 | true |
| C | KHONG_XUNG_DOT | 10 | 10 | 0 | true |
| C | XUNG_DOT_CUNG_CANH | 10 | 2 | 8 | true |
| D | KHONG_XUNG_DOT | 2 | 2 | 0 | true |
| D | XUNG_DOT_CUNG_CANH | 2 | 1 | 1 | true |
| D | KHONG_XUNG_DOT | 5 | 5 | 0 | true |
| D | XUNG_DOT_CUNG_CANH | 5 | 2 | 3 | true |
| D | KHONG_XUNG_DOT | 10 | 10 | 0 | true |
| D | XUNG_DOT_CUNG_CANH | 10 | 3 | 7 | true |
