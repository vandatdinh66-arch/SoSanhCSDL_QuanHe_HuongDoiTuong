package vn.edu.bom.oodb.repository;

import vn.edu.bom.oodb.dto.BomLine;
import vn.edu.bom.oodb.dto.BomSummary;
import vn.edu.bom.oodb.dto.VersionDiff;
import vn.edu.bom.oodb.dto.WhereUsedResult;
import vn.edu.bom.oodb.model.CauThanh;
import vn.edu.bom.oodb.model.CumLapRap;
import vn.edu.bom.oodb.model.LinhKien;
import vn.edu.bom.oodb.model.PhienBan;
import vn.edu.bom.oodb.model.ThanhPhan;
import vn.edu.bom.oodb.model.TrangThaiPhienBan;
import vn.edu.bom.oodb.util.Jpa;

import javax.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
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
 * Repository cho cac nghiep vu BOM (N1-N14).
 *
 * <p>Cac truy van doc N1, N6, N8, N9, N10 dung JPQL. Cac truy van de quy
 * N2, N3, N4, N5, N7 dung di chuyen tham chieu doi tuong truc tiep
 * (object navigation) — day la diem manh cua mo hinh huong doi tuong va cung
 * la cach ObjectDB thuc thi tot nhat vi khong phai JOIN lai bang quan he.</p>
 */
public class BomRepository {

    private final Jpa jpa;

    public BomRepository(Jpa jpa) {
        this.jpa = jpa;
    }

    // ------------------------------------------------------------------
    // N2/N3/N4/N7: no BOM, tong luong, tong chi phi, do sau
    // ------------------------------------------------------------------

    /**
     * No BOM day du theo tung duong di (co cap, so luong don vi goc va duong dan).
     * Dung cho demo / du lieu nho. Voi du lieu rat lon nen dung
     * {@link #tinhTongHop(String)} de tranh bung no so dong.
     */
    public BomSummary noBom(String phienBanMa) {
        return jpa.read(em -> {
            PhienBan pb = requirePhienBan(em, phienBanMa);
            CumLapRap goc = pb.getGoc();
            if (goc == null) {
                throw new IllegalStateException("Phien ban " + phienBanMa + " khong co cum goc");
            }

            List<BomLine> lines = new ArrayList<>();
            Map<String, Long> soLuongTheoLa = new LinkedHashMap<>();
            Set<String> nutDaGap = new HashSet<>();
            Set<String> canhDaGap = new HashSet<>();
            Set<String> duongDi = new HashSet<>();
            BigDecimal[] tongChiPhi = {BigDecimal.ZERO};
            int[] doSau = {0};

            duongDi.add(goc.getMa());
            duyetNoBom(em, phienBanMa, goc, 0, 1L, goc.getMa(),
                    lines, soLuongTheoLa, nutDaGap, canhDaGap, duongDi, tongChiPhi, doSau);

            return new BomSummary(phienBanMa, goc.getMa(), lines, soLuongTheoLa,
                    tongChiPhi[0], doSau[0], nutDaGap.size(), canhDaGap.size());
        });
    }

    private void duyetNoBom(EntityManager em,
                            String pbMa,
                            ThanhPhan nut,
                            int cap,
                            long soLuongTichLuy,
                            String duongDan,
                            List<BomLine> lines,
                            Map<String, Long> soLuongTheoLa,
                            Set<String> nutDaGap,
                            Set<String> canhDaGap,
                            Set<String> duongDi,
                            BigDecimal[] tongChiPhi,
                            int[] doSau) {
        nutDaGap.add(nut.getMa());
        doSau[0] = Math.max(doSau[0], cap);

        if (!(nut instanceof CumLapRap cum)) {
            return;
        }

        for (CauThanh q : cum.getCacQuanHeCha()) {
            if (!q.getPhienBan().getMa().equals(pbMa)) {
                continue;
            }
            ThanhPhan con = q.getCon();
            canhDaGap.add(cum.getMa() + "|" + con.getMa());

            if (!duongDi.add(con.getMa())) {
                throw new IllegalStateException("Phat hien chu trinh tai " + con.getMa()
                        + " trong phien ban " + pbMa);
            }

            long tichLuy = soLuongTichLuy * q.getSoLuong();
            BigDecimal donGia = con instanceof LinhKien lk ? lk.getDonGia() : null;
            BigDecimal thanhTien = donGia == null
                    ? BigDecimal.ZERO
                    : donGia.multiply(BigDecimal.valueOf(tichLuy));
            String duongDanCon = duongDan + "/" + con.getMa();

            lines.add(new BomLine(con.getMa(), con.getTen(), con.getLoai().getMaLoai(),
                    cap + 1, q.getSoLuong(), tichLuy, donGia, thanhTien, duongDanCon));

            if (con instanceof CumLapRap cumCon) {
                duyetNoBom(em, pbMa, cumCon, cap + 1, tichLuy, duongDanCon,
                        lines, soLuongTheoLa, nutDaGap, canhDaGap, duongDi, tongChiPhi, doSau);
            } else {
                soLuongTheoLa.merge(con.getMa(), tichLuy, Long::sum);
                tongChiPhi[0] = tongChiPhi[0].add(thanhTien);
            }
            duongDi.remove(con.getMa());
        }
    }

