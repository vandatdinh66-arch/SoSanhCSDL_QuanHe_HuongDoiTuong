package vn.edu.bom.oodb.experiment;

import vn.edu.bom.oodb.dto.VersionDiff;
import vn.edu.bom.oodb.model.CauThanh;
import vn.edu.bom.oodb.model.CumLapRap;
import vn.edu.bom.oodb.model.PhienBan;
import vn.edu.bom.oodb.model.ThanhPhan;
import vn.edu.bom.oodb.repository.BomRepository;
import vn.edu.bom.oodb.repository.CauThanhRepository;
import vn.edu.bom.oodb.repository.PhienBanRepository;
import vn.edu.bom.oodb.repository.ThanhPhanRepository;
import vn.edu.bom.oodb.util.Jpa;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sinh "bo so lieu thuc nghiem thao tac cap nhat/giao dich" (TV3 - Tuan 3):
 *
 * <ol>
 *   <li><b>Chuoi thao tac co kiem soat (N11-N14 + chu ky song cua phien ban)</b>
 *       tren mot phien ban sao chep rieng (khong pha du lieu goc): them/sua/thay
 *       the thanh phan, ca duong thanh cong va duong bi chan dung (chu trinh,
 *       BR-14, quan he khong ton tai), roi phat hanh va kiem tra khoa cau truc
 *       sau phat hanh. Dung lai cac Repository san xuat (khong co code rieng cho
     *       test) de so lieu phan anh dung hanh vi thuc te.</li>
 *   <li><b>Thuc nghiem "gioi han tuong duong"</b>: nhieu transaction chay dong
 *       thoi tren cung mot EntityManagerFactory (nhieu client cung ghi vao 1
 *       CSDL ObjectDB), vua truong hop khong xung dot khoa (moi thread 1 canh
 *       (cha,con) rieng) vua truong hop xung dot thuc su (nhieu thread cung sua
 *       1 canh) - do xem ObjectDB xu ly nhat quan nhu the nao khi khong dung
 *       khoa ung dung (chi dua vao transaction cua ObjectDB).</li>
 * </ol>
 *
 * <p>Ket qua duoc ghi ra 2 file CSV co cau truoc (danh cho TV4 lam bang/bieu do)
 * va 1 file tom tat markdown.</p>
 */
public final class TransactionExperimentRunner {

    public record OperationResult(
            String kichBan,
            String phienBan,
            String maThaoTac,
            String loaiThaoTac,
            String thamSo,
            String ketQua,
            long thoiGianMs,
            String ghiChu) {
    }

    public record ConcurrencyResult(
            String kichBan,
            String loaiKiemTra,
            int soThread,
            int soThanhCong,
            int soLoi,
            String loaiLoiChinh,
            String giaTriCuoiCung,
            boolean nhatQuan,
            long thoiGianTongMs,
            String ghiChu) {
    }

    private interface ThaoTac {
        void run() throws Exception;
    }

    private final List<OperationResult> ketQuaThaoTac = new ArrayList<>();
    private final List<ConcurrencyResult> ketQuaDongThoi = new ArrayList<>();

    public List<OperationResult> ketQuaThaoTac() {
        return ketQuaThaoTac;
    }

    public List<ConcurrencyResult> ketQuaDongThoi() {
        return ketQuaDongThoi;
    }

    // ------------------------------------------------------------------
    // 1) Chuoi thao tac co kiem soat N11-N14
    // ------------------------------------------------------------------

