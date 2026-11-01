package vn.edu.bom.oodb.importer;

import vn.edu.bom.oodb.model.CumLapRap;
import vn.edu.bom.oodb.model.LinhKienCoKhi;
import vn.edu.bom.oodb.model.LinhKienDienTu;
import vn.edu.bom.oodb.model.LoaiThanhPhan;
import vn.edu.bom.oodb.model.PhienBan;
import vn.edu.bom.oodb.model.SanPham;
import vn.edu.bom.oodb.model.ThanhPhan;
import vn.edu.bom.oodb.model.TrangThaiPhienBan;
import vn.edu.bom.oodb.util.CsvReader;
import vn.edu.bom.oodb.util.CsvTable;
import vn.edu.bom.oodb.util.Jpa;

import javax.persistence.EntityManager;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Nap du lieu CSV do DataGenerator sinh ra vao ObjectDB.
 *
 * <p>Du lieu duoc nap theo 4 pha de ho tro ca bo 100.000 ban ghi:
 * danh muc -> thanh phan -> phien ban -> quan he cau thanh. Cac pha dung
 * {@code getReference} de tranh phai giu toan bo do thi trong bo nho.</p>
 */
public class CsvDataImporter {

    private static final int BATCH = 2_000;

    private final Jpa jpa;

    public CsvDataImporter(Jpa jpa) {
        this.jpa = jpa;
    }

