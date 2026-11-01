package vn.edu.bom.oodb.util;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Quan ly EntityManagerFactory cua ObjectDB.
 *
 * <p>ObjectDB cho phep doi URL CSDL bang thuoc tinh
 * {@code javax.persistence.jdbc.url}. Vi vay cung mot persistence-unit co the
 * dung cho nhieu file .odb khac nhau (A, B, C, D, 10k, 100k...).</p>
 */
public final class Jpa implements AutoCloseable {

    public static final String PERSISTENCE_UNIT = "bom-oodb";

    private final EntityManagerFactory emf;
    private final String dbUrl;

    public Jpa(String dbUrl) {
        this.dbUrl = dbUrl;
        Map<String, String> props = new HashMap<>();
        props.put("javax.persistence.jdbc.url", dbUrl);
        this.emf = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT, props);
    }

    public EntityManagerFactory emf() {
        return emf;
    }

    public String dbUrl() {
        return dbUrl;
    }

    public EntityManager em() {
        return emf.createEntityManager();
    }

    /**
     * JPA 2.2 EntityManager khong implements AutoCloseable, nen dung helper nay
     * thay cho try-with-resources.
     */
    public <T> T read(Function<EntityManager, T> work) {
        EntityManager em = em();
        try {
            return work.apply(em);
        } finally {
            em.close();
        }
    }

    /**
     * Chay mot khoi cong viec trong transaction va tra ve ket qua.
     */
    public <T> T inTx(Function<EntityManager, T> work) {
        EntityManager em = em();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            T result = work.apply(em);
            tx.commit();
            return result;
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    public void runInTx(java.util.function.Consumer<EntityManager> work) {
        inTx(em -> {
            work.accept(em);
            return null;
        });
    }

    /**
     * Xoa file CSDL ObjectDB de nap lai tu dau. ObjectDB co the tao them file
     * khoa tam thoi voi hau to "$" nen can xoa ca hai.
     */
    public static void deleteDatabaseFiles(String dbUrl) {
        String path = dbUrl.startsWith("objectdb:") ? dbUrl.substring("objectdb:".length()) : dbUrl;
        int semi = path.indexOf(';');
        if (semi >= 0) {
            path = path.substring(0, semi);
        }
        for (String suffix : new String[]{"", "$", ".lck"}) {
            try {
                Files.deleteIfExists(Path.of(path + suffix));
            } catch (IOException e) {
                throw new IllegalStateException("Khong xoa duoc file CSDL: " + path + suffix, e);
            }
        }
    }

    @Override
    public void close() {
        if (emf.isOpen()) {
            emf.close();
        }
    }
}