    /**
     * Chay toan bo chuoi thao tac tren mot kich ban da duoc nap san vao {@code jpa}.
     * {@code nguonPb} phai la phien ban PHAT_HANH (vi CsvDataImporter nap phien ban
     * dau tien la PHAT_HANH) de co san mot phep thu BR-14 tren du lieu thuc.
     */
    public void chayChuoiThaoTac(Jpa jpa, String kichBan, String nguonPb) {
        BomRepository bom = new BomRepository(jpa);
        CauThanhRepository cauThanhRepo = new CauThanhRepository(jpa);
        ThanhPhanRepository thanhPhanRepo = new ThanhPhanRepository(jpa);
        PhienBanRepository phienBanRepo = new PhienBanRepository(jpa);

        String demo = "TX_DEMO_" + kichBan;

        thucHien(kichBan, nguonPb, "N14", "Sao chep phien ban",
                "nguon=" + nguonPb + ", dich=" + demo, false,
                () -> bom.saoChepPhienBan(nguonPb, demo, "Ban sao thuc nghiem TV3 (" + kichBan + ")"));

        PhienBan goc = phienBanRepo.findByMa(nguonPb).orElseThrow();
        CumLapRap root = goc.getGoc();
        List<CauThanh> canhDemo = cauThanhRepo.findByVersion(demo);

        ChaVaLa la1 = timLaThuTheoBFS(canhDemo, root, 1);
        ChaVaLa la2 = timLaThuTheoBFS(canhDemo, root, 2);
        CumLapRap cumKhongPhaiGoc = timCumKhongPhaiGoc(thanhPhanRepo, root);

        if (la1 == null) {
            thucHien(kichBan, demo, "N12", "Sua so luong (bo qua)",
                    "khong tim thay linh kien la nao trong cay", false,
                    () -> {
                        throw new IllegalStateException("Kich ban khong co linh kien la de thu nghiem");
                    });
        } else {
            // N12 - thanh cong
            CauThanh canhLa1 = cauThanhRepo.findRelation(demo, la1.cha().getMa(), la1.la().getMa()).orElseThrow();
            int soLuongMoi = canhLa1.getSoLuong() + 1;
            thucHien(kichBan, demo, "N12", "Sua so luong - thanh cong",
                    "cha=" + la1.cha().getMa() + ", con=" + la1.la().getMa() + ", SL=" + soLuongMoi, false,
                    () -> bom.suaSoLuong(demo, la1.cha().getMa(), la1.la().getMa(), soLuongMoi));

            // N12 - loi: quan he khong ton tai
            thucHien(kichBan, demo, "N12", "Sua so luong - quan he khong ton tai",
                    "cha=" + la1.cha().getMa() + ", con=MA_KHONG_TON_TAI_XYZ", true,
                    () -> bom.suaSoLuong(demo, la1.cha().getMa(), "MA_KHONG_TON_TAI_XYZ", 1));

            // N12 - loi: BR-14, phien ban nguon da PHAT_HANH
            thucHien(kichBan, nguonPb, "N12", "Sua so luong - chan boi BR-14 (phien ban da phat hanh)",
                    "cha=" + la1.cha().getMa() + ", con=" + la1.la().getMa(), true,
                    () -> bom.suaSoLuong(nguonPb, la1.cha().getMa(), la1.la().getMa(), 1));

            if (cumKhongPhaiGoc != null) {
                // N11 - thanh cong: gan them la1 (dung chung) vao mot cum khac
                thucHien(kichBan, demo, "N11", "Them thanh phan con - thanh cong (dung chung)",
                        "cha=" + cumKhongPhaiGoc.getMa() + ", con=" + la1.la().getMa() + ", SL=5", false,
                        () -> bom.themCumCon(demo, cumKhongPhaiGoc.getMa(), la1.la().getMa(), 5));

                // N11 - loi: tao chu trinh (goc la con cua mot cum nam duoi goc)
                thucHien(kichBan, demo, "N11", "Them thanh phan con - chan chu trinh",
                        "cha=" + cumKhongPhaiGoc.getMa() + ", con=" + root.getMa(), true,
                        () -> bom.themCumCon(demo, cumKhongPhaiGoc.getMa(), root.getMa(), 1));

                // N13 - loi: thay the tao chu trinh (la1 vua la con cua cumKhongPhaiGoc o buoc tren)
                thucHien(kichBan, demo, "N13", "Thay the linh kien - chan chu trinh",
                        "cu=" + la1.la().getMa() + " (con cua " + cumKhongPhaiGoc.getMa() + "), moi=" + root.getMa(), true,
                        () -> bom.thayTheLinhKien(demo, la1.la().getMa(), root.getMa()));
            }

            if (la2 != null) {
                thucHien(kichBan, demo, "N13", "Thay the linh kien - thanh cong (co the gop trung khoa)",
                        "cu=" + la1.la().getMa() + ", moi=" + la2.la().getMa(), false,
                        () -> bom.thayTheLinhKien(demo, la1.la().getMa(), la2.la().getMa()));
            }
        }

        // N8 - thong tin: do lech giua ban goc va ban da sua nhieu thanh phan
        thucHien(kichBan, demo, "N8", "So sanh voi ban goc (thong tin, khong phai pass/fail)",
                nguonPb + " vs " + demo, false,
                () -> {
                    VersionDiff diff = bom.soSanhPhienBan(nguonPb, demo);
                    ghiChuCuoi("them=" + diff.themMoi().size()
                            + ", xoa=" + diff.biXoa().size()
                            + ", doi so luong=" + diff.doiSoLuong().size());
                });

        // Phat hanh
        thucHien(kichBan, demo, "N15", "Phat hanh phien ban (BR-15)", "phien ban=" + demo, false,
                () -> bom.phatHanhPhienBan(demo));

        // Sau phat hanh: khoa cau truc phai hoat dong tren chinh phien ban nay
        if (la1 != null) {
            thucHien(kichBan, demo, "N12", "Sua so luong - chan boi BR-14 (sau khi tu phat hanh)",
                    "cha=" + la1.cha().getMa() + ", con=" + la1.la().getMa(), true,
                    () -> bom.suaSoLuong(demo, la1.cha().getMa(), la1.la().getMa(), 999));
        }
    }

