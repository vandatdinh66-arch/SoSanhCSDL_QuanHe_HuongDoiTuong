package vn.edu.bom.oodb.dto;

import java.util.List;

/**
 * Ket qua where-used (N5) trong mot phien ban.
 *
 * @param linhKienMa   thanh phan dang xet
 * @param cacCumBiAnhHuong cac cum/san pham nam tren duong di nguoc len goc
 * @param cacPhienBan  cac phien ban co chua thanh phan nay (khi tim toan CSDL)
 */
public record WhereUsedResult(
        String linhKienMa,
        List<String> cacCumBiAnhHuong,
        List<String> cacPhienBan) {
}
