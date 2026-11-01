package vn.edu.bom.oodb.repository;

import vn.edu.bom.oodb.model.PhienBan;
import vn.edu.bom.oodb.util.Jpa;

import java.util.List;
import java.util.Optional;

public class PhienBanRepository {

    private final Jpa jpa;

    public PhienBanRepository(Jpa jpa) {
        this.jpa = jpa;
    }

    public List<PhienBan> findAll() {
        return jpa.read(em -> em.createQuery(
                        "SELECT p FROM PhienBan p ORDER BY p.ma", PhienBan.class)
                .getResultList());
    }

    /**
     * Nap phien ban kem cum goc de tranh lazy-load ngoai EntityManager.
     */
    public Optional<PhienBan> findByMa(String ma) {
        return jpa.read(em -> {
            List<PhienBan> result = em.createQuery(
                            "SELECT p FROM PhienBan p " +
                                    "LEFT JOIN FETCH p.goc " +
                                    "LEFT JOIN FETCH p.sanPham " +
                                    "WHERE p.ma = :ma", PhienBan.class)
                    .setParameter("ma", ma)
                    .getResultList();
            return result.stream().findFirst();
        });
    }

    public List<String> findAllMa() {
        return jpa.read(em -> em.createQuery(
                        "SELECT p.ma FROM PhienBan p ORDER BY p.ma", String.class)
                .getResultList());
    }

    public PhienBan save(PhienBan phienBan) {
        return jpa.inTx(em -> {
            if (em.find(PhienBan.class, phienBan.getMa()) == null) {
                em.persist(phienBan);
                return phienBan;
            }
            return em.merge(phienBan);
        });
    }

    public long count() {
        return jpa.read(em -> em.createQuery(
                        "SELECT COUNT(p) FROM PhienBan p", Long.class)
                .getSingleResult());
    }
}