    private String ghiChuTamThoi;

    private void ghiChuCuoi(String ghiChu) {
        this.ghiChuTamThoi = ghiChu;
    }

    private void thucHien(String kichBan, String phienBan, String maThaoTac, String loaiThaoTac,
                           String thamSo, boolean mongDoiBiChan, ThaoTac hanhDong) {
        ghiChuTamThoi = null;
        long start = System.nanoTime();
        String ketQua;
        String ghiChu;
        try {
            hanhDong.run();
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            if (mongDoiBiChan) {
                ketQua = "LOI_KHONG_MONG_DOI";
                ghiChu = "Mong doi bi chan nhung lai thanh cong - can kiem tra lai logic ngan chan";
            } else {
                ketQua = "OK";
                ghiChu = ghiChuTamThoi != null ? ghiChuTamThoi : "Thanh cong";
            }
            ketQuaThaoTac.add(new OperationResult(kichBan, phienBan, maThaoTac, loaiThaoTac, thamSo,
                    ketQua, elapsedMs, ghiChu));
        } catch (Exception e) {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            if (mongDoiBiChan) {
                ketQua = "OK_CHAN_DUNG";
                ghiChu = e.getClass().getSimpleName() + ": " + rutGonThongBao(e.getMessage());
            } else {
                ketQua = "LOI_KHONG_MONG_DOI";
                ghiChu = e.getClass().getSimpleName() + ": " + rutGonThongBao(e.getMessage());
            }
            ketQuaThaoTac.add(new OperationResult(kichBan, phienBan, maThaoTac, loaiThaoTac, thamSo,
                    ketQua, elapsedMs, ghiChu));
        }
    }

    private static String rutGonThongBao(String message) {
        if (message == null) {
            return "";
        }
        return message.length() > 160 ? message.substring(0, 160) + "..." : message;
    }

    private record ChaVaLa(CumLapRap cha, ThanhPhan la) {
    }

