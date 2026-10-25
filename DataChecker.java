import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Kiem tra thu muc du lieu do DataGenerator sinh ra.
 *
 * Cach chay:
 *   java DataChecker data/scenarios/A
 *   java DataChecker data/scenarios/A --size 1000 --depth 3 --share 0.10
 *   java DataChecker data/scenarios/cycle --expect-cycle
 *
 * Ket qua in ra man hinh va ghi vao <thu muc>/check_report.txt. Thoat voi ma 1 neu co loi.
 */
public class DataChecker {

    static final StringBuilder out = new StringBuilder();
    static int failures = 0;

    static void line(String s) { System.out.println(s); out.append(s).append('\n'); }
    static void ok(String s) { line("[PASS] " + s); }
    static void fail(String s) { line("[FAIL] " + s); failures++; }
    static void info(String s) { line("[INFO] " + s); }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("Cach dung: java DataChecker <thu_muc> [--size N] [--depth D] [--share s] [--expect-cycle]");
            System.exit(2);
        }
        String dir = args[0];
        Map<String, String> opt = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            String k = args[i].substring(2);
            if (k.equals("expect-cycle")) opt.put(k, "true");
            else opt.put(k, args[++i]);
        }
        boolean expectCycle = opt.containsKey("expect-cycle");

        line("Bao cao kiem tra du lieu: " + dir);

        List<String[]> loai = read(dir + "/LOAI_THANH_PHAN.csv");
        List<String[]> tp = read(dir + "/THANH_PHAN.csv");
        List<String[]> cum = read(dir + "/CUM_LAP_RAP.csv");
        List<String[]> ck = read(dir + "/LINH_KIEN_CO_KHI.csv");
        List<String[]> dt = read(dir + "/LINH_KIEN_DIEN_TU.csv");
        List<String[]> pb = read(dir + "/PHIEN_BAN_THIET_KE.csv");
        List<String[]> qh = read(dir + "/QUAN_HE_CAU_THANH.csv");

        // ---- 1. Thanh phan va cac bang con ----
        Set<String> loaiIds = new HashSet<>();
        for (String[] r : loai) loaiIds.add(r[0]);

        Map<String, String> loaiOf = new LinkedHashMap<>();
        boolean dup = false;
        for (String[] r : tp) if (loaiOf.put(r[0], r[2]) != null) dup = true;
        if (dup) fail("THANH_PHAN co MaThanhPhan trung"); else ok("THANH_PHAN: khong trung ma (" + tp.size() + " dong)");

        boolean loaiOk = true;
        for (String l : loaiOf.values()) if (!loaiIds.contains(l)) loaiOk = false;
        if (loaiOk) ok("Moi MaLoai trong THANH_PHAN deu ton tai trong LOAI_THANH_PHAN");
        else fail("Co MaLoai khong ton tai trong LOAI_THANH_PHAN");

        Set<String> cumSet = ids(cum), ckSet = ids(ck), dtSet = ids(dt);
        boolean subOk = cumSet.size() + ckSet.size() + dtSet.size() == tp.size();
        for (Map.Entry<String, String> e : loaiOf.entrySet()) {
            String id = e.getKey(), l = e.getValue();
            boolean inCum = cumSet.contains(id), inCk = ckSet.contains(id), inDt = dtSet.contains(id);
            int n = (inCum ? 1 : 0) + (inCk ? 1 : 0) + (inDt ? 1 : 0);
            if (n != 1) subOk = false;
            if ((l.equals("LTP01") || l.equals("LTP02")) != inCum) subOk = false;
            if (l.equals("LTP03") != inCk) subOk = false;
            if (l.equals("LTP04") != inDt) subOk = false;
        }
        if (subOk) ok("Moi thanh phan thuoc dung 1 bang con (Cum " + cumSet.size() + ", CoKhi " + ckSet.size()
                + ", DienTu " + dtSet.size() + ") va khop MaLoai");
        else fail("Bang con khong khop THANH_PHAN / MaLoai");

        if (opt.containsKey("size")) {
            int want = Integer.parseInt(opt.get("size"));
            if (tp.size() == want) ok("So nut = " + want); else fail("So nut = " + tp.size() + ", mong doi " + want);
        }

        // ---- 2. Phien ban ----
        Map<String, String> rootOf = new LinkedHashMap<>();
        for (String[] r : pb) rootOf.put(r[0], r[1]);
        boolean rootOk = true;
        for (String r : rootOf.values()) if (!loaiOf.containsKey(r) || !cumSet.contains(r)) rootOk = false;
        if (rootOk) ok("Goc cua " + rootOf.size() + " phien ban deu la cum lap rap hop le");
        else fail("MaThanhPhanGoc khong hop le");

        // ---- 3. Quan he cau thanh ----
        Set<String> pk = new HashSet<>();
        boolean refOk = true, qtyOk = true, pkOk = true, selfOk = true, parentTypeOk = true;
        Map<String, List<String[]>> edgesByVersion = new LinkedHashMap<>();
        for (String[] r : qh) {
            String v = r[0], cha = r[1], con = r[2];
            if (!rootOf.containsKey(v) || !loaiOf.containsKey(cha) || !loaiOf.containsKey(con)) refOk = false;
            if (cha.equals(con)) selfOk = false;
            if (!cumSet.contains(cha)) parentTypeOk = false;
            try { if (Integer.parseInt(r[3]) <= 0) qtyOk = false; } catch (NumberFormatException e) { qtyOk = false; }
            if (!pk.add(v + "|" + cha + "|" + con)) pkOk = false;
            edgesByVersion.computeIfAbsent(v, x -> new ArrayList<>()).add(new String[]{cha, con});
        }
        report(refOk, "Khoa ngoai MaPhienBan/MaCha/MaCon hop le (" + qh.size() + " dong)", "Co khoa ngoai khong ton tai trong QUAN_HE_CAU_THANH");
        report(selfOk, "Khong co dong MaCha = MaCon", "Co dong MaCha = MaCon");
        report(qtyOk, "SoLuong la so nguyen > 0", "Co SoLuong khong hop le");
        report(pkOk, "Khoa chinh (MaPhienBan, MaCha, MaCon) khong trung", "Khoa chinh QUAN_HE_CAU_THANH bi trung");
        report(parentTypeOk, "MaCha luon la cum lap rap", "Co MaCha khong phai cum lap rap");

        // ---- 4. Do thi tung phien ban ----
        for (Map.Entry<String, List<String[]>> ve : edgesByVersion.entrySet()) {
            String v = ve.getKey();
            String root = rootOf.get(v);
            List<String[]> edges = ve.getValue();
            Map<String, List<String>> adj = new HashMap<>();
            Map<String, Integer> indeg = new LinkedHashMap<>();
            for (String id : loaiOf.keySet()) indeg.put(id, 0);
            for (String[] e : edges) {
                adj.computeIfAbsent(e[0], x -> new ArrayList<>()).add(e[1]);
                indeg.merge(e[1], 1, Integer::sum);
            }
            line("---- Phien ban " + v + ": " + edges.size() + " canh ----");

            // Kahn: tim chu trinh
            Map<String, Integer> deg = new HashMap<>(indeg);
            Deque<String> q = new ArrayDeque<>();
            for (Map.Entry<String, Integer> e : deg.entrySet()) if (e.getValue() == 0) q.add(e.getKey());
            List<String> topo = new ArrayList<>();
            while (!q.isEmpty()) {
                String u = q.poll();
                topo.add(u);
                for (String w : adj.getOrDefault(u, Collections.emptyList()))
                    if (deg.merge(w, -1, Integer::sum) == 0) q.add(w);
            }
            boolean cyclic = topo.size() < loaiOf.size();
            if (expectCycle) {
                if (cyclic) ok(v + ": phat hien chu trinh nhu mong doi (" + (loaiOf.size() - topo.size()) + " nut khong sap xep topo duoc vi chu trinh)");
                else if (v.equals("PB_v1.0")) fail(v + ": mong doi co chu trinh nhung khong tim thay");
                else info(v + ": khong co chu trinh (chu trinh chi chen vao PB_v1.0)");
                continue;
            }
            if (cyclic) { fail(v + ": co chu trinh (" + (loaiOf.size() - topo.size()) + " nut khong sap xep topo duoc)"); continue; }
            ok(v + ": do thi khong co chu trinh (DAG)");

            // moi nut tru goc co cha
            int noParent = 0;
            for (Map.Entry<String, Integer> e : indeg.entrySet())
                if (!e.getKey().equals(root) && e.getValue() == 0) noParent++;
            report(noParent == 0, v + ": moi nut tru goc deu co cha", v + ": co " + noParent + " nut khong co cha");
            report(indeg.get(root) == 0, v + ": goc khong co cha", v + ": goc dang co cha");

            // cum co it nhat 1 con
            int emptyCum = 0;
            for (String c : cumSet) if (!adj.containsKey(c)) emptyCum++;
            report(emptyCum == 0, v + ": moi cum lap rap co it nhat 1 con", v + ": co " + emptyCum + " cum khong co con");

            // do sau that su = duong dai nhat tu goc
            Map<String, Integer> dist = new HashMap<>();
            dist.put(root, 0);
            int maxDepth = 0;
            for (String u : topo) {
                Integer du = dist.get(u);
                if (du == null) continue;
                maxDepth = Math.max(maxDepth, du);
                for (String w : adj.getOrDefault(u, Collections.emptyList()))
                    dist.merge(w, du + 1, Math::max);
            }
            int reach = dist.size();
            report(reach == loaiOf.size(), v + ": tat ca " + reach + " nut deu di den duoc tu goc",
                    v + ": chi " + reach + "/" + loaiOf.size() + " nut di den duoc tu goc");

            int shared = 0, nonRoot = loaiOf.size() - 1, leaves = 0;
            for (Map.Entry<String, Integer> e : indeg.entrySet()) {
                if (!e.getKey().equals(root) && e.getValue() >= 2) shared++;
                if (!cumSet.contains(e.getKey())) leaves++;
            }
            double ratio = nonRoot == 0 ? 0 : (double) shared / nonRoot;
            info(String.format(Locale.ROOT, "%s: so nut=%d, canh=%d, do sau that=%d, linh kien la=%d, nut dung chung=%d (%.1f%%)",
                    v, loaiOf.size(), edges.size(), maxDepth, leaves, shared, ratio * 100));
            info(String.format(Locale.ROOT, "%s: so con trung binh moi cum=%.2f", v, (double) edges.size() / Math.max(1, cumSet.size())));

            if (v.equals("PB_v1.0")) {
                if (opt.containsKey("depth")) {
                    int want = Integer.parseInt(opt.get("depth"));
                    report(maxDepth == want, v + ": do sau = " + want, v + ": do sau = " + maxDepth + ", mong doi " + want);
                }
                if (opt.containsKey("share")) {
                    double want = Double.parseDouble(opt.get("share"));
                    report(Math.abs(ratio - want) <= 0.01, String.format(Locale.ROOT, "%s: ti le dung chung %.1f%% xap xi %.0f%%", v, ratio * 100, want * 100),
                            String.format(Locale.ROOT, "%s: ti le dung chung %.1f%%, mong doi %.0f%%", v, ratio * 100, want * 100));
                }
            }
        }

        line("");
        line(failures == 0 ? "KET LUAN: DAT (khong co loi)" : "KET LUAN: KHONG DAT (" + failures + " loi)");

        try (PrintWriter w = new PrintWriter(new File(dir + "/check_report.txt"), "UTF-8")) {
            w.print(out);
        }
        System.exit(failures == 0 ? 0 : 1);
    }

    static void report(boolean cond, String pass, String failMsg) {
        if (cond) ok(pass); else fail(failMsg);
    }

    static Set<String> ids(List<String[]> rows) {
        Set<String> s = new HashSet<>();
        for (String[] r : rows) s.add(r[0]);
        return s;
    }

    /** Doc CSV (UTF-8), bo dong tieu de, ho tro truong trong dau nhay kep. */
    static List<String[]> read(String path) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(new File(path).toPath(), StandardCharsets.UTF_8)) {
            br.readLine();
            String l;
            while ((l = br.readLine()) != null) {
                if (l.isEmpty()) continue;
                rows.add(split(l));
            }
        }
        return rows;
    }

    static String[] split(String l) {
        List<String> f = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < l.length(); i++) {
            char c = l.charAt(i);
            if (q) {
                if (c == '"') {
                    if (i + 1 < l.length() && l.charAt(i + 1) == '"') { sb.append('"'); i++; }
                    else q = false;
                } else sb.append(c);
            } else if (c == '"') q = true;
            else if (c == ',') { f.add(sb.toString()); sb.setLength(0); }
            else sb.append(c);
        }
        f.add(sb.toString());
        return f.toArray(new String[0]);
    }
}
