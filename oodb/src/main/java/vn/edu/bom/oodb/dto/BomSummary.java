package vn.edu.bom.oodb.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Ket qua tong hop cua mot lan no BOM.
 *
 * @param phienBanMa        phien ban duoc phan tich
 * @param gocMa             ma cum goc
 * @param cacDong           toan bo dong BOM theo thu tu duyet truoc
 * @param soLuongTheoLinhKien tong luong tung linh kien la (BR-16)
 * @param tongChiPhi        tong chi phi cau thanh (BR-17)
 * @param doSauLonNhat      do sau lon nhat tinh tu goc
 * @param soNut             so thanh phan khac nhau di den duoc tu goc
 * @param soCanh            so quan he cau thanh da duyet
 */
public record BomSummary(
        String phienBanMa,
        String gocMa,
        List<BomLine> cacDong,
        Map<String, Long> soLuongTheoLinhKien,
        BigDecimal tongChiPhi,
        int doSauLonNhat,
        int soNut,
        int soCanh) {
}
