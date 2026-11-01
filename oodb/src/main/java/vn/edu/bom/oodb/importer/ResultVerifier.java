package vn.edu.bom.oodb.importer;

import vn.edu.bom.oodb.dto.BomSummary;
import vn.edu.bom.oodb.dto.SharedComponent;
import vn.edu.bom.oodb.dto.VersionDiff;
import vn.edu.bom.oodb.dto.WhereUsedResult;
import vn.edu.bom.oodb.repository.BomRepository;
import vn.edu.bom.oodb.repository.CauThanhRepository;
import vn.edu.bom.oodb.repository.ThanhPhanRepository;
import vn.edu.bom.oodb.util.Jpa;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Doi chieu ket qua repository ObjectDB voi ket qua tinh truc tiep tu CSV.
 * Day la phan minh chung cho viec cung mot dau vao phai cho cung ket qua.
 */
public class ResultVerifier {

    private final Jpa jpa;
    private final BomRepository bomRepository;
    private final ThanhPhanRepository thanhPhanRepository;
    private final CauThanhRepository cauThanhRepository;

    private final List<String> lines = new ArrayList<>();
    private int checks = 0;
    private int failures = 0;

    public ResultVerifier(Jpa jpa) {
        this.jpa = jpa;
        this.bomRepository = new BomRepository(jpa);
        this.thanhPhanRepository = new ThanhPhanRepository(jpa);
        this.cauThanhRepository = new CauThanhRepository(jpa);
    }

