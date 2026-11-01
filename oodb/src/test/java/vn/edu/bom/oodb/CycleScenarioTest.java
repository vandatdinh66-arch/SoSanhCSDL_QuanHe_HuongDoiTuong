package vn.edu.bom.oodb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.bom.oodb.importer.CsvDataImporter;
import vn.edu.bom.oodb.repository.BomRepository;
import vn.edu.bom.oodb.util.Jpa;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kich ban "cycle" do TV5 sinh rieng de kiem chung BR-10 (ngan chu trinh): PB_v1.0
 * bi chen mot canh nguoc tao chu trinh thuc su (1143 canh, khong sap xep topo duoc -
 * xem data/scenarios/cycle/check_report.txt), con PB_v2.0 sach.
 *
 * <p>Truoc ban sua loi nay, kich ban "cycle" ton tai trong repo tu TV5 nhung khong
 * duoc tham chieu boi bat ky test nao trong module oodb - nen chua co gi chung minh
 * tang doi tuong/ObjectDB thuc su phat hien duoc chu trinh tren du lieu that (khac voi
 * cac test don vi tu dung du lieu chu trinh tu viet tay).</p>
 */
class CycleScenarioTest {

    @TempDir
    Path temp;

    @Test
    void phienBanCoChuTrinh_biPhatHienKhiTinhTongHop() throws Exception {
        Path scenario = Path.of("..", "data", "scenarios", "cycle").toAbsolutePath().normalize();
        assertTrue(Files.exists(scenario.resolve("THANH_PHAN.csv")),
                "Khong tim thay kich ban cycle - TV5 co the da doi vi tri du lieu");

        String dbUrl = "objectdb:" + temp.resolve("cycle.odb");
        try (Jpa jpa = new Jpa(dbUrl)) {
            new CsvDataImporter(jpa).importScenario(scenario, "SP_LAPTOP", "Laptop LT-100", true);

            BomRepository bom = new BomRepository(jpa);

            // PB_v1.0 chua chu trinh duoc chen co tinh -> ca hai duong tinh (duyet toan
            // bo duong di va quy hoach dong co memo) deu phai bat duoc va bao loi ro rang,
            // khong duoc treo may (StackOverflow/vong lap vo han) hay tra ve ket qua sai.
            IllegalStateException exTongHop = assertThrows(IllegalStateException.class,
                    () -> bom.tinhTongHop("PB_v1.0"));
            assertTrue(exTongHop.getMessage().toLowerCase().contains("chu trinh"));

            IllegalStateException exNoBom = assertThrows(IllegalStateException.class,
                    () -> bom.noBom("PB_v1.0"));
            assertTrue(exNoBom.getMessage().toLowerCase().contains("chu trinh"));
        }
    }

    @Test
    void phienBanSach_khongBiAnhHuongBoiChuTrinhOPhienBanKhac() throws Exception {
        Path scenario = Path.of("..", "data", "scenarios", "cycle").toAbsolutePath().normalize();
        String dbUrl = "objectdb:" + temp.resolve("cycle-clean.odb");
        try (Jpa jpa = new Jpa(dbUrl)) {
            new CsvDataImporter(jpa).importScenario(scenario, "SP_LAPTOP", "Laptop LT-100", true);

            BomRepository bom = new BomRepository(jpa);

            // Chu trinh chi duoc chen vao PB_v1.0; PB_v2.0 phai tinh binh thuong vi
            // moi phien ban co tap canh (CauThanh) rieng.
            var tongHop = bom.tinhTongHop("PB_v2.0");
            assertEquals(1000, tongHop.soNut());
            assertEquals(1141, tongHop.soCanh());
        }
    }
}
