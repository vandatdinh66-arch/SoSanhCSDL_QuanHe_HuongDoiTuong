package vn.edu.bom.oodb.model;

import com.objectdb.Index;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

/**
 * Canh cau thanh (cha, con, so luong) trong mot phien ban cu the.
 *
 * <p>Day la "quan he co thuoc tinh" cua mo hinh quan he, nhung trong ObjectDB
 * hai dau duoc luu bang tham chieu doi tuong truc tiep, khong phai khoa ngoai
 * nguyen. Nho vay tu mot cum co the di chuyen sang con bang
 * {@code cum.getCacQuanHeCha()} roi {@code quanHe.getCon()}.</p>
 */
@Entity
@Index(name = "idx_cau_thanh_pk", members = {"phienBan", "cha", "con"}, unique = true)
@Index(name = "idx_cau_thanh_cha", members = {"phienBan", "cha"})
@Index(name = "idx_cau_thanh_con", members = {"phienBan", "con"})
public class CauThanh {

    @Id
    @GeneratedValue
    private long id;

    @ManyToOne
    private PhienBan phienBan;

    @ManyToOne
    private CumLapRap cha;

    @ManyToOne
    private ThanhPhan con;

    private int soLuong;

    protected CauThanh() {
    }

    public CauThanh(PhienBan phienBan, CumLapRap cha, ThanhPhan con, int soLuong) {
        this.phienBan = phienBan;
        this.cha = cha;
        this.con = con;
        this.soLuong = soLuong;
    }

    public long getId() {
        return id;
    }

    public PhienBan getPhienBan() {
        return phienBan;
    }

    public void setPhienBan(PhienBan phienBan) {
        this.phienBan = phienBan;
    }

    public CumLapRap getCha() {
        return cha;
    }

    public void setCha(CumLapRap cha) {
        this.cha = cha;
    }

    public ThanhPhan getCon() {
        return con;
    }

    public void setCon(ThanhPhan con) {
        this.con = con;
    }

    public int getSoLuong() {
        return soLuong;
    }

    public void setSoLuong(int soLuong) {
        this.soLuong = soLuong;
    }
}
