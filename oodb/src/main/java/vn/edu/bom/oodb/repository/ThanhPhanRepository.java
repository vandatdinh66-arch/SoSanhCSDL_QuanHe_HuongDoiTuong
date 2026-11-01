package vn.edu.bom.oodb.repository;

import vn.edu.bom.oodb.dto.SharedComponent;
import vn.edu.bom.oodb.model.CumLapRap;
import vn.edu.bom.oodb.model.LinhKienCoKhi;
import vn.edu.bom.oodb.model.LinhKienDienTu;
import vn.edu.bom.oodb.model.ThanhPhan;
import vn.edu.bom.oodb.util.Jpa;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Truy van danh muc thanh phan va cac truy van da hinh (N6, N9, N10).
 */
public class ThanhPhanRepository {

    private final Jpa jpa;

    public ThanhPhanRepository(Jpa jpa) {
        this.jpa = jpa;
    }

    public Optional<ThanhPhan> findByMa(String ma) {
        return jpa.read(em -> Optional.ofNullable(em.find(ThanhPhan.class, ma)));
    }

    public List<ThanhPhan> findAll() {
        return jpa.read(em -> em.createQuery(
                        "SELECT t FROM ThanhPhan t ORDER BY t.ma", ThanhPhan.class)
                .getResultList());
    }

    public List<CumLapRap> findAllCumLapRap() {
        return jpa.read(em -> em.createQuery(
                        "SELECT c FROM CumLapRap c ORDER BY c.ma", CumLapRap.class)
                .getResultList());
    }

    /**
     * N9a: loc linh kien co khi theo vat lieu. Truy van da hinh tren lop con.
     */
    public List<LinhKienCoKhi> locLinhKienCoKhiTheoVatLieu(String vatLieu) {
        return jpa.read(em -> em.createQuery(
                        "SELECT c FROM LinhKienCoKhi c WHERE c.chatLieu = :vatLieu ORDER BY c.ma",
                        LinhKienCoKhi.class)
                .setParameter("vatLieu", vatLieu)
                .getResultList());
    }

    /**
     * N9b: loc linh kien dien tu theo khoang dien ap dinh muc.
     */
    public List<LinhKienDienTu> locLinhKienDienTuTheoDienAp(double min, double max) {
        return jpa.read(em -> em.createQuery(
                        "SELECT d FROM LinhKienDienTu d " +
                                "WHERE d.dienAp BETWEEN :min AND :max ORDER BY d.ma",
                        LinhKienDienTu.class)
                .setParameter("min", min)
                .setParameter("max", max)
                .getResultList());
    }

    /**
     * N6: linh kien duoc dung chung o nhieu cum nhat trong mot phien ban.
     * COUNT(DISTINCT q.cha) dem so cum khac nhau, khong dem so lan lap dong.
     */
    public List<SharedComponent> linhKienDungChungNhieuNhat(String phienBanMa, int limit) {
        return jpa.read(em -> {
            List<Object[]> rows = em.createQuery(
                            "SELECT q.con.ma, q.con.ten, COUNT(DISTINCT q.cha.ma) " +
                                    "FROM CauThanh q, LinhKien l " +
                                    "WHERE q.phienBan.ma = :pb AND q.con = l " +
                                    "GROUP BY q.con.ma, q.con.ten " +
                                    "ORDER BY COUNT(DISTINCT q.cha.ma) DESC, q.con.ma",
                            Object[].class)
                    .setParameter("pb", phienBanMa)
                    .setMaxResults(limit)
                    .getResultList();
            return rows.stream()
                    .map(r -> new SharedComponent((String) r[0], (String) r[1], ((Number) r[2]).longValue()))
                    .toList();
        });
    }

    /**
     * N10: cum khong co thanh phan con trong mot phien ban.
     *
     * <p>ObjectDB 2.9.5 khong ho tro subquery/EXISTS trong JPQL, nen dung hai
     * truy van phang: lay tat ca cum va tap cum co con, roi hieu trong Java.
     * Cach nay van giu duoc chi phi O(n) va khong phu thuoc vao du lieu lon.</p>
     */
    public List<CumLapRap> cumKhongCoCon(String phienBanMa) {
        List<CumLapRap> tatCa = jpa.read(em -> em.createQuery(
                        "SELECT c FROM CumLapRap c ORDER BY c.ma", CumLapRap.class)
                .getResultList());
        Set<String> coCon = new HashSet<>(jpa.read(em -> em.createQuery(
                        "SELECT DISTINCT q.cha.ma FROM CauThanh q WHERE q.phienBan.ma = :pb",
                        String.class)
                .setParameter("pb", phienBanMa)
                .getResultList()));
        return tatCa.stream().filter(c -> !coCon.contains(c.getMa())).toList();
    }

    public long count() {
        return jpa.read(em -> em.createQuery(
                        "SELECT COUNT(t) FROM ThanhPhan t", Long.class)
                .getSingleResult());
    }

    public long countCumLapRap() {
        return jpa.read(em -> em.createQuery(
                        "SELECT COUNT(c) FROM CumLapRap c", Long.class)
                .getSingleResult());
    }

    public long countLinhKienCoKhi() {
        return jpa.read(em -> em.createQuery(
                        "SELECT COUNT(c) FROM LinhKienCoKhi c", Long.class)
                .getSingleResult());
    }

    public long countLinhKienDienTu() {
        return jpa.read(em -> em.createQuery(
                        "SELECT COUNT(d) FROM LinhKienDienTu d", Long.class)
                .getSingleResult());
    }
}