    /**
     * Tinh tong hop bang quy hoach dong tren DAG: moi cum chi duoc tinh mot lan,
     * ket qua duoc nhan voi so luong cua canh. Phu hop voi cay sau / dung chung
     * nhieu vi khong bung no theo so duong di.
     */
    public BomSummary tinhTongHop(String phienBanMa) {
        return jpa.read(em -> {
            PhienBan pb = requirePhienBan(em, phienBanMa);
            CumLapRap goc = pb.getGoc();
            if (goc == null) {
                throw new IllegalStateException("Phien ban " + phienBanMa + " khong co cum goc");
            }

            Map<String, KetQuaDonVi> memo = new HashMap<>();
            Map<String, Integer> doSauMemo = new HashMap<>();
            Set<String> dangXet = new HashSet<>();
            KetQuaDonVi tong = moRongDonVi(em, phienBanMa, goc, memo, dangXet);
            int doSau = tinhDoSau(em, phienBanMa, goc, doSauMemo, new HashSet<>());

            Set<String> nut = new HashSet<>();
            Set<String> canh = new HashSet<>();
            duyetTapDenDuoc(em, phienBanMa, goc, nut, canh);

            return new BomSummary(phienBanMa, goc.getMa(), List.of(), tong.soLuong(),
                    tong.chiPhi(), doSau, nut.size(), canh.size());
        });
    }

    private KetQuaDonVi moRongDonVi(EntityManager em,
                                    String pbMa,
                                    CumLapRap cum,
                                    Map<String, KetQuaDonVi> memo,
                                    Set<String> dangXet) {
        KetQuaDonVi cached = memo.get(cum.getMa());
        if (cached != null) {
            return cached;
        }
        if (!dangXet.add(cum.getMa())) {
            throw new IllegalStateException("Phat hien chu trinh tai " + cum.getMa()
                    + " trong phien ban " + pbMa);
        }

        Map<String, Long> soLuong = new LinkedHashMap<>();
        BigDecimal chiPhi = BigDecimal.ZERO;

        for (CauThanh q : cum.getCacQuanHeCha()) {
            if (!q.getPhienBan().getMa().equals(pbMa)) {
                continue;
            }
            ThanhPhan con = q.getCon();
            int sl = q.getSoLuong();

            if (con instanceof CumLapRap cumCon) {
                KetQuaDonVi conKetQua = moRongDonVi(em, pbMa, cumCon, memo, dangXet);
                for (Map.Entry<String, Long> e : conKetQua.soLuong().entrySet()) {
                    soLuong.merge(e.getKey(), e.getValue() * sl, Long::sum);
                }
                chiPhi = chiPhi.add(conKetQua.chiPhi().multiply(BigDecimal.valueOf(sl)));
            } else {
                soLuong.merge(con.getMa(), (long) sl, Long::sum);
                if (con instanceof LinhKien lk) {
                    chiPhi = chiPhi.add(lk.getDonGia().multiply(BigDecimal.valueOf(sl)));
                }
            }
        }

        dangXet.remove(cum.getMa());
        KetQuaDonVi ketQua = new KetQuaDonVi(soLuong, chiPhi);
        memo.put(cum.getMa(), ketQua);
        return ketQua;
    }

