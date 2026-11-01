package vn.edu.bom.oodb.model;

import javax.persistence.Entity;
import javax.persistence.Id;

/**
 * Danh muc loai thanh phan (LTP01..LTP04 trong bo du lieu sinh).
 * Giu ma/ten trong CSDL de bao toan dung du lieu dau vao, thay vi hard-code enum.
 */
@Entity
public class LoaiThanhPhan {

    @Id
    private String maLoai;

    private String tenLoai;

    protected LoaiThanhPhan() {
    }

    public LoaiThanhPhan(String maLoai, String tenLoai) {
        this.maLoai = maLoai;
        this.tenLoai = tenLoai;
    }

    public String getMaLoai() {
        return maLoai;
    }

    public void setMaLoai(String maLoai) {
        this.maLoai = maLoai;
    }

    public String getTenLoai() {
        return tenLoai;
    }

    public void setTenLoai(String tenLoai) {
        this.tenLoai = tenLoai;
    }

    @Override
    public String toString() {
        return maLoai + " - " + tenLoai;
    }
}
