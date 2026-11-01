package vn.edu.bom.oodb.repository;

import vn.edu.bom.oodb.model.CauThanh;
import vn.edu.bom.oodb.util.Jpa;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DAO cho canh cau thanh. Cac phuong thuc doc deu JOIN FETCH de tra ve
 * doi tuong da nap day du, tranh lazy-load ngoai EntityManager.
 */
public class CauThanhRepository {

    private final Jpa jpa;

    public CauThanhRepository(Jpa jpa) {
        this.jpa = jpa;
    }

    /**
     * N1: thanh phan con truc tiep cua mot cum trong mot phien ban.
     */
    public List<CauThanh> findDirectChildren(String phienBanMa, String chaMa) {
        return jpa.read(em -> dedupe(em.createQuery(
                                "SELECT DISTINCT q FROM CauThanh q " +
                                        "JOIN FETCH q.con " +
                                        "WHERE q.phienBan.ma = :pb AND q.cha.ma = :cha " +
                                        "ORDER BY q.con.ma",
                                CauThanh.class)
                        .setParameter("pb", phienBanMa)
                        .setParameter("cha", chaMa)
                        .getResultList().stream()
                        .filter(q -> q.getPhienBan().getMa().equals(phienBanMa))
                        .toList()));
    }

    /**
     * Toan bo canh cua mot phien ban. Dung cho benchmark, so sanh phien ban
     * va cac thuat toan duyet do thi.
     */
    public List<CauThanh> findByVersion(String phienBanMa) {
        return jpa.read(em -> dedupe(em.createQuery(
                                "SELECT DISTINCT q FROM CauThanh q " +
                                        "JOIN FETCH q.cha " +
                                        "JOIN FETCH q.con " +
                                        "WHERE q.phienBan.ma = :pb " +
                                        "ORDER BY q.cha.ma, q.con.ma",
                                CauThanh.class)
                        .setParameter("pb", phienBanMa)
                        .getResultList().stream()
                        .filter(q -> q.getPhienBan().getMa().equals(phienBanMa))
                        .toList()));
    }

    public Optional<CauThanh> findRelation(String phienBanMa, String chaMa, String conMa) {
        return jpa.read(em -> {
            List<CauThanh> result = em.createQuery(
                            "SELECT q FROM CauThanh q " +
                                    "WHERE q.phienBan.ma = :pb " +
                                    "AND q.cha.ma = :cha AND q.con.ma = :con",
                            CauThanh.class)
                    .setParameter("pb", phienBanMa)
                    .setParameter("cha", chaMa)
                    .setParameter("con", conMa)
                    .getResultList();
            return result.stream()
                    .filter(q -> q.getPhienBan().getMa().equals(phienBanMa))
                    .findFirst();
        });
    }

    /**
     * Cac quan he ma mot thanh phan la con (dung cho where-used N5).
     */
    public List<CauThanh> findByCon(String phienBanMa, String conMa) {
        return jpa.read(em -> dedupe(em.createQuery(
                                "SELECT DISTINCT q FROM CauThanh q " +
                                        "JOIN FETCH q.cha " +
                                        "WHERE q.phienBan.ma = :pb AND q.con.ma = :con",
                                CauThanh.class)
                        .setParameter("pb", phienBanMa)
                        .setParameter("con", conMa)
                        .getResultList().stream()
                        .filter(q -> q.getPhienBan().getMa().equals(phienBanMa))
                        .toList()));
    }

    public List<String> findVersionsByCon(String conMa) {
        return jpa.read(em -> em.createQuery(
                        "SELECT DISTINCT q.phienBan.ma FROM CauThanh q " +
                                "WHERE q.con.ma = :con ORDER BY q.phienBan.ma",
                        String.class)
                .setParameter("con", conMa)
                .getResultList());
    }

    public long countByVersion(String phienBanMa) {
        return findByVersion(phienBanMa).size();
    }

    public long count() {
        return jpa.read(em -> em.createQuery(
                        "SELECT COUNT(q) FROM CauThanh q", Long.class)
                .getSingleResult());
    }

    private static List<CauThanh> dedupe(List<CauThanh> rows) {
        Map<Long, CauThanh> unique = new LinkedHashMap<>();
        for (CauThanh q : rows) {
            unique.putIfAbsent(q.getId(), q);
        }
        return new ArrayList<>(unique.values());
    }
}
