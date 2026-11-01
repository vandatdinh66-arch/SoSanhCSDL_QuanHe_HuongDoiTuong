package vn.edu.bom.oodb.importer;

import vn.edu.bom.oodb.util.CsvReader;
import vn.edu.bom.oodb.util.CsvTable;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tinh ket qua ky vong truc tiep tu CSV, doc lap voi ObjectDB.
 *
 * <p>Dung lam "oracle" de doi chieu ket qua cua repository. Nho vay viec kiem
 * tra khong phu thuoc vao chinh cach ObjectDB luu tru.</p>
 */
public final class CsvOracle {

    private final CsvTable thanhPhanTable;
    private final CsvTable phienBanTable;
    private final CsvTable quanHeTable;
    private final Set<String> cumIds = new HashSet<>();
    private final Map<String, String> tenOf = new HashMap<>();
    private final Map<String, String> loaiOf = new HashMap<>();
    private final Map<String, BigDecimal> giaOf = new HashMap<>();
    private final Map<String, String> rootOf = new LinkedHashMap<>();
    private final Map<String, Map<String, Integer>> canhTheoPhienBan = new LinkedHashMap<>();

    public CsvOracle(Path dir) throws IOException {
        thanhPhanTable = CsvReader.read(dir.resolve("THANH_PHAN.csv"));
        phienBanTable = CsvReader.read(dir.resolve("PHIEN_BAN_THIET_KE.csv"));
        quanHeTable = CsvReader.read(dir.resolve("QUAN_HE_CAU_THANH.csv"));
        CsvTable cumTable = CsvReader.read(dir.resolve("CUM_LAP_RAP.csv"));
        CsvTable coKhiTable = CsvReader.read(dir.resolve("LINH_KIEN_CO_KHI.csv"));
        CsvTable dienTuTable = CsvReader.read(dir.resolve("LINH_KIEN_DIEN_TU.csv"));

        for (String[] row : cumTable.rows()) {
            cumIds.add(cumTable.get(row, "MaThanhPhan"));
        }
        for (String[] row : thanhPhanTable.rows()) {
            String ma = thanhPhanTable.get(row, "MaThanhPhan");
            tenOf.put(ma, thanhPhanTable.get(row, "TenThanhPhan"));
            loaiOf.put(ma, thanhPhanTable.get(row, "MaLoai"));
            String gia = thanhPhanTable.get(row, "DonGia");
            if (gia != null && !gia.isBlank()) {
                giaOf.put(ma, new BigDecimal(gia));
            }
        }
        // Gia chi co y nghia voi linh kien (BR-04); bo gia cua cum de tranh
        // tinh sai neu file sinh du lieu co ghi gia cho moi dong.
        for (String cum : cumIds) {
            giaOf.remove(cum);
        }
        for (String[] row : phienBanTable.rows()) {
            rootOf.put(phienBanTable.get(row, "MaPhienBan"),
                    phienBanTable.get(row, "MaThanhPhanGoc"));
        }
        for (String[] row : quanHeTable.rows()) {
            String pb = quanHeTable.get(row, "MaPhienBan");
            String cha = quanHeTable.get(row, "MaCha");
            String con = quanHeTable.get(row, "MaCon");
            int sl = Integer.parseInt(quanHeTable.get(row, "SoLuong"));
            canhTheoPhienBan.computeIfAbsent(pb, x -> new LinkedHashMap<>()).put(cha + "|" + con, sl);
        }
    }

    public Set<String> cacPhienBan() {
        return rootOf.keySet();
    }

    public int soThanhPhan() {
        return thanhPhanTable.size();
    }

    public int soCumLapRap() {
        return cumIds.size();
    }

    public int soLinhKienCoKhi() {
        return (int) loaiOf.values().stream().filter("LTP03"::equals).count();
    }

    public int soLinhKienDienTu() {
        return (int) loaiOf.values().stream().filter("LTP04"::equals).count();
    }

    public int soPhienBan() {
        return phienBanTable.size();
    }

    public int soQuanHe() {
        return quanHeTable.size();
    }

    public String goc(String phienBan) {
        return rootOf.get(phienBan);
    }

    public Map<String, Integer> canh(String phienBan) {
        return canhTheoPhienBan.getOrDefault(phienBan, Map.of());
    }

    public Map<String, Long> tongLuongTheoLa(String phienBan) {
        Map<String, KetQuaDonVi> memo = new HashMap<>();
        return moRongDonVi(phienBan, goc(phienBan), memo, new HashSet<>()).soLuong();
    }

    public BigDecimal tongChiPhi(String phienBan) {
        Map<String, KetQuaDonVi> memo = new HashMap<>();
        return moRongDonVi(phienBan, goc(phienBan), memo, new HashSet<>()).chiPhi();
    }

    public int doSau(String phienBan) {
        return doSau(phienBan, goc(phienBan), new HashMap<>(), new HashSet<>());
    }

    public int soNut(String phienBan) {
        return tapDenDuoc(phienBan).nut().size();
    }

    public int soCanh(String phienBan) {
        return tapDenDuoc(phienBan).canh().size();
    }

    public List<String> conTrucTiep(String phienBan, String cha) {
        List<String> result = new ArrayList<>();
        for (String key : canh(phienBan).keySet()) {
            String[] parts = key.split("\\|", 2);
            if (parts[0].equals(cha)) {
                result.add(parts[1]);
            }
        }
        result.sort(String::compareTo);
        return result;
    }

