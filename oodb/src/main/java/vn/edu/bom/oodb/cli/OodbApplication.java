package vn.edu.bom.oodb.cli;

import vn.edu.bom.oodb.dto.BomLine;
import vn.edu.bom.oodb.dto.BomSummary;
import vn.edu.bom.oodb.dto.SharedComponent;
import vn.edu.bom.oodb.dto.VersionDiff;
import vn.edu.bom.oodb.dto.WhereUsedResult;
import vn.edu.bom.oodb.importer.CsvDataImporter;
import vn.edu.bom.oodb.importer.ResultVerifier;
import vn.edu.bom.oodb.model.PhienBan;
import vn.edu.bom.oodb.repository.BomRepository;
import vn.edu.bom.oodb.repository.CauThanhRepository;
import vn.edu.bom.oodb.repository.PhienBanRepository;
import vn.edu.bom.oodb.repository.SanPhamRepository;
import vn.edu.bom.oodb.repository.ThanhPhanRepository;
import vn.edu.bom.oodb.util.Jpa;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * CLI de nap du lieu, chay demo truy van, doi chieu va benchmark nhanh.
 */
public final class OodbApplication {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
            return;
        }
        String command = args[0];
        Map<String, String> opt = parseOptions(args, 1);
        List<String> positional = positional(args, 1);

        switch (command) {
            case "import" -> runImport(positional, opt);
            case "verify" -> runVerify(positional, opt);
            case "demo" -> runDemo(positional, opt);
            case "write-demo" -> runWriteDemo(positional, opt);
            case "bench" -> runBench(positional, opt);
            default -> {
                System.err.println("Lenh khong hop le: " + command);
                usage();
                System.exit(2);
            }
        }
    }

    private static void runImport(List<String> positional, Map<String, String> opt) throws Exception {
        if (positional.size() < 2) {
            throw new IllegalArgumentException("Can: import <scenarioDir> <dbPath>");
        }
        Path scenarioDir = Path.of(positional.get(0));
        String dbUrl = normalizeDbUrl(positional.get(1));
        if (!opt.containsKey("no-reset")) {
            Jpa.deleteDatabaseFiles(dbUrl);
        }

        String sanPhamMa = opt.getOrDefault("product", "SP_LAPTOP");
        String sanPhamTen = opt.getOrDefault("name", "Laptop LT-100");
        boolean draftAll = opt.containsKey("draft-all");

        try (Jpa jpa = new Jpa(dbUrl)) {
            CsvDataImporter.ImportReport report = new CsvDataImporter(jpa)
                    .importScenario(scenarioDir, sanPhamMa, sanPhamTen, !draftAll);
            System.out.println("[IMPORT] " + report);
            System.out.println("[IMPORT] DB = " + dbUrl);
        }
    }

    private static void runVerify(List<String> positional, Map<String, String> opt) throws Exception {
        if (positional.size() < 2) {
            throw new IllegalArgumentException("Can: verify <scenarioDir> <dbPath>");
        }
        Path scenarioDir = Path.of(positional.get(0));
        String dbUrl = normalizeDbUrl(positional.get(1));
        Path reportPath = opt.containsKey("report")
                ? Path.of(opt.get("report"))
                : Path.of("target", "oodb-verify-" + scenarioDir.getFileName() + ".txt");

        try (Jpa jpa = new Jpa(dbUrl)) {
            ResultVerifier.VerificationReport report = new ResultVerifier(jpa)
                    .verify(scenarioDir, reportPath);
            System.out.println();
            System.out.println("[VERIFY] checks=" + report.checks()
                    + ", failures=" + report.failures()
                    + ", report=" + report.reportPath());
            if (report.failures() > 0) {
                System.exit(1);
            }
        }
    }

    private static void runDemo(List<String> positional, Map<String, String> opt) {
        if (positional.size() < 1) {
            throw new IllegalArgumentException("Can: demo <dbPath> [--version PB_v1.0]");
        }
        String dbUrl = normalizeDbUrl(positional.get(0));
        try (Jpa jpa = new Jpa(dbUrl)) {
            PhienBanRepository phienBanRepo = new PhienBanRepository(jpa);
            ThanhPhanRepository thanhPhanRepo = new ThanhPhanRepository(jpa);
            CauThanhRepository cauThanhRepo = new CauThanhRepository(jpa);
            BomRepository bomRepo = new BomRepository(jpa);
            SanPhamRepository sanPhamRepo = new SanPhamRepository(jpa);

            System.out.println("=== DEMO OBJECTDB ===");
            System.out.println("DB: " + dbUrl);
            System.out.println("San pham: " + sanPhamRepo.findAll());
            System.out.println("So thanh phan: " + thanhPhanRepo.count()
                    + " (cum=" + thanhPhanRepo.countCumLapRap()
                    + ", co khi=" + thanhPhanRepo.countLinhKienCoKhi()
                    + ", dien tu=" + thanhPhanRepo.countLinhKienDienTu() + ")");
            System.out.println("So quan he: " + cauThanhRepo.count());

            List<String> versions = opt.containsKey("version")
                    ? List.of(opt.get("version"))
                    : phienBanRepo.findAllMa();
            for (String pbMa : versions) {
                PhienBan pb = phienBanRepo.findByMa(pbMa)
                        .orElseThrow(() -> new IllegalArgumentException("Khong co phien ban " + pbMa));
                System.out.println();
                System.out.println("---- " + pbMa + " | " + pb.getTen()
                        + " | trang thai=" + pb.getTrangThai() + " ----");

                BomSummary summary = bomRepo.tinhTongHop(pbMa);
                System.out.printf(Locale.ROOT,
                        "N2/N3/N4/N7: nut=%d, canh=%d, do sau=%d, linh kien=%d, tong chi phi=%s%n",
                        summary.soNut(), summary.soCanh(), summary.doSauLonNhat(),
                        summary.soLuongTheoLinhKien().size(),
                        summary.tongChiPhi().toPlainString());

                List<Map.Entry<String, Long>> top = summary.soLuongTheoLinhKien().entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .limit(5)
                        .toList();
                System.out.println("Top 5 linh kien theo tong luong: " + top);

                if (summary.soCanh() <= 5_000) {
                    BomSummary noBom = bomRepo.noBom(pbMa);
                    System.out.println("N2: " + noBom.cacDong().size() + " dong no BOM, in 12 dong dau:");
                    int i = 0;
                    for (BomLine line : noBom.cacDong()) {
                        if (i++ >= 12) {
                            break;
                        }
                        System.out.printf(Locale.ROOT, "  cap %d | %-10s | SL can=%d | tich luy=%d | %s%n",
                                line.cap(), line.ma(), line.soLuong(),
                                line.soLuongTichLuy(), line.duongDan());
                    }
                } else {
                    System.out.println("N2: bo qua no BOM chi tiet vi qua nhieu canh; dung tinhTongHop.");
                }

                List<String> conTrucTiep = cauThanhRepo.findDirectChildren(pbMa, pb.getGoc().getMa())
                        .stream().map(q -> q.getCon().getMa()).toList();
                System.out.println("N1: goc " + pb.getGoc().getMa() + " co " + conTrucTiep.size()
                        + " con truc tiep: " + conTrucTiep.stream().limit(10).toList());

                List<SharedComponent> shared = thanhPhanRepo.linhKienDungChungNhieuNhat(pbMa, 5);
                System.out.println("N6: linh kien dung chung nhieu nhat: " + shared);
                if (!shared.isEmpty()) {
                    WhereUsedResult used = bomRepo.whereUsed(pbMa, shared.get(0).ma());
                    System.out.println("N5: where-used " + used.linhKienMa() + " -> "
                            + used.cacCumBiAnhHuong().size() + " cum, versions=" + used.cacPhienBan());
                }

                System.out.println("N9: co khi 'Nhôm Alloy' = "
                        + thanhPhanRepo.locLinhKienCoKhiTheoVatLieu("Nhôm Alloy").size()
                        + ", dien tu 12V..20V = "
                        + thanhPhanRepo.locLinhKienDienTuTheoDienAp(12, 20).size());
                System.out.println("N10: so cum khong co con = "
                        + thanhPhanRepo.cumKhongCoCon(pbMa).size());
            }

            List<String> all = phienBanRepo.findAllMa();
            if (all.size() >= 2) {
                VersionDiff diff = bomRepo.soSanhPhienBan(all.get(0), all.get(all.size() - 1));
                System.out.println();
                System.out.println("N8: so sanh " + all.get(0) + " -> " + all.get(all.size() - 1)
                        + ": them=" + diff.themMoi().size()
                        + ", xoa=" + diff.biXoa().size()
                        + ", doi so luong=" + diff.doiSoLuong().size());
            }
        }
    }

    private static void runWriteDemo(List<String> positional, Map<String, String> opt) {
        if (positional.size() < 1) {
            throw new IllegalArgumentException("Can: write-demo <dbPath> [--source PB_v2.0]");
        }
        String dbUrl = normalizeDbUrl(positional.get(0));
        try (Jpa jpa = new Jpa(dbUrl)) {
            PhienBanRepository phienBanRepo = new PhienBanRepository(jpa);
            ThanhPhanRepository thanhPhanRepo = new ThanhPhanRepository(jpa);
            CauThanhRepository cauThanhRepo = new CauThanhRepository(jpa);
            BomRepository bomRepo = new BomRepository(jpa);

            String source = opt.getOrDefault("source", "PB_v2.0");
            String target = opt.getOrDefault("target", "PB_demo");
            if (phienBanRepo.findByMa(target).isPresent()) {
                System.out.println("[WRITE-DEMO] " + target + " da ton tai, bo qua buoc sao chep");
            } else {
                String created = bomRepo.saoChepPhienBan(source, target, "Ban sao demo tu " + source);
                System.out.println("[WRITE-DEMO] N14: da sao chep " + source + " -> " + created);
            }

            List<vn.edu.bom.oodb.model.CauThanh> canh = cauThanhRepo.findByVersion(target);
            if (canh.isEmpty()) {
                throw new IllegalStateException("Phien ban " + target + " khong co canh");
            }
            vn.edu.bom.oodb.model.CauThanh mau = canh.get(0);
            int slCu = mau.getSoLuong();
            bomRepo.suaSoLuong(target, mau.getCha().getMa(), mau.getCon().getMa(), slCu + 1);
            System.out.println("[WRITE-DEMO] N12: sua so luong " + mau.getCha().getMa()
                    + " -> " + mau.getCon().getMa() + " tu " + slCu + " thanh " + (slCu + 1));

            vn.edu.bom.oodb.model.CauThanh coCumCon = canh.stream()
                    .filter(q -> q.getCon() instanceof vn.edu.bom.oodb.model.CumLapRap)
                    .findFirst()
                    .orElse(null);
            if (coCumCon != null) {
                try {
                    bomRepo.themCumCon(target,
                            coCumCon.getCon().getMa(), coCumCon.getCha().getMa(), 1);
                    System.out.println("[WRITE-DEMO] N11: KHONG phat hien chu trinh (can kiem tra lai)");
                } catch (IllegalStateException e) {
                    System.out.println("[WRITE-DEMO] N11: da chan chu trinh dung: " + e.getMessage());
                }
            }

            List<String> linhKien = thanhPhanRepo.findAll().stream()
                    .filter(t -> t instanceof vn.edu.bom.oodb.model.LinhKien)
                    .map(vn.edu.bom.oodb.model.ThanhPhan::getMa)
                    .limit(2)
                    .toList();
            if (linhKien.size() == 2) {
                try {
                    int so = bomRepo.thayTheLinhKien(target, linhKien.get(0), linhKien.get(1));
                    System.out.println("[WRITE-DEMO] N13: thay " + linhKien.get(0)
                            + " bang " + linhKien.get(1) + " -> " + so + " quan he");
                } catch (RuntimeException e) {
                    System.out.println("[WRITE-DEMO] N13: khong thay the duoc: " + e.getMessage());
                }
            }
            System.out.println("[WRITE-DEMO] Hoan tat. Phien ban " + target
                    + " van o trang thai NHAP de tiep tuc thu nghiem.");
        }
    }

    private static void runBench(List<String> positional, Map<String, String> opt) {
        if (positional.size() < 1) {
            throw new IllegalArgumentException("Can: bench <dbPath> [--version PB_v1.0] [--runs 5]");
        }
        String dbUrl = normalizeDbUrl(positional.get(0));
        int runs = Integer.parseInt(opt.getOrDefault("runs", "5"));
        try (Jpa jpa = new Jpa(dbUrl)) {
            PhienBanRepository phienBanRepo = new PhienBanRepository(jpa);
            ThanhPhanRepository thanhPhanRepo = new ThanhPhanRepository(jpa);
            CauThanhRepository cauThanhRepo = new CauThanhRepository(jpa);
            BomRepository bomRepo = new BomRepository(jpa);

            String pb = opt.getOrDefault("version", phienBanRepo.findAllMa().get(0));
            PhienBan phienBan = phienBanRepo.findByMa(pb)
                    .orElseThrow(() -> new IllegalArgumentException("Khong co phien ban " + pb));
            String goc = phienBan.getGoc().getMa();
            List<SharedComponent> shared = thanhPhanRepo.linhKienDungChungNhieuNhat(pb, 1);
            String linhKien = shared.isEmpty() ? null : shared.get(0).ma();

            System.out.println("=== BENCH OBJECTDB: " + dbUrl + " | " + pb + " | runs=" + runs + " ===");
            doBench("N1 con truc tiep", runs, () -> cauThanhRepo.findDirectChildren(pb, goc));
            doBench("N2/N3/N4 tong hop BOM", runs, () -> bomRepo.tinhTongHop(pb));
            doBench("N2 no BOM chi tiet", runs, () -> bomRepo.noBom(pb));
            if (linhKien != null) {
                doBench("N5 where-used", runs, () -> bomRepo.whereUsed(pb, linhKien));
            }
            doBench("N6 dung chung nhieu nhat", runs,
                    () -> thanhPhanRepo.linhKienDungChungNhieuNhat(pb, 5));
            doBench("N10 cum khong co con", runs, () -> thanhPhanRepo.cumKhongCoCon(pb));
        }
    }

    private static void doBench(String ten, int runs, Runnable action) {
        long best = Long.MAX_VALUE;
        long total = 0;
        for (int i = 0; i < runs; i++) {
            long start = System.nanoTime();
            action.run();
            long elapsed = System.nanoTime() - start;
            best = Math.min(best, elapsed);
            total += elapsed;
        }
        System.out.printf(Locale.ROOT, "%-30s min=%8.2f ms  avg=%8.2f ms%n",
                ten, best / 1_000_000.0, total / 1_000_000.0 / runs);
    }

    private static String normalizeDbUrl(String path) {
        return path.startsWith("objectdb:") ? path : "objectdb:" + path;
    }

    private static Map<String, String> parseOptions(String[] args, int from) {
        Map<String, String> result = new HashMap<>();
        for (int i = from; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--")) {
                String key = arg.substring(2);
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    result.put(key, args[++i]);
                } else {
                    result.put(key, "true");
                }
            }
        }
        return result;
    }

    private static List<String> positional(String[] args, int from) {
        List<String> result = new ArrayList<>();
        for (int i = from; i < args.length; i++) {
            if (!args[i].startsWith("--")) {
                result.add(args[i]);
            } else if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                i++;
            }
        }
        return result;
    }

    private static void usage() {
        System.out.println("""
                ObjectDB DAO/Repository - BOM Laptop
                Cach dung:
                  import     <scenarioDir> <dbPath> [--product MA] [--name TEN] [--draft-all] [--no-reset]
                  verify     <scenarioDir> <dbPath> [--report FILE]
                  demo       <dbPath> [--version PB_v1.0]
                  write-demo <dbPath> [--source PB_v2.0] [--target PB_demo]
                  bench      <dbPath> [--version PB_v1.0] [--runs 5]

                Vi du:
                  java ... OodbApplication import data/scenarios/A target/oodb-A.odb
                  java ... OodbApplication verify data/scenarios/A target/oodb-A.odb
                  java ... OodbApplication demo target/oodb-A.odb
                """);
    }
}