    public VerificationReport verify(Path scenarioDir, Path reportPath) throws IOException {
        CsvOracle oracle = new CsvOracle(scenarioDir);
        line("Doi chieu ObjectDB voi CSV: " + scenarioDir);
        line("");

        kiemTraSoLuong(oracle);
        for (String pb : oracle.cacPhienBan()) {
            kiemTraPhienBan(oracle, pb);
        }
        if (oracle.cacPhienBan().size() >= 2) {
            kiemTraSoSanhPhienBan(oracle);
        }

        line("");
        line(failures == 0
                ? "KET LUAN: DAT - " + checks + " phep kiem tra, 0 loi"
                : "KET LUAN: KHONG DAT - " + checks + " phep kiem tra, " + failures + " loi");

        Files.createDirectories(reportPath.toAbsolutePath().getParent());
        Files.writeString(reportPath, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
        return new VerificationReport(checks, failures, List.copyOf(lines), reportPath.toString());
    }

    private void kiemTraSoLuong(CsvOracle oracle) {
        check("So thanh phan", oracle.soThanhPhan(), thanhPhanRepository.count());
        check("So cum lap rap", oracle.soCumLapRap(), thanhPhanRepository.countCumLapRap());
        check("So linh kien co khi", oracle.soLinhKienCoKhi(), thanhPhanRepository.countLinhKienCoKhi());
        check("So linh kien dien tu", oracle.soLinhKienDienTu(), thanhPhanRepository.countLinhKienDienTu());
        long soQuanHe = oracle.cacPhienBan().stream()
                .mapToLong(cauThanhRepository::countByVersion)
                .sum();
        check("So quan he cau thanh", oracle.soQuanHe(), soQuanHe);
    }

    private void kiemTraPhienBan(CsvOracle oracle, String pb) {
        line("---- Phien ban " + pb + " ----");
        Map<String, Long> kyVong = oracle.tongLuongTheoLa(pb);
        BomSummary thucTe = bomRepository.tinhTongHop(pb);

        check("So nut di den duoc", oracle.soNut(pb), thucTe.soNut());
        check("So canh di den duoc", oracle.soCanh(pb), thucTe.soCanh());
        check("Do sau lon nhat", oracle.doSau(pb), thucTe.doSauLonNhat());

        BigDecimal kyVongChiPhi = oracle.tongChiPhi(pb);
        if (kyVongChiPhi.compareTo(thucTe.tongChiPhi()) == 0) {
            pass("Tong chi phi = " + thucTe.tongChiPhi().toPlainString());
        } else {
            fail("Tong chi phi: ky vong " + kyVongChiPhi.toPlainString()
                    + ", thuc te " + thucTe.tongChiPhi().toPlainString());
        }

        Map<String, Long> thucTeLuong = thucTe.soLuongTheoLinhKien();
        check("So linh kien la", kyVong.size(), thucTeLuong.size());
        int saiLuong = 0;
        for (Map.Entry<String, Long> e : kyVong.entrySet()) {
            Long actual = thucTeLuong.get(e.getKey());
            if (actual == null || !actual.equals(e.getValue())) {
                saiLuong++;
                if (saiLuong <= 5) {
                    fail("Tong luong " + e.getKey() + ": ky vong " + e.getValue() + ", thuc te " + actual);
                }
            }
        }
        if (saiLuong == 0) {
            pass("Tong luong tat ca " + kyVong.size() + " linh kien khop");
        } else {
            fail("Co " + saiLuong + " linh kien lech tong luong");
        }

        String goc = oracle.goc(pb);
        List<String> kyVongCon = oracle.conTrucTiep(pb, goc);
        List<String> thucTeCon = cauThanhRepository.findDirectChildren(pb, goc).stream()
                .map(q -> q.getCon().getMa())
                .sorted()
                .toList();
        if (kyVongCon.equals(thucTeCon)) {
            pass("N1: " + kyVongCon.size() + " con truc tiep cua goc khop");
        } else {
            fail("N1: con truc tiep cua goc khac nhau (ky vong " + kyVongCon.size()
                    + ", thuc te " + thucTeCon.size() + ")");
        }

        kiemTraWhereUsed(oracle, pb);
        kiemTraDungChung(oracle, pb);
        kiemTraCumRong(oracle, pb);
    }

    private void kiemTraCumRong(CsvOracle oracle, String pb) {
        List<String> kyVong = oracle.cumKhongCoCon(pb).stream().sorted().toList();
        List<String> thucTe = thanhPhanRepository.cumKhongCoCon(pb).stream()
                .map(c -> c.getMa())
                .sorted()
                .toList();
        if (kyVong.equals(thucTe)) {
            pass("N10: " + kyVong.size() + " cum khong co con khop");
        } else {
            fail("N10: cum khong co con khac nhau (ky vong " + kyVong.size()
                    + ", thuc te " + thucTe.size() + ")");
        }
    }

    private void kiemTraWhereUsed(CsvOracle oracle, String pb) {
        Map<String, Integer> soCum = oracle.soCumDungMoiLinhKien(pb);
        String mau = soCum.entrySet().stream()
                .max(Map.Entry.<String, Integer>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                .map(Map.Entry::getKey)
                .orElse(null);
        if (mau == null) {
            return;
        }
        Set<String> kyVong = oracle.whereUsed(pb, mau);
        WhereUsedResult thucTe = bomRepository.whereUsed(pb, mau);
        if (kyVong.equals(Set.copyOf(thucTe.cacCumBiAnhHuong()))) {
            pass("N5: where-used cua " + mau + " khop (" + kyVong.size() + " cum)");
        } else {
            fail("N5: where-used cua " + mau + " khac nhau (ky vong " + kyVong.size()
                    + ", thuc te " + thucTe.cacCumBiAnhHuong().size() + ")");
        }
    }

    private void kiemTraDungChung(CsvOracle oracle, String pb) {
        Map<String, Integer> kyVong = oracle.soCumDungMoiLinhKien(pb);
        String maKyVong = kyVong.entrySet().stream()
                .max(Map.Entry.<String, Integer>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                .map(Map.Entry::getKey)
                .orElse(null);
        List<SharedComponent> thucTe = thanhPhanRepository.linhKienDungChungNhieuNhat(pb, 1);
        if (maKyVong == null || thucTe.isEmpty()) {
            return;
        }
        if (thucTe.get(0).ma().equals(maKyVong)
                && thucTe.get(0).soCumSuDung() == kyVong.get(maKyVong)) {
            pass("N6: linh kien dung chung nhieu nhat = " + maKyVong
                    + " (" + kyVong.get(maKyVong) + " cum)");
        } else {
            fail("N6: ky vong " + maKyVong + " (" + kyVong.get(maKyVong)
                    + " cum), thuc te " + thucTe.get(0));
        }
    }

    private void kiemTraSoSanhPhienBan(CsvOracle oracle) {
        List<String> ds = new ArrayList<>(oracle.cacPhienBan());
        ds.sort(String::compareTo);
        String cu = ds.get(0);
        String moi = ds.get(ds.size() - 1);
        Map<String, Integer> canhCu = oracle.canh(cu);
        Map<String, Integer> canhMoi = oracle.canh(moi);

        int kyVongThem = 0;
        int kyVongXoa = 0;
        int kyVongDoi = 0;
        for (Map.Entry<String, Integer> e : canhMoi.entrySet()) {
            Integer old = canhCu.get(e.getKey());
            if (old == null) {
                kyVongThem++;
            } else if (!old.equals(e.getValue())) {
                kyVongDoi++;
            }
        }
        for (String key : canhCu.keySet()) {
            if (!canhMoi.containsKey(key)) {
                kyVongXoa++;
            }
        }

        VersionDiff diff = bomRepository.soSanhPhienBan(cu, moi);
        check("N8: so canh them moi (" + cu + " -> " + moi + ")", kyVongThem, diff.themMoi().size());
        check("N8: so canh bi xoa", kyVongXoa, diff.biXoa().size());
        check("N8: so canh doi so luong", kyVongDoi, diff.doiSoLuong().size());
    }

    private void check(String ten, long kyVong, long thucTe) {
        checks++;
        if (kyVong == thucTe) {
            pass(ten + " = " + thucTe);
        } else {
            fail(ten + ": ky vong " + kyVong + ", thuc te " + thucTe);
        }
    }

    private void pass(String message) {
        line("[PASS] " + message);
    }

    private void fail(String message) {
        failures++;
        line("[FAIL] " + message);
    }

    private void line(String message) {
        lines.add(message);
        System.out.println(message);
    }

    public record VerificationReport(
            int checks,
            int failures,
            List<String> lines,
            String reportPath) {
    }
}
