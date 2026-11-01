package vn.edu.bom.oodb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.bom.oodb.experiment.TransactionExperimentRunner;
import vn.edu.bom.oodb.experiment.TransactionExperimentRunner.ConcurrencyResult;
import vn.edu.bom.oodb.experiment.TransactionExperimentRunner.OperationResult;
import vn.edu.bom.oodb.importer.CsvDataImporter;
import vn.edu.bom.oodb.util.Jpa;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiem tra {@link TransactionExperimentRunner} - cong cu sinh "bo so lieu thuc nghiem
 * thao tac cap nhat/giao dich" (TV3 - Tuan 3) - tren du lieu thuc cua kich ban A.
 *
 * <p>Tieu chi quan trong nhat: KHONG co thao tac nao roi vao "LOI_KHONG_MONG_DOI"
 * (nghia la logic ngan chan BR-10/BR-14/BR-09 hoat dong dung nhu ky vong tren du
 * lieu 1000 ban ghi thuc, khong chi tren fixture tu dung nho), va cac phep thu dong
 * thoi phai luon "nhat quan" (khong lam hong du lieu du co xung dot ghi).</p>
 */
class TransactionExperimentRunnerTest {

    @TempDir
    Path temp;

    private static final Path SCENARIO_A =
            Path.of("..", "data", "scenarios", "A").toAbsolutePath().normalize();

    @Test
    void chuoiThaoTac_khongCoLoiKhongMongDoi() throws Exception {
        TransactionExperimentRunner runner = new TransactionExperimentRunner();
        String dbUrl = "objectdb:" + temp.resolve("tx-exp-chain.odb");
        try (Jpa jpa = new Jpa(dbUrl)) {
            new CsvDataImporter(jpa).importScenario(SCENARIO_A, "SP_LAPTOP", "Laptop LT-100", true);
            runner.chayChuoiThaoTac(jpa, "A", "PB_v1.0");
        }

        List<OperationResult> ops = runner.ketQuaThaoTac();
        assertFalse(ops.isEmpty());

        List<OperationResult> loiKhongMongDoi = ops.stream()
                .filter(o -> o.ketQua().equals("LOI_KHONG_MONG_DOI"))
                .toList();
        assertTrue(loiKhongMongDoi.isEmpty(),
                () -> "Co thao tac loi khong mong doi (co the la bug thuc su): " + loiKhongMongDoi);

        // Phai bao phu ca duong thanh cong va duong bi chan dung tren du lieu that.
        assertTrue(ops.stream().anyMatch(o -> o.ketQua().equals("OK")),
                "Khong co thao tac nao thanh cong");
        assertTrue(ops.stream().anyMatch(o -> o.ketQua().equals("OK_CHAN_DUNG")),
                "Khong co thao tac nao bi chan dung (BR-10/BR-14) - bai kiem tra khong co y nghia");
    }

    @Test
    void thucNghiemDongThoi_luonNhatQuanDuKetQuaXungDotHayKhong() throws Exception {
        TransactionExperimentRunner runner = new TransactionExperimentRunner();
        String dbUrl = "objectdb:" + temp.resolve("tx-exp-concurrency.odb");
        try (Jpa jpa = new Jpa(dbUrl)) {
            new CsvDataImporter(jpa).importScenario(SCENARIO_A, "SP_LAPTOP", "Laptop LT-100", true);
            runner.chayThucNghiemDongThoi(jpa, "A", "PB_v1.0", new int[]{2, 5});
        }

        List<ConcurrencyResult> ketQua = runner.ketQuaDongThoi();
        assertFalse(ketQua.isEmpty());
        for (ConcurrencyResult r : ketQua) {
            assertTrue(r.nhatQuan(), () -> "Phat hien du lieu khong nhat quan sau thu nghiem dong thoi: " + r);
        }

        assertTrue(ketQua.stream().anyMatch(r ->
                        r.loaiKiemTra().equals("KHONG_XUNG_DOT") && r.soThanhCong() == r.soThread()),
                "Truong hop khong xung dot phai luon 100% thanh cong");
        assertTrue(ketQua.stream().anyMatch(r -> r.loaiKiemTra().equals("XUNG_DOT_CUNG_CANH")),
                "Thieu phep thu xung dot tren cung mot canh");
    }

    @Test
    void xuatFile_sinhDungCauTrucCsvVaMarkdown() throws Exception {
        TransactionExperimentRunner runner = new TransactionExperimentRunner();
        String dbUrl = "objectdb:" + temp.resolve("tx-exp-export.odb");
        try (Jpa jpa = new Jpa(dbUrl)) {
            new CsvDataImporter(jpa).importScenario(SCENARIO_A, "SP_LAPTOP", "Laptop LT-100", true);
            runner.chayChuoiThaoTac(jpa, "A", "PB_v1.0");
            runner.chayThucNghiemDongThoi(jpa, "A", "PB_v1.0", new int[]{2});
        }

        Path fileThaoTac = temp.resolve("out/tv3-transaction-log.csv");
        Path fileDongThoi = temp.resolve("out/tv3-concurrency-log.csv");
        Path fileTomTat = temp.resolve("out/tv3-transaction-summary.md");
        runner.xuatThaoTacCsv(fileThaoTac);
        runner.xuatDongThoiCsv(fileDongThoi);
        runner.xuatTomTatMarkdown(fileTomTat, List.of("A"));

        assertTrue(Files.exists(fileThaoTac));
        assertTrue(Files.exists(fileDongThoi));
        assertTrue(Files.exists(fileTomTat));

        List<String> dongThaoTac = Files.readAllLines(fileThaoTac);
        assertTrue(dongThaoTac.size() > 1, "CSV thao tac phai co header + it nhat 1 dong du lieu");
        assertTrue(dongThaoTac.get(0).startsWith("kich_ban,phien_ban,ma_thao_tac"));

        List<String> dongDongThoi = Files.readAllLines(fileDongThoi);
        assertTrue(dongDongThoi.size() > 1, "CSV dong thoi phai co header + it nhat 1 dong du lieu");
        assertTrue(dongDongThoi.get(0).startsWith("kich_ban,loai_kiem_tra,so_thread"));
    }
}
