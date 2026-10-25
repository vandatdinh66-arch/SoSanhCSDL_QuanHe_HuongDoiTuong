import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Sinh du lieu mau cau truc san pham (BOM) cho do an 5.
 *
 * Cach chay (trong thu muc goc repo):
 *   javac -encoding UTF-8 DataGenerator.java DataChecker.java
 *   java DataGenerator --scenario A
 *   java DataGenerator --scenario C --size 10000
 *   java DataGenerator --scenario A --cycle
 *
 * Tham so (deu tuy chon, ghi de gia tri mac dinh cua kich ban):
 *   --scenario A|B|C|D   kich ban mau (mac dinh A)
 *   --size N             so dong THANH_PHAN (mac dinh 1000)
 *   --depth D            do sau cay (so cap duoi goc)
 *   --branch b           so nhanh cua cac cum (cum cap k co khoang b^k nut)
 *   --share s            ti le linh kien dung chung (0..1) tren tong so nut tru goc
 *   --seed S             hat giong ngau nhien (mac dinh 42)
 *   --cycle              them du lieu co chu trinh co y, xuat ra thu muc "cycle"
 *   --root DIR           thu muc chua cac kich ban (mac dinh data/scenarios)
 *   --out DIR            ghi de hoan toan thu muc xuat
 */
public class DataGenerator {

    static final String V1 = "PB_v1.0";
    static final String V2 = "PB_v2.0";