    private int tinhDoSau(EntityManager em,
                          String pbMa,
                          CumLapRap cum,
                          Map<String, Integer> memo,
                          Set<String> dangXet) {
        Integer cached = memo.get(cum.getMa());
        if (cached != null) {
            return cached;
        }
        if (!dangXet.add(cum.getMa())) {
            throw new IllegalStateException("Phat hien chu trinh tai " + cum.getMa()
                    + " trong phien ban " + pbMa);
        }
        int doSau = 0;
        for (CauThanh q : cum.getCacQuanHeCha()) {
            if (!q.getPhienBan().getMa().equals(pbMa)) {
                continue;
            }
            ThanhPhan con = q.getCon();
            int conDoSau = con instanceof CumLapRap cumCon
                    ? tinhDoSau(em, pbMa, cumCon, memo, dangXet)
                    : 0;
            doSau = Math.max(doSau, 1 + conDoSau);
        }
        dangXet.remove(cum.getMa());
        memo.put(cum.getMa(), doSau);
        return doSau;
    }

    private void duyetTapDenDuoc(EntityManager em,
                                 String pbMa,
                                 CumLapRap goc,
                                 Set<String> nut,
                                 Set<String> canh) {
        Deque<CumLapRap> stack = new ArrayDeque<>();
        stack.push(goc);
        nut.add(goc.getMa());
        while (!stack.isEmpty()) {
            CumLapRap cum = stack.pop();
            for (CauThanh q : cum.getCacQuanHeCha()) {
                if (!q.getPhienBan().getMa().equals(pbMa)) {
                    continue;
                }
                ThanhPhan con = q.getCon();
                canh.add(cum.getMa() + "|" + con.getMa());
                if (nut.add(con.getMa()) && con instanceof CumLapRap cumCon) {
                    stack.push(cumCon);
                }
            }
        }
    }

    private record KetQuaDonVi(Map<String, Long> soLuong, BigDecimal chiPhi) {
    }

    // ------------------------------------------------------------------
    // N5: where-used
    // ------------------------------------------------------------------

    public WhereUsedResult whereUsed(String phienBanMa, String thanhPhanMa) {
        return jpa.read(em -> {
            ThanhPhan mucTieu = em.find(ThanhPhan.class, thanhPhanMa);
            if (mucTieu == null) {
                throw new IllegalArgumentException("Khong tim thay thanh phan " + thanhPhanMa);
            }

            Set<String> biAnhHuong = new LinkedHashSet<>();
            Set<String> daGap = new HashSet<>();
            Deque<ThanhPhan> stack = new ArrayDeque<>();
            stack.push(mucTieu);
            daGap.add(mucTieu.getMa());

            while (!stack.isEmpty()) {
                ThanhPhan hienTai = stack.pop();
                for (CauThanh q : hienTai.getDuocDungBoi()) {
                    if (!q.getPhienBan().getMa().equals(phienBanMa)) {
                        continue;
                    }
                    CumLapRap cha = q.getCha();
                    if (daGap.add(cha.getMa())) {
                        biAnhHuong.add(cha.getMa());
                        stack.push(cha);
                    }
                }
            }

            List<String> cacPhienBan = em.createQuery(
                            "SELECT DISTINCT q.phienBan.ma FROM CauThanh q " +
                                    "WHERE q.con.ma = :ma ORDER BY q.phienBan.ma",
                            String.class)
                    .setParameter("ma", thanhPhanMa)
                    .getResultList();

            return new WhereUsedResult(thanhPhanMa, new ArrayList<>(biAnhHuong), cacPhienBan);
        });
    }

    // ------------------------------------------------------------------
    // N8: so sanh phien ban
    // ------------------------------------------------------------------

