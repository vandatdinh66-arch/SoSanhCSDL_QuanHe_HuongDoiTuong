package vn.edu.bom.oodb.model;

import com.objectdb.Index;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Phien ban thiet ke. Moi phien ban co mot cum goc va tap quan he cau thanh rieng.
 */
@Entity
@Index(members = {"sanPham"})
public class PhienBan {

    @Id
    private String ma;

    private String ten;

    private LocalDate ngayTao;

    @Enumerated(EnumType.STRING)
    private TrangThaiPhienBan trangThai;

    @ManyToOne
    private SanPham sanPham;

    @ManyToOne
    private CumLapRap goc;

    @OneToMany(mappedBy = "phienBan")
    private List<CauThanh> cacCauThanh = new ArrayList<>();

    protected PhienBan() {
    }

    public PhienBan(String ma, String ten, LocalDate ngayTao, TrangThaiPhienBan trangThai,
                    SanPham sanPham, CumLapRap goc) {
        this.ma = ma;
        this.ten = ten;
        this.ngayTao = ngayTao;
        this.trangThai = trangThai;
        this.sanPham = sanPham;
        this.goc = goc;
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

    public LocalDate getNgayTao() {
        return ngayTao;
    }

    public void setNgayTao(LocalDate ngayTao) {
        this.ngayTao = ngayTao;
    }

    public TrangThaiPhienBan getTrangThai() {
        return trangThai;
    }

    public void setTrangThai(TrangThaiPhienBan trangThai) {
        this.trangThai = trangThai;
    }

    public SanPham getSanPham() {
        return sanPham;
    }

    public void setSanPham(SanPham sanPham) {
        this.sanPham = sanPham;
    }

    public CumLapRap getGoc() {
        return goc;
    }

    public void setGoc(CumLapRap goc) {
        this.goc = goc;
    }

    public List<CauThanh> getCacCauThanh() {
        return cacCauThanh;
    }

    public void setCacCauThanh(List<CauThanh> cacCauThanh) {
        this.cacCauThanh = cacCauThanh;
    }

    public boolean choPhepSuaCauTruc() {
        return trangThai == TrangThaiPhienBan.NHAP;
    }
}
