package vn.edu.bom.oodb;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.bom.oodb.model.CauThanh;
import vn.edu.bom.oodb.model.CumLapRap;
import vn.edu.bom.oodb.model.LinhKienCoKhi;
import vn.edu.bom.oodb.model.LoaiThanhPhan;
import vn.edu.bom.oodb.model.PhienBan;
import vn.edu.bom.oodb.model.SanPham;
import vn.edu.bom.oodb.model.TrangThaiPhienBan;
import vn.edu.bom.oodb.repository.BomRepository;
import vn.edu.bom.oodb.repository.CauThanhRepository;
import vn.edu.bom.oodb.util.Jpa;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiem tra rieng cac duong ghi N11-N14 cua {@link BomRepository} (them/sua/thay the
 * thanh phan, phat hanh phien ban) tren mot do thi toi gian tu dung thay vi phu thuoc
 * du lieu CSV kich ban A/B/C/D. {@link OodbSmokeTest} da bao phu duong doc (N1-N10) va
 * doi chieu voi oracle CSV; o day tap trung vao cac nhanh thanh cong/loi/gop trung cua
 * cac thao tac ghi, dieu ma OodbSmokeTest moi cham qua loa.
 *
 * <p>Do thi dung cho test:
 * <pre>
 *   CUM_ROOT --(2)--> LK_1
 *   CUM_ROOT --(4)--> LK_3
 *   CUM_ROOT --(1)--> CUM_SUB
 *   CUM_SUB  --(3)--> LK_2
 *
 *   CUM_PA --(1)--> CUM_PB --(1)--> LK_X   (nhanh rieng de test chu trinh)
 * </pre>
 */
class BomRepositoryTransactionTest {

    @TempDir
    Path temp;

    private Jpa jpa;
    private BomRepository bomRepository;
    private CauThanhRepository cauThanhRepository;

    private static final String PB_NHAP = "PB_NHAP";
    private static final String PB_PHAT_HANH = "PB_PH";

    @BeforeEach
    void setUp() {
        jpa = new Jpa("objectdb:" + temp.resolve("tx-test.odb"));
        bomRepository = new BomRepository(jpa);
        cauThanhRepository = new CauThanhRepository(jpa);

        jpa.runInTx(em -> {
            LoaiThanhPhan loaiCum = new LoaiThanhPhan("LTP01", "Cum lap rap");
            LoaiThanhPhan loaiCoKhi = new LoaiThanhPhan("LTP03", "Linh kien co khi");
            em.persist(loaiCum);
            em.persist(loaiCoKhi);

            SanPham sp = new SanPham("SP_TEST", "San pham test", "Dung cho unit test N11-N14");
            em.persist(sp);

            CumLapRap root = new CumLapRap("CUM_ROOT", "Cum goc", null, "Bo", loaiCum);
            CumLapRap sub = new CumLapRap("CUM_SUB", "Cum con", null, "Bo", loaiCum);
            CumLapRap pa = new CumLapRap("CUM_PA", "Cum PA", null, "Bo", loaiCum);
            CumLapRap pb2 = new CumLapRap("CUM_PB", "Cum PB", null, "Bo", loaiCum);
            em.persist(root);
            em.persist(sub);
            em.persist(pa);
            em.persist(pb2);

            LinhKienCoKhi l1 = new LinhKienCoKhi("LK_1", "Linh kien 1", null, "Cai",
                    loaiCoKhi, new BigDecimal("10.00"), "Nhua", "1x1");
            LinhKienCoKhi l2 = new LinhKienCoKhi("LK_2", "Linh kien 2", null, "Cai",
                    loaiCoKhi, new BigDecimal("20.00"), "Nhua", "2x2");
            LinhKienCoKhi l3 = new LinhKienCoKhi("LK_3", "Linh kien 3", null, "Cai",
                    loaiCoKhi, new BigDecimal("30.00"), "Nhom", "3x3");
            LinhKienCoKhi l4 = new LinhKienCoKhi("LK_4", "Linh kien 4 (chua dung)", null, "Cai",
                    loaiCoKhi, new BigDecimal("40.00"), "Nhom", "4x4");
            LinhKienCoKhi lx = new LinhKienCoKhi("LK_X", "Linh kien X", null, "Cai",
                    loaiCoKhi, new BigDecimal("5.00"), "Nhua", "1x1");
            em.persist(l1);
            em.persist(l2);
            em.persist(l3);
            em.persist(l4);
            em.persist(lx);

            PhienBan pbNhap = new PhienBan(PB_NHAP, "Phien ban nhap", LocalDate.now(),
                    TrangThaiPhienBan.NHAP, sp, root);
            PhienBan pbPhatHanh = new PhienBan(PB_PHAT_HANH, "Phien ban da phat hanh", LocalDate.now(),
                    TrangThaiPhienBan.PHAT_HANH, sp, root);
            em.persist(pbNhap);
            em.persist(pbPhatHanh);

            em.persist(new CauThanh(pbNhap, root, l1, 2));
            em.persist(new CauThanh(pbNhap, root, l3, 4));
            em.persist(new CauThanh(pbNhap, root, sub, 1));
            em.persist(new CauThanh(pbNhap, sub, l2, 3));
            em.persist(new CauThanh(pbNhap, pa, pb2, 1));
            em.persist(new CauThanh(pbNhap, pb2, lx, 1));
        });
    }

