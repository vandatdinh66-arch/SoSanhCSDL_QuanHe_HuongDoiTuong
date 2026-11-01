package vn.edu.bom.oodb.dto;

import java.math.BigDecimal;

/**
 * Mot dong trong ket qua no BOM.
 *
 * @param ma              ma thanh phan
 * @param ten             ten thanh phan
 * @param loai            ma loai (LTP01..LTP04)
 * @param cap             cap trong cay (goc = 0)
 * @param soLuong         so luong cua canh cha -> thanh phan nay
 * @param soLuongTichLuy  so luong don vi goc can cho thanh phan nay
 * @param donGia          don gia (chi linh kien moi co)
 * @param thanhTien       soLuongTichLuy * donGia (0 neu la cum)
 * @param duongDan        duong dan tu goc, vi du TP_1/TP_2/TP_9
 */
public record BomLine(
        String ma,
        String ten,
        String loai,
        int cap,
        long soLuong,
        long soLuongTichLuy,
        BigDecimal donGia,
        BigDecimal thanhTien,
        String duongDan) {
}
