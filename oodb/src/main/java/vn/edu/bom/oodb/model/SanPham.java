package vn.edu.bom.oodb.model;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;

/**
 * San pham ban ra, co nhieu phien ban thiet ke.
 */
@Entity
public class SanPham {

    @Id
    private String ma;

    private String ten;

    private String moTa;

    @OneToMany(mappedBy = "sanPham")
    private List<PhienBan> cacPhienBan = new ArrayList<>();

    protected SanPham() {
    }

    public SanPham(String ma, String ten, String moTa) {
        this.ma = ma;
        this.ten = ten;
        this.moTa = moTa;
    }

    public String getMa() {
        return ma;
    }

    public void setMa(String ma) {
        this.ma = ma;
    }

    public String getTen() {
        return ten;
    }

    public void setTen(String ten) {
        this.ten = ten;
    }

    public String getMoTa() {
        return moTa;
    }

    public void setMoTa(String moTa) {
        this.moTa = moTa;
    }

    public List<PhienBan> getCacPhienBan() {
        return cacPhienBan;
    }

    public void setCacPhienBan(List<PhienBan> cacPhienBan) {
        this.cacPhienBan = cacPhienBan;
    }
}
