package vn.edu.bom.oodb.model;

import javax.persistence.Entity;
import java.math.BigDecimal;

/**
 * Linh kien la thanh phan la, co don gia (BR-04).
 * Hai lop con the hien chuyen biet hoa tiep theo loai linh kien.
 */
@Entity
public abstract class LinhKien extends ThanhPhan {

    private BigDecimal donGia;

    protected LinhKien() {
    }

    protected LinhKien(String ma, String ten, String moTa, String donViTinh,
                       LoaiThanhPhan loai, BigDecimal donGia) {
        super(ma, ten, moTa, donViTinh, loai);
        this.donGia = donGia;
    }

    public BigDecimal getDonGia() {
        return donGia;
    }

    public void setDonGia(BigDecimal donGia) {
        this.donGia = donGia;
    }
}