    public static void main(String[] args) throws IOException {
        Map<String, String> opt = parseArgs(args);

        String scenario = opt.getOrDefault("scenario", "A").toUpperCase();
        int depth, branch;
        double share;
        switch (scenario) {
            case "A": depth = 3; branch = 5; share = 0.10; break; // nong, nhieu nhanh
            case "B": depth = 8; branch = 2; share = 0.10; break; // sau, it nhanh
            case "C": depth = 5; branch = 3; share = 0.50; break; // vua, dung chung nhieu
            case "D": depth = 5; branch = 3; share = 0.00; break; // doi chung, khong dung chung
            default:
                throw new IllegalArgumentException("Kich ban phai la A, B, C hoac D");
        }
        int size = Integer.parseInt(opt.getOrDefault("size", "1000"));
        depth = Integer.parseInt(opt.getOrDefault("depth", String.valueOf(depth)));
        branch = Integer.parseInt(opt.getOrDefault("branch", String.valueOf(branch)));
        share = Double.parseDouble(opt.getOrDefault("share", String.valueOf(share)));
        long seed = Long.parseLong(opt.getOrDefault("seed", "42"));
        boolean cycle = opt.containsKey("cycle");
        String root = opt.getOrDefault("root", "data/scenarios");

        String defaultName = cycle ? "cycle" : (size == 1000 ? scenario : scenario + "_" + size);
        String outDir = opt.getOrDefault("out", root + "/" + defaultName);

        if (depth < 1) throw new IllegalArgumentException("depth phai >= 1");
        if (branch < 1) throw new IllegalArgumentException("branch phai >= 1");
        if (share < 0 || share > 1) throw new IllegalArgumentException("share phai trong [0,1]");
        if (size < depth + 2) throw new IllegalArgumentException("size qua nho so voi depth");

        Random rand = new Random(seed);
        new File(outDir).mkdirs();
        System.out.printf(Locale.ROOT,
                "[*] Kich ban %s | size=%d depth=%d branch=%d share=%.2f seed=%d cycle=%b%n",
                scenario, size, depth, branch, share, seed, cycle);

        // ---------- 1. Kich thuoc tung cap ----------
        // Cap 0 = goc (1 nut). Cac cap 1..depth-1 la cum lap rap, cap depth la linh kien la.
        int[] sizes = new int[depth + 1];
        sizes[0] = 1;
        int budget = Math.max(depth, (int) (size * 0.2)); // ngan sach so cum ~20% tong so nut
        int used = 1;
        for (int k = 1; k < depth; k++) {
            long want = 1;
            for (int j = 0; j < k && want < Integer.MAX_VALUE; j++) want *= branch;
            int allowed = budget - used - (depth - 1 - k);
            int s = (int) Math.max(1, Math.min(want, allowed));
            sizes[k] = Math.max(sizes[k - 1], s); // moi cum cap tren phai co it nhat 1 con
            used += sizes[k];
        }
        sizes[depth] = size - used;
        if (sizes[depth] < sizes[depth - 1]) {
            throw new IllegalArgumentException("size qua nho: khong du linh kien la cho cac cum o cap duoi cung");
        }

        // ---------- 2. Tao nut va ma ----------
        String[][] level = new String[depth + 1][];
        int counter = 1;
        for (int k = 0; k <= depth; k++) {
            level[k] = new String[sizes[k]];
            for (int j = 0; j < sizes[k]; j++) level[k][j] = String.format("TP_%06d", counter++);
        }
        String rootId = level[0][0];

        // ---------- 3. Canh cha-con: cay chinh ----------
        Map<String, List<String>> parentsOf = new LinkedHashMap<>();
        List<String[]> edges = new ArrayList<>(); // {cha, con}
        for (int k = 1; k <= depth; k++) {
            String[] parents = level[k - 1];
            for (int j = 0; j < sizes[k]; j++) {
                // sizes[k] >= sizes[k-1]: j dau tien duyet vong tron de moi cha co it nhat 1 con
                String p = j < parents.length ? parents[j] : parents[rand.nextInt(parents.length)];
                String c = level[k][j];
                edges.add(new String[]{p, c});
                parentsOf.computeIfAbsent(c, x -> new ArrayList<>()).add(p);
            }
        }

        // ---------- 4. Dung chung: them cha cho mot so linh kien la ----------
        List<String> assemblies = new ArrayList<>();
        for (int k = 0; k < depth; k++) assemblies.addAll(Arrays.asList(level[k]));
        int wantShared = (int) Math.round(share * (size - 1));
        int sharedCount = Math.min(wantShared, sizes[depth]);
        if (sharedCount < wantShared) {
            System.out.printf("[!] Chi co %d linh kien la nen khong dat duoc ti le dung chung %.2f%n",
                    sizes[depth], share);
        }
        if (depth < 2 && sharedCount > 0) {
            System.out.println("[!] depth = 1 chi co 1 cum (goc) nen khong the dung chung");
            sharedCount = 0;
        }
        List<String> leaves = new ArrayList<>(Arrays.asList(level[depth]));
        Collections.shuffle(leaves, rand);
        for (int i = 0; i < sharedCount; i++) {
            String leaf = leaves.get(i);
            int extra = rand.nextInt(2) + 1; // them 1 hoac 2 cha
            for (int e = 0; e < extra; e++) {
                String p = assemblies.get(rand.nextInt(assemblies.size()));
                if (parentsOf.get(leaf).contains(p)) continue;
                parentsOf.get(leaf).add(p);
                edges.add(new String[]{p, leaf});
            }
        }
        // do dem thuc te de bao cao (khong trung voi viec bo qua cha da co)
        int realShared = 0;
        for (String leaf : level[depth]) if (parentsOf.get(leaf).size() >= 2) realShared++;

        // ---------- 5. Bang danh muc / thanh phan / bang con ----------
        List<String[]> loai = Arrays.asList(
                new String[]{"LTP01", "Cụm Lắp Ráp Nguyên Chiếc"},
                new String[]{"LTP02", "Cụm Module Tải Nối"},
                new String[]{"LTP03", "Linh Kiện Cơ Khí"},
                new String[]{"LTP04", "Linh Kiện Điện Tử"});
        String[] chatLieuArr = {"Nhôm Alloy", "Thép Không Gỉ", "Nhựa ABS", "Carbon Fiber"};
        String[] donViTinhArr = {"Cái", "Bộ", "Chiếc", "Hộp"};
        double[] dienApArr = {5.0, 12.0, 19.0, 20.0};
        double[] congSuatArr = {15.0, 45.0, 65.0, 100.0};

        List<String[]> thanhPhan = new ArrayList<>();
        List<String[]> cumLapRap = new ArrayList<>();
        List<String[]> coKhi = new ArrayList<>();
        List<String[]> dienTu = new ArrayList<>();
        int idx = 1;
        for (int k = 0; k <= depth; k++) {
            for (String id : level[k]) {
                double donGia = Math.round((10.0 + (2000.0 - 10.0) * rand.nextDouble()) * 100.0) / 100.0;
                String gia = String.format(Locale.ROOT, "%.2f", donGia);
                String dvt = donViTinhArr[rand.nextInt(donViTinhArr.length)];
                String ten = "Thành Phần " + idx++;
                if (k < depth) {
                    String maLoai = (k == 0) ? "LTP01" : "LTP02";
                    thanhPhan.add(new String[]{id, ten, maLoai, gia, dvt});
                    cumLapRap.add(new String[]{id});
                } else if (rand.nextBoolean()) {
                    thanhPhan.add(new String[]{id, ten, "LTP03", gia, dvt});
                    String kt = (rand.nextInt(46) + 5) + "x" + (rand.nextInt(46) + 5) + " mm";
                    coKhi.add(new String[]{id, chatLieuArr[rand.nextInt(chatLieuArr.length)], kt});
                } else {
                    thanhPhan.add(new String[]{id, ten, "LTP04", gia, dvt});
                    dienTu.add(new String[]{id,
                            String.valueOf(dienApArr[rand.nextInt(dienApArr.length)]),
                            String.valueOf(congSuatArr[rand.nextInt(congSuatArr.length)])});
                }
            }
        }

        // ---------- 6. Phien ban thiet ke + quan he cau thanh ----------
        // Ngay co dinh de cung seed cho ra file giong het nhau o moi ngay chay.
        List<String[]> phienBan = Arrays.asList(
                new String[]{V1, rootId, "Phiên bản Thương Mại v1.0", "2026-01-15"},
                new String[]{V2, rootId, "Phiên bản Nâng Cấp v2.0", "2026-09-15"});

        List<String[]> quanHe = new ArrayList<>();
        for (String[] e : edges) {
            int q1 = rand.nextInt(10) + 1;
            quanHe.add(new String[]{V1, e[0], e[1], String.valueOf(q1)});
        }
        // v2.0: cung cau truc, khoang 10% dinh muc so luong thay doi
        List<String[]> v2 = new ArrayList<>();
        for (String[] r : new ArrayList<>(quanHe)) {
            int q = Integer.parseInt(r[3]);
            if (rand.nextInt(10) == 0) q = rand.nextInt(10) + 1;
            v2.add(new String[]{V2, r[1], r[2], String.valueOf(q)});
        }
        quanHe.addAll(v2);

        // ---------- 7. Chu trinh co y ----------
        if (cycle) {
            if (depth < 2) throw new IllegalArgumentException("--cycle can depth >= 2");
            String c = level[depth - 1][0];
            String p1 = parentsOf.get(c).get(0);
            quanHe.add(new String[]{V1, c, p1, "1"}); // p1 -> c -> p1
            System.out.println("[!] Chu trinh 2 nut : " + p1 + " -> " + c + " -> " + p1);
            if (depth >= 3) {
                String p2 = parentsOf.get(p1).get(0);
                quanHe.add(new String[]{V1, c, p2, "1"}); // p2 -> p1 -> c -> p2
                System.out.println("[!] Chu trinh 3 nut : " + p2 + " -> " + p1 + " -> " + c + " -> " + p2);
            }
        }

        // ---------- 8. Xuat CSV (UTF-8) ----------
        writeCSV(outDir + "/LOAI_THANH_PHAN.csv", new String[]{"MaLoai", "TenLoai"}, loai);
        writeCSV(outDir + "/THANH_PHAN.csv",
                new String[]{"MaThanhPhan", "TenThanhPhan", "MaLoai", "DonGia", "DonViTinh"}, thanhPhan);
        writeCSV(outDir + "/CUM_LAP_RAP.csv", new String[]{"MaThanhPhan"}, cumLapRap);
        writeCSV(outDir + "/LINH_KIEN_CO_KHI.csv", new String[]{"MaThanhPhan", "ChatLieu", "KichThuoc"}, coKhi);
        writeCSV(outDir + "/LINH_KIEN_DIEN_TU.csv", new String[]{"MaThanhPhan", "DienAp", "CongSuat"}, dienTu);
        writeCSV(outDir + "/PHIEN_BAN_THIET_KE.csv",
                new String[]{"MaPhienBan", "MaThanhPhanGoc", "TenPhienBan", "NgayTao"}, phienBan);
        writeCSV(outDir + "/QUAN_HE_CAU_THANH.csv", new String[]{"MaPhienBan", "MaCha", "MaCon", "SoLuong"}, quanHe);

        System.out.printf(Locale.ROOT,
                "[+] Xong: %d thanh phan (%d cum, %d linh kien), %d canh moi phien ban, %d linh kien dung chung (%.1f%%)%n",
                size, cumLapRap.size(), size - cumLapRap.size(), edges.size(),
                realShared, 100.0 * realShared / (size - 1));
        System.out.println("[+] Thu muc: " + outDir);
    }

    static Map<String, String> parseArgs(String[] args) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--")) throw new IllegalArgumentException("Tham so la: " + args[i]);
            String key = args[i].substring(2);
            if (key.equals("cycle")) { m.put(key, "true"); continue; }
            if (i + 1 >= args.length) throw new IllegalArgumentException("Thieu gia tri cho --" + key);
            m.put(key, args[++i]);
        }
        return m;
    }

    static String esc(String s) {
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    static void writeCSV(String path, String[] headers, List<String[]> rows) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(new File(path).toPath(), StandardCharsets.UTF_8)) {
            w.write(String.join(",", headers));
            w.write("\n");
            for (String[] r : rows) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < r.length; i++) {
                    if (i > 0) sb.append(',');
                    sb.append(esc(r[i]));
                }
                w.write(sb.toString());
                w.write("\n");
            }
        }
        System.out.println(" -> " + path + " (" + rows.size() + " dong)");
    }
}