    @AfterEach
    void tearDown() {
        jpa.close();
    }

    // ------------------------------------------------------------------
    // N12: suaSoLuong
    // ------------------------------------------------------------------

    @Test
    void suaSoLuong_capNhatThanhCong() {
        bomRepository.suaSoLuong(PB_NHAP, "CUM_ROOT", "LK_1", 9);

        CauThanh quanHe = cauThanhRepository.findRelation(PB_NHAP, "CUM_ROOT", "LK_1").orElseThrow();
        assertEquals(9, quanHe.getSoLuong());
    }

    @Test
    void suaSoLuong_quanHeKhongTonTai_baoLoi() {
        assertThrows(IllegalArgumentException.class,
                () -> bomRepository.suaSoLuong(PB_NHAP, "CUM_ROOT", "LK_2", 5));
    }

    @Test
    void suaSoLuong_soLuongKhongDuong_biChan() {
        assertThrows(IllegalArgumentException.class,
                () -> bomRepository.suaSoLuong(PB_NHAP, "CUM_ROOT", "LK_1", 0));
    }

    @Test
    void suaSoLuong_phienBanDaPhatHanh_biChanTheoBR14() {
        // BR-14: khong duoc sua cau truc cua phien ban da PHAT_HANH, du quan he
        // co ton tai hay khong - trang thai phai duoc kiem tra truoc.
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> bomRepository.suaSoLuong(PB_PHAT_HANH, "CUM_ROOT", "LK_1", 5));
        assertTrue(ex.getMessage().contains("BR-14"));
    }

    // ------------------------------------------------------------------
    // N13: thayTheLinhKien
    // ------------------------------------------------------------------

    @Test
    void thayTheLinhKien_khongTrungKhoa_thayTrucTiep() {
        int soThayDoi = bomRepository.thayTheLinhKien(PB_NHAP, "LK_2", "LK_4");

        assertEquals(1, soThayDoi);
        assertFalse(cauThanhRepository.findRelation(PB_NHAP, "CUM_SUB", "LK_2").isPresent());
        CauThanh moi = cauThanhRepository.findRelation(PB_NHAP, "CUM_SUB", "LK_4").orElseThrow();
        assertEquals(3, moi.getSoLuong());
    }

    @Test
    void thayTheLinhKien_trungKhoaVoiCanhSanCo_gopSoLuongVaXoaCanhCu() {
        // CUM_ROOT da co san canh toi LK_3 (SL=4). Thay LK_1 (SL=2) bang LK_3
        // phai gop thanh SL=6 tren mot canh duy nhat, canh LK_1 bi xoa.
        int soThayDoi = bomRepository.thayTheLinhKien(PB_NHAP, "LK_1", "LK_3");

        assertEquals(1, soThayDoi);
        assertFalse(cauThanhRepository.findRelation(PB_NHAP, "CUM_ROOT", "LK_1").isPresent());
        CauThanh gop = cauThanhRepository.findRelation(PB_NHAP, "CUM_ROOT", "LK_3").orElseThrow();
        assertEquals(6, gop.getSoLuong());
        // Khong con hai canh CUM_ROOT -> LK_3 trung lap.
        assertEquals(1, cauThanhRepository.findByVersion(PB_NHAP).stream()
                .filter(q -> q.getCha().getMa().equals("CUM_ROOT") && q.getCon().getMa().equals("LK_3"))
                .count());
    }

    @Test
    void thayTheLinhKien_seTaoChuTrinh_biChan() {
        // CUM_PA -> CUM_PB -> LK_X. Thay LK_X bang CUM_PA se tao canh
        // CUM_PB -> CUM_PA, nhung CUM_PA da di xuong duoc CUM_PB => chu trinh.
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> bomRepository.thayTheLinhKien(PB_NHAP, "LK_X", "CUM_PA"));
        assertTrue(ex.getMessage().toLowerCase().contains("chu trinh"));

        // Canh goc phai con nguyen, khong bi sua nua vo (transaction phai rollback).
        CauThanh vanConNguyen = cauThanhRepository.findRelation(PB_NHAP, "CUM_PB", "LK_X").orElseThrow();
        assertEquals(1, vanConNguyen.getSoLuong());
    }

    @Test
    void thayTheLinhKien_cungMaKhongLamGi() {
        assertEquals(0, bomRepository.thayTheLinhKien(PB_NHAP, "LK_1", "LK_1"));
    }

    // ------------------------------------------------------------------
    // phatHanhPhienBan (BR-15), scoped dung theo tung phien ban
    // ------------------------------------------------------------------

    @Test
    void phatHanhPhienBan_coCumRongTrongCayCuaChinhPhienBan_biChanDungCum() {
        // CUM_RONG duoc gan vao CUM_ROOT trong PB_NHAP nhung khong co con nao
        // -> nam trong cay cua PB_NHAP va phai bi chan dung ten.
        jpa.runInTx(em -> {
            LoaiThanhPhan loaiCum = em.find(LoaiThanhPhan.class, "LTP01");
            CumLapRap cumRong = new CumLapRap("CUM_RONG", "Cum rong", null, "Bo", loaiCum);
            em.persist(cumRong);
            CumLapRap root = em.find(CumLapRap.class, "CUM_ROOT");
            PhienBan pb = em.find(PhienBan.class, PB_NHAP);
            em.persist(new CauThanh(pb, root, cumRong, 1));
        });

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> bomRepository.phatHanhPhienBan(PB_NHAP));
        assertTrue(ex.getMessage().contains("CUM_RONG"));
    }

    @Test
    void phatHanhPhienBan_coCumMoCuaSanPhamKhacKhongLienQuan_khongBiAnhHuong() {
        // Hoi quy cho bug da sua: truoc day phatHanhPhienBan dem TOAN BO CumLapRap
        // trong CSDL ("SELECT COUNT(c) FROM CumLapRap c") roi tru di so cum co con
        // trong phien ban dang xet. Mot cum hoan toan khong lien quan - thuoc san
        // pham/phien ban khac, chua duoc gan vao bat ky cay nao - se bi dem nham
        // la "cum rong" va chan oan viec phat hanh PB_NHAP du no khong thuoc cay
        // cua PB_NHAP. Sau khi sua, ham chi duyet tu goc cua chinh phien ban dang
        // xet nen cum nay khong duoc nhac toi va publish phai thanh cong.
        jpa.runInTx(em -> {
            LoaiThanhPhan loaiCum = em.find(LoaiThanhPhan.class, "LTP01");
            em.persist(new CumLapRap("CUM_SAN_PHAM_KHAC", "Cum cua san pham khac",
                    null, "Bo", loaiCum));
        });

        bomRepository.phatHanhPhienBan(PB_NHAP);

        PhienBan pb = jpa.read(em -> em.find(PhienBan.class, PB_NHAP));
        assertEquals(TrangThaiPhienBan.PHAT_HANH, pb.getTrangThai());
    }

    @Test
    void phatHanhPhienBan_khongCoCumRong_thanhCong() {
        bomRepository.phatHanhPhienBan(PB_NHAP);

        PhienBan pb = jpa.read(em -> em.find(PhienBan.class, PB_NHAP));
        assertEquals(TrangThaiPhienBan.PHAT_HANH, pb.getTrangThai());
    }
}