    public Set<String> whereUsed(String phienBan, String conMa) {
        Map<String, List<String>> parents = new HashMap<>();
        for (String key : canh(phienBan).keySet()) {
            String[] parts = key.split("\\|", 2);
            parents.computeIfAbsent(parts[1], x -> new ArrayList<>()).add(parts[0]);
        }
        Set<String> result = new LinkedHashSet<>();
        Set<String> daGap = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        stack.push(conMa);
        daGap.add(conMa);
        while (!stack.isEmpty()) {
            String hienTai = stack.pop();
            for (String cha : parents.getOrDefault(hienTai, List.of())) {
                if (daGap.add(cha)) {
                    result.add(cha);
                    stack.push(cha);
                }
            }
        }
        return result;
    }

    public Map<String, Integer> soCumDungMoiLinhKien(String phienBan) {
        Map<String, Set<String>> cumTheoLa = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : canh(phienBan).entrySet()) {
            String[] parts = e.getKey().split("\\|", 2);
            if (!cumIds.contains(parts[1])) {
                cumTheoLa.computeIfAbsent(parts[1], x -> new HashSet<>()).add(parts[0]);
            }
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> e : cumTheoLa.entrySet()) {
            result.put(e.getKey(), e.getValue().size());
        }
        return result;
    }

    public Set<String> cumKhongCoCon(String phienBan) {
        Set<String> coCon = new HashSet<>();
        for (String key : canh(phienBan).keySet()) {
            coCon.add(key.split("\\|", 2)[0]);
        }
        Set<String> result = new HashSet<>(cumIds);
        result.removeAll(coCon);
        return result;
    }

    public String ten(String ma) {
        return tenOf.get(ma);
    }

    public String loai(String ma) {
        return loaiOf.get(ma);
    }

    public BigDecimal gia(String ma) {
        return giaOf.get(ma);
    }

    private KetQuaDonVi moRongDonVi(String phienBan,
                                    String cum,
                                    Map<String, KetQuaDonVi> memo,
                                    Set<String> dangXet) {
        KetQuaDonVi cached = memo.get(cum);
        if (cached != null) {
            return cached;
        }
        if (!dangXet.add(cum)) {
            throw new IllegalStateException("CSV co chu trinh tai " + cum + " trong " + phienBan);
        }

        Map<String, Long> soLuong = new LinkedHashMap<>();
        BigDecimal chiPhi = BigDecimal.ZERO;
        for (Map.Entry<String, Integer> e : canh(phienBan).entrySet()) {
            String[] parts = e.getKey().split("\\|", 2);
            if (!parts[0].equals(cum)) {
                continue;
            }
            String con = parts[1];
            int sl = e.getValue();
            if (cumIds.contains(con)) {
                KetQuaDonVi conKetQua = moRongDonVi(phienBan, con, memo, dangXet);
                for (Map.Entry<String, Long> ce : conKetQua.soLuong().entrySet()) {
                    soLuong.merge(ce.getKey(), ce.getValue() * sl, Long::sum);
                }
                chiPhi = chiPhi.add(conKetQua.chiPhi().multiply(BigDecimal.valueOf(sl)));
            } else {
                soLuong.merge(con, (long) sl, Long::sum);
                BigDecimal gia = giaOf.get(con);
                if (gia != null) {
                    chiPhi = chiPhi.add(gia.multiply(BigDecimal.valueOf(sl)));
                }
            }
        }
        dangXet.remove(cum);
        KetQuaDonVi result = new KetQuaDonVi(soLuong, chiPhi);
        memo.put(cum, result);
        return result;
    }

    private int doSau(String phienBan, String cum, Map<String, Integer> memo, Set<String> dangXet) {
        Integer cached = memo.get(cum);
        if (cached != null) {
            return cached;
        }
        if (!dangXet.add(cum)) {
            throw new IllegalStateException("CSV co chu trinh tai " + cum + " trong " + phienBan);
        }
        int result = 0;
        for (Map.Entry<String, Integer> e : canh(phienBan).entrySet()) {
            String[] parts = e.getKey().split("\\|", 2);
            if (!parts[0].equals(cum)) {
                continue;
            }
            String con = parts[1];
            int conDoSau = cumIds.contains(con) ? doSau(phienBan, con, memo, dangXet) : 0;
            result = Math.max(result, 1 + conDoSau);
        }
        dangXet.remove(cum);
        memo.put(cum, result);
        return result;
    }

    private TapDenDuoc tapDenDuoc(String phienBan) {
        Set<String> nut = new HashSet<>();
        Set<String> canh = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        stack.push(goc(phienBan));
        nut.add(goc(phienBan));
        while (!stack.isEmpty()) {
            String cum = stack.pop();
            for (Map.Entry<String, Integer> e : canh(phienBan).entrySet()) {
                String[] parts = e.getKey().split("\\|", 2);
                if (!parts[0].equals(cum)) {
                    continue;
                }
                canh.add(e.getKey());
                if (nut.add(parts[1]) && cumIds.contains(parts[1])) {
                    stack.push(parts[1]);
                }
            }
        }
        return new TapDenDuoc(nut, canh);
    }

    private record KetQuaDonVi(Map<String, Long> soLuong, BigDecimal chiPhi) {
    }

    private record TapDenDuoc(Set<String> nut, Set<String> canh) {
    }
}
