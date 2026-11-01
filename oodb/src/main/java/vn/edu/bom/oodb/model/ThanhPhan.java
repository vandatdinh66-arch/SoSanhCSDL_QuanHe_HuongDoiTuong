package vn.edu.bom.oodb.model;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;

/**
 * Lop truu tuong cho moi thanh phan trong BOM.
 *
 * <p>Chuyen biet hoa roi nhau, toan phan thanh:
 * {@link CumLapRap}, {@link LinhKienCoKhi}, {@link LinhKienDienTu}.
 * Trong mo hinh doi tuong, quan he cau thanh la tham chieu truc tiep
 * ({@link CauThanh#getCon()}) chu khong phai khoa ngoai + JOIN.</p>
 */
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
public abstract class ThanhPhan {

    @Id
    private String ma;

    private String ten;

    private String moTa;

    private String donViTinh;

    @ManyToOne
    private LoaiThanhPhan loai;

    /**
     * Cac quan he ma thanh phan nay la CON. Dung cho truy van where-used
     * (N5) va kiem tra chu trinh (BR-10).
     */
    @OneToMany(mappedBy = "con")
    private List<CauThanh> duocDungBoi = new ArrayList<>();

    protected ThanhPhan() {
    }

    protected ThanhPhan(String ma, String ten, String moTa, String donViTinh, LoaiThanhPhan loai) {
        this.ma = ma;
        this.ten = ten;
        this.moTa = moTa;
        this.donViTinh = donViTinh;
        this.loai = loai;
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

    public String getDonViTinh() {
        return donViTinh;
    }

    public void setDonViTinh(String donViTinh) {
        this.donViTinh = donViTinh;
    }

    public LoaiThanhPhan getLoai() {
        return loai;
    }

    public void setLoai(LoaiThanhPhan loai) {
        this.loai = loai;
    }

    public List<CauThanh> getDuocDungBoi() {
        return duocDungBoi;
    }

    public void setDuocDungBoi(List<CauThanh> duocDungBoi) {
        this.duocDungBoi = duocDungBoi;
    }
}