    /**
     * Duyet BFS tu goc theo canh cua {@code canh}, tra ve la (thanh phan khong
     * phai cum) thu {@code thuTu} (1-based) gap duoc theo thu tu BFS (uu tien
     * cap thap). Dung cho bat ky do sau/do rong cay nao (A/B/C/D deu khac nhau).
     */
    private ChaVaLa timLaThuTheoBFS(List<CauThanh> canh, CumLapRap root, int thuTu) {
        Map<String, List<CauThanh>> theoCha = new HashMap<>();
        for (CauThanh q : canh) {
            theoCha.computeIfAbsent(q.getCha().getMa(), k -> new ArrayList<>()).add(q);
        }
        Deque<CumLapRap> stack = new ArrayDeque<>();
        Set<String> daGap = new HashSet<>();
        stack.push(root);
        daGap.add(root.getMa());
        int soDaGap = 0;
        while (!stack.isEmpty()) {
            CumLapRap cum = stack.pop();
            for (CauThanh q : theoCha.getOrDefault(cum.getMa(), List.of())) {
                ThanhPhan con = q.getCon();
                if (!(con instanceof CumLapRap)) {
                    soDaGap++;
                    if (soDaGap == thuTu) {
                        return new ChaVaLa(cum, con);
                    }
                } else if (daGap.add(con.getMa())) {
                    stack.push((CumLapRap) con);
                }
            }
        }
        return null;
    }

