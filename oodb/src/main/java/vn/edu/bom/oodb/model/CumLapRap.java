package vn.edu.bom.oodb.model;

import javax.persistence.Entity;
import javax.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;

/**
 * Cum lap rap (hoac san pham o goc). Chi cum moi duoc lam CHA trong CauThanh
 * (BR-06). Danh sach quan he cha duoc giu de di chuyen truc tiep
 * tu cum xuong cac thanh phan con ma khong can JOIN.
 */
@Entity
public class CumLapRap extends ThanhPhan {

    @OneToMany(mappedBy = "cha")
    private List<CauThanh> cacQuanHeCha = new ArrayList<>();

    protected CumLapRap() {
    }

    public CumLapRap(String ma, String ten, String moTa, String donViTinh, LoaiThanhPhan loai) {
        super(ma, ten, moTa, donViTinh, loai);
    }

    public List<CauThanh> getCacQuanHeCha() {
        return cacQuanHeCha;
    }

    public void setCacQuanHeCha(List<CauThanh> cacQuanHeCha) {
        this.cacQuanHeCha = cacQuanHeCha;
    }
}
