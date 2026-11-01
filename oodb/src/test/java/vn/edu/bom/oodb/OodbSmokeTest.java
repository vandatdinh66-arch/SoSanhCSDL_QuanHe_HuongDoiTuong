package vn.edu.bom.oodb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.bom.oodb.importer.CsvDataImporter;
import vn.edu.bom.oodb.importer.ResultVerifier;
import vn.edu.bom.oodb.model.TrangThaiPhienBan;
import vn.edu.bom.oodb.repository.BomRepository;
import vn.edu.bom.oodb.repository.PhienBanRepository;
import vn.edu.bom.oodb.util.Jpa;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OodbSmokeTest {

    @TempDir
    Path temp;

    @Test
    void importVerifyAndWriteScenarioA() throws Exception {
        Path scenario = Path.of("..", "data", "scenarios", "A").toAbsolutePath().normalize();
        assertTrue(Files.exists(scenario.resolve("THANH_PHAN.csv")));

        String dbUrl = "objectdb:" + temp.resolve("smoke.odb");
        try (Jpa jpa = new Jpa(dbUrl)) {
            CsvDataImporter.ImportReport imported = new CsvDataImporter(jpa)
                    .importScenario(scenario, "SP_LAPTOP", "Laptop LT-100", true);
            assertEquals(2282, imported.soQuanHe());

            ResultVerifier.VerificationReport verified = new ResultVerifier(jpa)
                    .verify(scenario, temp.resolve("verify.txt"));
            assertEquals(0, verified.failures(), () -> String.join("\n", verified.lines()));

            BomRepository bom = new BomRepository(jpa);
            assertEquals("PB_demo", bom.saoChepPhienBan("PB_v2.0", "PB_demo", "Ban sao"));
            assertThrows(IllegalStateException.class,
                    () -> bom.themCumCon("PB_demo", "TP_000002", "TP_000001", 1));

            bom.phatHanhPhienBan("PB_demo");
            PhienBanRepository phienBanRepository = new PhienBanRepository(jpa);
            assertEquals(TrangThaiPhienBan.PHAT_HANH,
                    phienBanRepository.findByMa("PB_demo").orElseThrow().getTrangThai());
        }
    }
}
