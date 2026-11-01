package vn.edu.bom.oodb.model;

import javax.persistence.Entity;
import java.math.BigDecimal;

@Entity
public class LinhKienCoKhi extends LinhKien {

    private String chatLieu;

    private String kichThuoc;

    protected LinhKienCoKhi() {
    }

    public LinhKienCoKhi(String ma, String ten, String moTa, String donViTinh,
                         LoaiThanhPhan loai, BigDecimal donGia,
                         String chatLieu, String kichThuoc) {
        super(ma, ten, moTa, donViTinh, loai, donGia);
        this.chatLieu = chatLieu;
        this.kichThuoc = kichThuoc;
    }

    public String getChatLieu() {
        return chatLieu;
    }

    public void setChatLieu(String chatLieu) {
        this.chatLieu = chatLieu;
    }

    public String getKichThuoc() {
        return kichThuoc;
    }

    public void setKichThuoc(String kichThuoc) {
        this.kichThuoc = kichThuoc;
    }
}
