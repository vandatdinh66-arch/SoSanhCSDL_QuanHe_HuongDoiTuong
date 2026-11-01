package vn.edu.bom.oodb.repository;

import vn.edu.bom.oodb.model.SanPham;
import vn.edu.bom.oodb.util.Jpa;

import java.util.List;
import java.util.Optional;

public class SanPhamRepository {

    private final Jpa jpa;

    public SanPhamRepository(Jpa jpa) {
        this.jpa = jpa;
    }

    public List<SanPham> findAll() {
        return jpa.read(em -> em.createQuery(
                        "SELECT s FROM SanPham s ORDER BY s.ma", SanPham.class)
                .getResultList());
    }

    public Optional<SanPham> findByMa(String ma) {
        return jpa.read(em -> Optional.ofNullable(em.find(SanPham.class, ma)));
    }

    public SanPham save(SanPham sanPham) {
        return jpa.inTx(em -> {
            if (em.find(SanPham.class, sanPham.getMa()) == null) {
                em.persist(sanPham);
                return sanPham;
            }
            return em.merge(sanPham);
        });
    }
}
