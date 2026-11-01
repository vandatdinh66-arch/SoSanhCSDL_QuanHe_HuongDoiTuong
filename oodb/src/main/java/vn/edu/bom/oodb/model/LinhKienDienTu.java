package vn.edu.bom.oodb.model;

import javax.persistence.Entity;
import java.math.BigDecimal;

@Entity
public class LinhKienDienTu extends LinhKien {

    private double dienAp;

    private double congSuat;

    protected LinhKienDienTu() {
    }

    public LinhKienDienTu(String ma, String ten, String moTa, String donViTinh,
                          LoaiThanhPhan loai, BigDecimal donGia,
                          double dienAp, double congSuat) {
        super(ma, ten, moTa, donViTinh, loai, donGia);
        this.dienAp = dienAp;
        this.congSuat = congSuat;
    }

    public double getDienAp() {
        return dienAp;
    }

    public void setDienAp(double dienAp) {
        this.dienAp = dienAp;
    }

    public double getCongSuat() {
        return congSuat;
    }

    public void setCongSuat(double congSuat) {
        this.congSuat = congSuat;
    }
}