    public ImportReport importScenario(Path dir,
                                       String sanPhamMa,
                                       String sanPhamTen,
                                       boolean phienBanDauPhatHanh) throws IOException {
        long start = System.currentTimeMillis();

        CsvTable loaiTable = CsvReader.read(dir.resolve("LOAI_THANH_PHAN.csv"));
        CsvTable thanhPhanTable = CsvReader.read(dir.resolve("THANH_PHAN.csv"));
        CsvTable cumTable = CsvReader.read(dir.resolve("CUM_LAP_RAP.csv"));
        CsvTable coKhiTable = CsvReader.read(dir.resolve("LINH_KIEN_CO_KHI.csv"));
        CsvTable dienTuTable = CsvReader.read(dir.resolve("LINH_KIEN_DIEN_TU.csv"));
        CsvTable phienBanTable = CsvReader.read(dir.resolve("PHIEN_BAN_THIET_KE.csv"));
        CsvTable quanHeTable = CsvReader.read(dir.resolve("QUAN_HE_CAU_THANH.csv"));

        Set<String> cumIds = ids(cumTable);
        Map<String, String[]> coKhiRows = byId(coKhiTable);
        Map<String, String[]> dienTuRows = byId(dienTuTable);

        // ---- Pha 1: danh muc loai + san pham ----
        jpa.runInTx(em -> {
            for (String[] row : loaiTable.rows()) {
                em.persist(new LoaiThanhPhan(
                        loaiTable.get(row, "MaLoai"),
                        loaiTable.get(row, "TenLoai")));
            }
            em.persist(new SanPham(sanPhamMa, sanPhamTen,
                    "San pham minh hoa cho do an so sanh CSDL quan he va huong doi tuong"));
        });

        // ---- Pha 2: thanh phan va cac bang con ----
        List<String[]> thanhPhanRows = thanhPhanTable.rows();
        for (int from = 0; from < thanhPhanRows.size(); from += BATCH) {
            int to = Math.min(from + BATCH, thanhPhanRows.size());
            List<String[]> batch = thanhPhanRows.subList(from, to);
            jpa.runInTx(em -> {
                for (String[] row : batch) {
                    String ma = thanhPhanTable.get(row, "MaThanhPhan");
                    String ten = thanhPhanTable.get(row, "TenThanhPhan");
                    String maLoai = thanhPhanTable.get(row, "MaLoai");
                    String donViTinh = thanhPhanTable.get(row, "DonViTinh");
                    LoaiThanhPhan loai = em.getReference(LoaiThanhPhan.class, maLoai);

                    ThanhPhan tp;
                    if (cumIds.contains(ma)) {
                        tp = new CumLapRap(ma, ten, null, donViTinh, loai);
                    } else if (coKhiRows.containsKey(ma)) {
                        String[] ck = coKhiRows.get(ma);
                        tp = new LinhKienCoKhi(ma, ten, null, donViTinh, loai,
                                new BigDecimal(thanhPhanTable.get(row, "DonGia")),
                                coKhiTable.get(ck, "ChatLieu"),
                                coKhiTable.get(ck, "KichThuoc"));
                    } else if (dienTuRows.containsKey(ma)) {
                        String[] dt = dienTuRows.get(ma);
                        tp = new LinhKienDienTu(ma, ten, null, donViTinh, loai,
                                new BigDecimal(thanhPhanTable.get(row, "DonGia")),
                                Double.parseDouble(dienTuTable.get(dt, "DienAp")),
                                Double.parseDouble(dienTuTable.get(dt, "CongSuat")));
                    } else {
                        throw new IllegalStateException("Thanh phan " + ma
                                + " khong thuoc CUM_LAP_RAP / LINH_KIEN_CO_KHI / LINH_KIEN_DIEN_TU");
                    }
                    em.persist(tp);
                }
            });
        }

        // ---- Pha 3: phien ban ----
        List<String[]> phienBanRows = phienBanTable.rows();
        jpa.runInTx(em -> {
            SanPham sp = em.getReference(SanPham.class, sanPhamMa);
            boolean first = true;
            for (String[] row : phienBanRows) {
                String ma = phienBanTable.get(row, "MaPhienBan");
                String maGoc = phienBanTable.get(row, "MaThanhPhanGoc");
                String ten = phienBanTable.get(row, "TenPhienBan");
                LocalDate ngay = LocalDate.parse(phienBanTable.get(row, "NgayTao"));
                TrangThaiPhienBan trangThai = first && phienBanDauPhatHanh
                        ? TrangThaiPhienBan.PHAT_HANH
                        : TrangThaiPhienBan.NHAP;
                CumLapRap goc = em.getReference(CumLapRap.class, maGoc);
                em.persist(new PhienBan(ma, ten, ngay, trangThai, sp, goc));
                first = false;
            }
        });

        // ---- Pha 4: quan he cau thanh ----
        List<String[]> quanHeRows = quanHeTable.rows();
        for (int from = 0; from < quanHeRows.size(); from += BATCH) {
            int to = Math.min(from + BATCH, quanHeRows.size());
            List<String[]> batch = quanHeRows.subList(from, to);
            jpa.runInTx(em -> {
                for (String[] row : batch) {
                    String maPb = quanHeTable.get(row, "MaPhienBan");
                    String maCha = quanHeTable.get(row, "MaCha");
                    String maCon = quanHeTable.get(row, "MaCon");
                    int soLuong = Integer.parseInt(quanHeTable.get(row, "SoLuong"));

                    PhienBan pb = em.getReference(PhienBan.class, maPb);
                    CumLapRap cha = em.getReference(CumLapRap.class, maCha);
                    ThanhPhan con = em.getReference(ThanhPhan.class, maCon);
                    em.persist(new vn.edu.bom.oodb.model.CauThanh(pb, cha, con, soLuong));
                }
            });
        }

        long elapsed = System.currentTimeMillis() - start;
        int soCum = cumIds.size();
        int soCoKhi = coKhiRows.size();
        int soDienTu = dienTuRows.size();
        return new ImportReport(dir.toString(), sanPhamMa, thanhPhanRows.size(), soCum,
                soCoKhi, soDienTu, phienBanRows.size(), quanHeRows.size(), elapsed);
    }

    private static Set<String> ids(CsvTable table) {
        Set<String> result = new HashSet<>();
        for (String[] row : table.rows()) {
            result.add(table.get(row, "MaThanhPhan"));
        }
        return result;
    }

    private static Map<String, String[]> byId(CsvTable table) {
        Map<String, String[]> result = new LinkedHashMap<>();
        for (String[] row : table.rows()) {
            result.put(table.get(row, "MaThanhPhan"), row);
        }
        return result;
    }

    public record ImportReport(
            String thuMuc,
            String sanPhamMa,
            int soThanhPhan,
            int soCumLapRap,
            int soLinhKienCoKhi,
            int soLinhKienDienTu,
            int soPhienBan,
            int soQuanHe,
            long thoiGianMs) {
    }
}