    private CumLapRap timCumKhongPhaiGoc(ThanhPhanRepository thanhPhanRepo, CumLapRap root) {
        for (CumLapRap c : thanhPhanRepo.findAllCumLapRap()) {
            if (!c.getMa().equals(root.getMa())) {
                return c;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 2) Thuc nghiem "gioi han tuong duong" (concurrency)
    // ------------------------------------------------------------------

    /**
     * Tao mot phien ban NHAP rieng (khong bao gio phat hanh) de thu nghiem dong
     * thoi, roi chay 2 loai kiem tra voi tung so luong thread trong {@code soThreadList}:
     * <ul>
     *   <li>KHONG_XUNG_DOT: moi thread ghi vao mot canh (cha,con) khac nhau -
     *       ky vong tat ca thanh cong vi khong tranh chap tai nguyen.</li>
     *   <li>XUNG_DOT_CUNG_CANH: tat ca thread cung sua SO LUONG cua MOT canh
     *       duy nhat voi gia tri dich khac nhau - do xem ObjectDB (khong co
     *       @Version/khoa tuong minh trong code) xu ly ra sao: co bao nhieu
     *       thread thanh cong, gia tri cuoi cung co phai la mot trong cac gia
     *       tri da thu khong (khong bi hong du lieu thanh gia tri "la").</li>
     * </ul>
     */
    public void chayThucNghiemDongThoi(Jpa jpa, String kichBan, String nguonPb, int[] soThreadList)
            throws Exception {
        BomRepository bom = new BomRepository(jpa);
        CauThanhRepository cauThanhRepo = new CauThanhRepository(jpa);
        PhienBanRepository phienBanRepo = new PhienBanRepository(jpa);
        ThanhPhanRepository thanhPhanRepo = new ThanhPhanRepository(jpa);

        String txPb = "TX_CONCURRENT_" + kichBan;
        bom.saoChepPhienBan(nguonPb, txPb, "Ban sao rieng cho thu nghiem dong thoi (" + kichBan + ")");

        PhienBan goc = phienBanRepo.findByMa(nguonPb).orElseThrow();
        CumLapRap root = goc.getGoc();
        List<CauThanh> canh = cauThanhRepo.findByVersion(txPb);

        int maxThread = 0;
        for (int n : soThreadList) {
            maxThread = Math.max(maxThread, n);
        }
        List<ChaVaLa> laDaTim = timNLaDauTien(canh, root, Math.max(maxThread + 2, 12));
        if (laDaTim.size() < maxThread + 1) {
            ketQuaDongThoi.add(new ConcurrencyResult(kichBan, "BO_QUA", 0, 0, 0, "", "", true, 0,
                    "Khong du linh kien la (" + laDaTim.size() + ") de chay thu nghiem dong thoi toi "
                            + maxThread + " thread"));
            return;
        }

        for (int soThread : soThreadList) {
            // (a) Khong xung dot: moi thread sua 1 canh rieng (root -> la khac nhau).
            chayKhongXungDot(bom, cauThanhRepo, kichBan, txPb, root, laDaTim, soThread);

            // (b) Xung dot: tat ca thread cung sua 1 canh duy nhat.
            chayXungDotCungCanh(bom, cauThanhRepo, kichBan, txPb, laDaTim.get(0), soThread);
        }

        // txPb co the da bi "lam ban" boi cac thao tac them/sua tren - khong phat hanh,
        // chi dung cho thu nghiem dong thoi roi bo (khong anh huong du lieu khac).
        thanhPhanRepo.count(); // cham DB mot lan de dam bao EMF/EM van song truoc khi ket thuc
    }

    private void chayKhongXungDot(BomRepository bom, CauThanhRepository cauThanhRepo, String kichBan,
                                   String txPb, CumLapRap root, List<ChaVaLa> laDaTim, int soThread)
            throws Exception {
        List<ThanhPhan> muc = new ArrayList<>();
        for (int i = 1; i <= soThread; i++) {
            muc.add(laDaTim.get(i).la());
        }

        ExecutorService pool = Executors.newFixedThreadPool(soThread);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (ThanhPhan la : muc) {
            tasks.add(() -> {
                bom.themCumCon(txPb, root.getMa(), la.getMa(), 7);
                return true;
            });
        }

        long start = System.nanoTime();
        List<Future<Boolean>> futures = pool.invokeAll(tasks, 60, TimeUnit.SECONDS);
        int thanhCong = 0;
        int loi = 0;
        String loaiLoiChinh = "";
        for (Future<Boolean> f : futures) {
            try {
                if (Boolean.TRUE.equals(f.get())) {
                    thanhCong++;
                }
            } catch (Exception e) {
                loi++;
                loaiLoiChinh = rootCauseName(e);
            }
        }
        pool.shutdown();
        long elapsed = (System.nanoTime() - start) / 1_000_000;

        boolean nhatQuan = thanhCong == soThread;
        ketQuaDongThoi.add(new ConcurrencyResult(kichBan, "KHONG_XUNG_DOT", soThread, thanhCong, loi,
                loaiLoiChinh, "n/a (moi thread 1 canh rieng)", nhatQuan, elapsed,
                thanhCong + "/" + soThread + " thread ghi thanh cong tren " + soThread + " canh doc lap"));
    }

    private void chayXungDotCungCanh(BomRepository bom, CauThanhRepository cauThanhRepo, String kichBan,
                                      String txPb, ChaVaLa mucTieu, int soThread) throws Exception {
        String chaMa = mucTieu.cha().getMa();
        String conMa = mucTieu.la().getMa();
        int giaTriBanDau = cauThanhRepo.findRelation(txPb, chaMa, conMa)
                .map(CauThanh::getSoLuong)
                .orElse(-1);
        AtomicInteger loiCount = new AtomicInteger(0);
        Map<Integer, String> loaiLoiTheoThread = new java.util.concurrent.ConcurrentHashMap<>();

        ExecutorService pool = Executors.newFixedThreadPool(soThread);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < soThread; i++) {
            int giaTriDich = 500 + i;
            tasks.add(() -> {
                try {
                    bom.suaSoLuong(txPb, chaMa, conMa, giaTriDich);
                    return giaTriDich;
                } catch (Exception e) {
                    loiCount.incrementAndGet();
                    loaiLoiTheoThread.put(giaTriDich, rootCauseName(e));
                    throw e;
                }
            });
        }

        long start = System.nanoTime();
        List<Future<Integer>> futures = pool.invokeAll(tasks, 60, TimeUnit.SECONDS);
        Set<Integer> giaTriThanhCong = new HashSet<>();
        for (Future<Integer> f : futures) {
            try {
                giaTriThanhCong.add(f.get());
            } catch (Exception ignored) {
                // da duoc dem trong loiCount
            }
        }
        pool.shutdown();
        long elapsed = (System.nanoTime() - start) / 1_000_000;

        int giaTriCuoiCung = cauThanhRepo.findRelation(txPb, chaMa, conMa)
                .map(CauThanh::getSoLuong)
                .orElse(-1);
        // Nhat quan = gia tri cuoi cung luu trong CSDL phai la mot gia tri "co thuc" - tuc la
        // hoac la mot trong cac gia tri da thu thanh cong, hoac (neu khong co thread nao thanh
        // cong) van giu dung gia tri ban dau. Bat ky gia tri nao khac nghia la du lieu bi hong.
        boolean nhatQuan = giaTriThanhCong.isEmpty()
                ? giaTriCuoiCung == giaTriBanDau
                : giaTriThanhCong.contains(giaTriCuoiCung);
        String loaiLoiChinh = loaiLoiTheoThread.values().stream().findFirst().orElse("");

        ketQuaDongThoi.add(new ConcurrencyResult(kichBan, "XUNG_DOT_CUNG_CANH", soThread,
                soThread - loiCount.get(), loiCount.get(), loaiLoiChinh,
                String.valueOf(giaTriCuoiCung), nhatQuan, elapsed,
                soThread + " thread cung sua canh " + chaMa + "->" + conMa
                        + ", cac gia tri thu: " + giaTriThanhCong + ", gia tri luu cuoi cung=" + giaTriCuoiCung));
    }

    private static String rootCauseName(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String ten = cur.getClass().getSimpleName();
        // Cac lop noi bo cua ObjectDB dat ten voi dau gach duoi dau (vi du
        // com.objectdb.jpa._OptimisticLockException ke thua javax.persistence.OptimisticLockException)
        // - bo di cho de doc trong bao cao.
        return ten.startsWith("_") ? ten.substring(1) : ten;
    }

    private List<ChaVaLa> timNLaDauTien(List<CauThanh> canh, CumLapRap root, int n) {
        Map<String, List<CauThanh>> theoCha = new HashMap<>();
        for (CauThanh q : canh) {
            theoCha.computeIfAbsent(q.getCha().getMa(), k -> new ArrayList<>()).add(q);
        }
        List<ChaVaLa> ketQua = new ArrayList<>();
        Deque<CumLapRap> stack = new ArrayDeque<>();
        Set<String> daGap = new HashSet<>();
        stack.push(root);
        daGap.add(root.getMa());
        while (!stack.isEmpty() && ketQua.size() < n) {
            CumLapRap cum = stack.pop();
            for (CauThanh q : theoCha.getOrDefault(cum.getMa(), List.of())) {
                ThanhPhan con = q.getCon();
                if (!(con instanceof CumLapRap)) {
                    ketQua.add(new ChaVaLa(cum, con));
                    if (ketQua.size() >= n) {
                        break;
                    }
                } else if (daGap.add(con.getMa())) {
                    stack.push((CumLapRap) con);
                }
            }
        }
        return ketQua;
    }

    // ------------------------------------------------------------------
    // Xuat file
    // ------------------------------------------------------------------

    public void xuatThaoTacCsv(Path file) throws IOException {
        List<String> header = List.of("kich_ban", "phien_ban", "ma_thao_tac", "loai_thao_tac",
                "tham_so", "ket_qua", "thoi_gian_ms", "ghi_chu");
        List<List<String>> rows = new ArrayList<>();
        for (OperationResult r : ketQuaThaoTac) {
            rows.add(List.of(r.kichBan(), r.phienBan(), r.maThaoTac(), r.loaiThaoTac(),
                    r.thamSo(), r.ketQua(), String.valueOf(r.thoiGianMs()), r.ghiChu()));
        }
        ghiCsv(file, header, rows);
    }

    public void xuatDongThoiCsv(Path file) throws IOException {
        List<String> header = List.of("kich_ban", "loai_kiem_tra", "so_thread", "so_thanh_cong",
                "so_loi", "loai_loi_chinh", "gia_tri_cuoi_cung", "nhat_quan", "thoi_gian_tong_ms", "ghi_chu");
        List<List<String>> rows = new ArrayList<>();
        for (ConcurrencyResult r : ketQuaDongThoi) {
            rows.add(List.of(r.kichBan(), r.loaiKiemTra(), String.valueOf(r.soThread()),
                    String.valueOf(r.soThanhCong()), String.valueOf(r.soLoi()), r.loaiLoiChinh(),
                    r.giaTriCuoiCung(), String.valueOf(r.nhatQuan()), String.valueOf(r.thoiGianTongMs()),
                    r.ghiChu()));
        }
        ghiCsv(file, header, rows);
    }

    private static void ghiCsv(Path file, List<String> header, List<List<String>> rows) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write(String.join(",", header));
            w.newLine();
            for (List<String> row : rows) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < row.size(); i++) {
                    if (i > 0) {
                        sb.append(',');
                    }
                    sb.append(csvEscape(row.get(i)));
                }
                w.write(sb.toString());
                w.newLine();
            }
        }
    }

    private static String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        boolean canQuote = value.contains(",") || value.contains("\"") || value.contains("\n");
        String v = value.replace("\"", "\"\"");
        return canQuote ? "\"" + v + "\"" : v;
    }

    public void xuatTomTatMarkdown(Path file, List<String> kichBanDaChay) throws IOException {
        Map<String, int[]> dem = new LinkedHashMap<>(); // kichBan -> [OK, OK_CHAN_DUNG, LOI_KHONG_MONG_DOI]
        for (OperationResult r : ketQuaThaoTac) {
            int[] arr = dem.computeIfAbsent(r.kichBan(), k -> new int[3]);
            switch (r.ketQua()) {
                case "OK" -> arr[0]++;
                case "OK_CHAN_DUNG" -> arr[1]++;
                default -> arr[2]++;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# TV3 - Bo so lieu thuc nghiem thao tac cap nhat/giao dich\n\n");
        sb.append("Sinh tu `OodbApplication tx-experiment`, chay tren du lieu thuc cua TV5 ")
                .append("(kich ban: ").append(String.join(", ", kichBanDaChay)).append(").\n\n");
        sb.append("## 1. Chuoi thao tac N11-N14 (file `tv3-transaction-log.csv`)\n\n");
        sb.append("| Kich ban | OK (thanh cong dung mong doi) | OK_CHAN_DUNG (bi chan dung mong doi) | LOI_KHONG_MONG_DOI |\n");
        sb.append("|---|---|---|---|\n");
        for (Map.Entry<String, int[]> e : dem.entrySet()) {
            int[] a = e.getValue();
            sb.append("| ").append(e.getKey()).append(" | ").append(a[0]).append(" | ").append(a[1])
                    .append(" | ").append(a[2]).append(" |\n");
        }
        sb.append("\n`LOI_KHONG_MONG_DOI` phai luon bang 0 - neu khac 0 nghia la logic ngan chan ")
                .append("(chu trinh, BR-14, BR-09...) khong hoat dong dung nhu thiet ke tren du lieu that.\n\n");

        sb.append("## 2. Thuc nghiem gioi han tuong duong / dong thoi (file `tv3-concurrency-log.csv`)\n\n");
        sb.append("- `KHONG_XUNG_DOT`: N thread ghi dong thoi vao N canh (cha,con) khac nhau - ky vong tat ca thanh cong.\n");
        sb.append("- `XUNG_DOT_CUNG_CANH`: N thread cung sua so luong cua **mot** canh duy nhat voi N gia tri dich khac nhau, ")
                .append("khong dung khoa ung dung (chi dua vao transaction cua ObjectDB). Cot `nhat_quan=true` nghia la ")
                .append("gia tri cuoi cung luu trong CSDL **trung voi mot trong cac gia tri da thu** (khong bi hong thanh ")
                .append("mot gia tri khac/rac), du co the mot so thread bi loi do xung dot.\n\n");
        sb.append("| Kich ban | Loai kiem tra | So thread | Thanh cong | Loi | Nhat quan |\n");
        sb.append("|---|---|---|---|---|---|\n");
        for (ConcurrencyResult r : ketQuaDongThoi) {
            sb.append("| ").append(r.kichBan()).append(" | ").append(r.loaiKiemTra()).append(" | ")
                    .append(r.soThread()).append(" | ").append(r.soThanhCong()).append(" | ")
                    .append(r.soLoi()).append(" | ").append(r.nhatQuan()).append(" |\n");
        }

        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }
}