    public VersionDiff soSanhPhienBan(String phienBanCu, String phienBanMoi) {
        return jpa.read(em -> {
            Map<String, Integer> cu = docCanh(em, phienBanCu);
            Map<String, Integer> moi = docCanh(em, phienBanMoi);

            Map<String, Integer> themMoi = new LinkedHashMap<>();
            Map<String, Integer> biXoa = new LinkedHashMap<>();
            Map<String, int[]> doiSoLuong = new LinkedHashMap<>();

            for (Map.Entry<String, Integer> e : moi.entrySet()) {
                Integer slCu = cu.get(e.getKey());
                if (slCu == null) {
                    themMoi.put(e.getKey(), e.getValue());
                } else if (!slCu.equals(e.getValue())) {
                    doiSoLuong.put(e.getKey(), new int[]{slCu, e.getValue()});
                }
            }
            for (Map.Entry<String, Integer> e : cu.entrySet()) {
                if (!moi.containsKey(e.getKey())) {
                    biXoa.put(e.getKey(), e.getValue());
                }
            }
            return new VersionDiff(themMoi, biXoa, doiSoLuong);
        });
    }

    private Map<String, Integer> docCanh(EntityManager em, String phienBanMa) {
        List<Object[]> rows = em.createQuery(
                        "SELECT q.phienBan.ma, q.cha.ma, q.con.ma, q.soLuong FROM CauThanh q " +
                                "WHERE q.phienBan.ma = :pb ORDER BY q.cha.ma, q.con.ma",
                        Object[].class)
                .setParameter("pb", phienBanMa)
                .getResultList();
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Object[] r : rows) {
            if (phienBanMa.equals(r[0])) {
                result.put(r[1] + "|" + r[2], ((Number) r[3]).intValue());
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // N11-N14: cap nhat co kiem tra rang buoc
    // ------------------------------------------------------------------

    /**
     * N11: them cum con vao cum, kiem tra chu trinh (BR-10) va trang thai
     * phien ban (BR-14). Neu quan he da ton tai thi cap nhat so luong (BR-09).
     *
     * @return true neu them moi, false neu cap nhat so luong
     */
    public boolean themCumCon(String phienBanMa, String chaMa, String conMa, int soLuong) {
        if (soLuong <= 0) {
            throw new IllegalArgumentException("So luong phai > 0");
        }
        return jpa.inTx(em -> {
            PhienBan pb = requirePhienBanSuaDuoc(em, phienBanMa);
            CumLapRap cha = requireCum(em, chaMa);
            ThanhPhan con = requireThanhPhan(em, conMa);

            if (chaMa.equals(conMa)) {
                throw new IllegalStateException("Khong the them " + conMa + " vao chinh no");
            }
            if (con instanceof CumLapRap cumCon
                    && chuaDuongDi(em, phienBanMa, cumCon, chaMa)) {
                throw new IllegalStateException("Them " + chaMa + " -> " + conMa
                        + " se tao chu trinh trong phien ban " + phienBanMa);
            }

            CauThanh daCo = timQuanHe(em, phienBanMa, chaMa, conMa);
            if (daCo != null) {
                daCo.setSoLuong(soLuong);
                return false;
            }
            em.persist(new CauThanh(pb, cha, con, soLuong));
            return true;
        });
    }

    /**
     * N12: sua so luong cua mot quan he cau thanh.
     */
    public void suaSoLuong(String phienBanMa, String chaMa, String conMa, int soLuong) {
        if (soLuong <= 0) {
            throw new IllegalArgumentException("So luong phai > 0");
        }
        jpa.runInTx(em -> {
            requirePhienBanSuaDuoc(em, phienBanMa);
            CauThanh q = timQuanHe(em, phienBanMa, chaMa, conMa);
            if (q == null) {
                throw new IllegalArgumentException("Khong co quan he " + chaMa + " -> " + conMa);
            }
            q.setSoLuong(soLuong);
        });
    }

    /**
     * N13: thay linh kien A bang linh kien B o moi cum trong mot phien ban.
     * Neu cum da co B thi cong don so luong va xoa quan he cu de tranh trung
     * khoa (phienBan, cha, con). Kiem tra chu trinh truoc khi thay.
     *
     * @return so quan he da thay doi
     */
    public int thayTheLinhKien(String phienBanMa, String cuMa, String moiMa) {
        if (cuMa.equals(moiMa)) {
            return 0;
        }
        return jpa.inTx(em -> {
            requirePhienBanSuaDuoc(em, phienBanMa);
            requireThanhPhan(em, cuMa);
            ThanhPhan moi = requireThanhPhan(em, moiMa);

            List<CauThanh> tatCa = em.createQuery(
                            "SELECT DISTINCT q FROM CauThanh q JOIN FETCH q.cha JOIN FETCH q.con " +
                                    "WHERE q.phienBan.ma = :pb",
                            CauThanh.class)
                    .setParameter("pb", phienBanMa)
                    .getResultList().stream()
                    .filter(q -> q.getPhienBan().getMa().equals(phienBanMa))
                    .toList();

            Map<String, CauThanh> theoChaCon = new LinkedHashMap<>();
            for (CauThanh q : tatCa) {
                theoChaCon.put(q.getCha().getMa() + "|" + q.getCon().getMa(), q);
            }

            List<CauThanh> cacQuanHe = tatCa.stream()
                    .filter(q -> q.getCon().getMa().equals(cuMa))
                    .toList();

            int soThayDoi = 0;
            for (CauThanh q : cacQuanHe) {
                String chaMa = q.getCha().getMa();
                if (moi instanceof CumLapRap cumMoi
                        && chuaDuongDi(em, phienBanMa, cumMoi, chaMa)) {
                    throw new IllegalStateException("Thay " + cuMa + " bang " + moiMa
                            + " tai " + chaMa + " se tao chu trinh");
                }

                String keyMoi = chaMa + "|" + moiMa;
                CauThanh daCo = theoChaCon.get(keyMoi);
                if (daCo != null && daCo.getId() != q.getId()) {
                    daCo.setSoLuong(daCo.getSoLuong() + q.getSoLuong());
                    em.remove(q);
                } else {
                    q.setCon(moi);
                    theoChaCon.put(keyMoi, q);
                }
                theoChaCon.remove(chaMa + "|" + cuMa);
                soThayDoi++;
            }
            return soThayDoi;
        });
    }

    /**
     * N14: sao chep mot phien ban thanh phien ban nhap moi.
     */
    public String saoChepPhienBan(String nguonMa, String dichMa, String tenMoi) {
        return jpa.inTx(em -> {
            PhienBan nguon = requirePhienBan(em, nguonMa);
            if (em.find(PhienBan.class, dichMa) != null) {
                throw new IllegalStateException("Phien ban " + dichMa + " da ton tai");
            }

            PhienBan dich = new PhienBan(dichMa, tenMoi, LocalDate.now(),
                    TrangThaiPhienBan.NHAP, nguon.getSanPham(), nguon.getGoc());
            em.persist(dich);

            List<CauThanh> canhNguon = em.createQuery(
                            "SELECT DISTINCT q FROM CauThanh q JOIN FETCH q.cha JOIN FETCH q.con " +
                                    "WHERE q.phienBan.ma = :pb",
                            CauThanh.class)
                    .setParameter("pb", nguonMa)
                    .getResultList();

            Map<Long, CauThanh> duyNhat = new LinkedHashMap<>();
            for (CauThanh q : canhNguon) {
                if (q.getPhienBan().getMa().equals(nguonMa)) {
                    duyNhat.putIfAbsent(q.getId(), q);
                }
            }
            for (CauThanh q : duyNhat.values()) {
                em.persist(new CauThanh(dich, q.getCha(), q.getCon(), q.getSoLuong()));
            }
            return dichMa;
        });
    }

    /**
     * Phat hanh phien ban: kiem tra moi cum co it nhat mot con (BR-15)
     * roi chuyen trang thai.
     *
     * <p>Chi xet cac cum <b>di den duoc tu goc cua chinh phien ban nay</b>
     * (duyet theo canh cua phien ban nay), khong dem toan bo CumLapRap
     * trong CSDL. Dem toan bo se sai neu CSDL chua nhieu san pham/phien ban
     * khac — cum cua san pham khac se bi tinh nham thanh "cum rong" va chan
     * phat hanh oan.</p>
     */
    public void phatHanhPhienBan(String phienBanMa) {
        jpa.runInTx(em -> {
            PhienBan pb = requirePhienBan(em, phienBanMa);
            if (pb.getTrangThai() == TrangThaiPhienBan.NHAP) {
                CumLapRap goc = pb.getGoc();
                if (goc != null) {
                    List<String> cumRong = timCumRongTrongCay(em, phienBanMa, goc);
                    if (!cumRong.isEmpty()) {
                        throw new IllegalStateException("Cac cum khong co thanh phan con trong phien ban "
                                + phienBanMa + " (BR-15): " + cumRong);
                    }
                }
            }
            pb.setTrangThai(TrangThaiPhienBan.PHAT_HANH);
        });
    }

    /**
     * Duyet tu goc theo canh cua {@code phienBanMa}, tra ve danh sach cum
     * (bao gom ca goc) khong co bat ky thanh phan con nao trong phien ban nay.
     */
    private List<String> timCumRongTrongCay(EntityManager em, String phienBanMa, CumLapRap goc) {
        List<String> cumRong = new ArrayList<>();
        Set<String> daGap = new HashSet<>();
        Deque<CumLapRap> stack = new ArrayDeque<>();
        stack.push(goc);
        daGap.add(goc.getMa());
        while (!stack.isEmpty()) {
            CumLapRap cum = stack.pop();
            List<CauThanh> conTrongPhienBan = cum.getCacQuanHeCha().stream()
                    .filter(q -> q.getPhienBan().getMa().equals(phienBanMa))
                    .toList();
            if (conTrongPhienBan.isEmpty()) {
                cumRong.add(cum.getMa());
                continue;
            }
            for (CauThanh q : conTrongPhienBan) {
                if (q.getCon() instanceof CumLapRap cumCon && daGap.add(cumCon.getMa())) {
                    stack.push(cumCon);
                }
            }
        }
        return cumRong;
    }

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------

    private PhienBan requirePhienBan(EntityManager em, String ma) {
        PhienBan pb = em.find(PhienBan.class, ma);
        if (pb == null) {
            throw new IllegalArgumentException("Khong tim thay phien ban " + ma);
        }
        return pb;
    }

    private PhienBan requirePhienBanSuaDuoc(EntityManager em, String ma) {
        PhienBan pb = requirePhienBan(em, ma);
        if (!pb.choPhepSuaCauTruc()) {
            throw new IllegalStateException("Phien ban " + ma + " dang o trang thai "
                    + pb.getTrangThai() + ", khong duoc sua cau truc (BR-14)");
        }
        return pb;
    }

    private CumLapRap requireCum(EntityManager em, String ma) {
        CumLapRap cum = em.find(CumLapRap.class, ma);
        if (cum == null) {
            throw new IllegalArgumentException("Khong tim thay cum lap rap " + ma);
        }
        return cum;
    }

    private ThanhPhan requireThanhPhan(EntityManager em, String ma) {
        ThanhPhan tp = em.find(ThanhPhan.class, ma);
        if (tp == null) {
            throw new IllegalArgumentException("Khong tim thay thanh phan " + ma);
        }
        return tp;
    }

    private CauThanh timQuanHe(EntityManager em, String pbMa, String chaMa, String conMa) {
        List<CauThanh> result = em.createQuery(
                        "SELECT q FROM CauThanh q WHERE q.phienBan.ma = :pb " +
                                "AND q.cha.ma = :cha AND q.con.ma = :con",
                        CauThanh.class)
                .setParameter("pb", pbMa)
                .setParameter("cha", chaMa)
                .setParameter("con", conMa)
                .getResultList();
        return result.isEmpty() ? null : result.get(0);
    }

    /**
     * Kiem tra tu {@code xuatPhat} co di xuong duoc {@code dich} khong.
     * Dung cho kiem tra chu trinh khi them/thay the canh.
     */
    private boolean chuaDuongDi(EntityManager em, String pbMa, CumLapRap xuatPhat, String dich) {
        Deque<CumLapRap> stack = new ArrayDeque<>();
        Set<String> daGap = new HashSet<>();
        stack.push(xuatPhat);
        daGap.add(xuatPhat.getMa());
        while (!stack.isEmpty()) {
            CumLapRap cum = stack.pop();
            if (cum.getMa().equals(dich)) {
                return true;
            }
            for (CauThanh q : cum.getCacQuanHeCha()) {
                if (!q.getPhienBan().getMa().equals(pbMa)) {
                    continue;
                }
                if (q.getCon() instanceof CumLapRap conCum && daGap.add(conCum.getMa())) {
                    stack.push(conCum);
                }
            }
        }
        return false;
    }
}
