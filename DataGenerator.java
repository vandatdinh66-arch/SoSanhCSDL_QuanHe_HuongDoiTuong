import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.*;

public class DataGenerator {

    public static void main(String[] args) {
        // Cấu hình tham số mặc định
        String scenario = "A"; // Kịch bản: A, B, C, D
        int size = 1000;       // Quy mô bản ghi THANH_PHAN: 1000, 10000, 100000
        long seed = 42L;       // Hạt giống ngẫu nhiên để tái lập dữ liệu
        boolean enableCycle = false; // Bật true để tạo dữ liệu chu trình test Trigger

        Random rand = new Random(seed);
        String outputDir = "data/scenarios/" + (enableCycle ? scenario + "_cycle" : scenario);
        new File(outputDir).mkdirs();

        System.out.printf("[*] Đang sinh dữ liệu Java: Kịch bản %s | Quy mô: %d | Seed: %d | Cycle: %b%n",
                scenario, size, seed, enableCycle);

        // 1. Sinh danh mục LOAI_THANH_PHAN
        List<String[]> loaiThanhPhan = Arrays.asList(
            new String[]{"LTP01", "Cụm Lắp Ráp Nguyên Chiếc"},
            new String[]{"LTP02", "Cụm Module Tải Nối"},
            new String[]{"LTP03", "Linh Kiện Cơ Khí"},
            new String[]{"LTP04", "Linh Kiện Điện Tử"}
        );

        // 2. Sinh dữ liệu THANH_PHAN và các bảng con kế thừa
        List<String[]> thanhPhanList = new ArrayList<>();
        List<String[]> cumLapRapList = new ArrayList<>();
        List<String[]> linhKienCoKhiList = new ArrayList<>();
        List<String[]> linhKienDienTuList = new ArrayList<>();

        List<String> cumIds = new ArrayList<>();
        List<String> lkIds = new ArrayList<>();

        int numCum = Math.max(1, (int) (size * 0.2)); // 20% tổng số làm Cụm Lắp Ráp
        String[] chatLieuArr = {"Nhôm Alloy", "Thép Không Gỉ", "Nhựa ABS", "Carbon Fiber"};
        String[] donViTinhArr = {"Cái", "Bộ", "Chiếc", "Hộp"};

        for (int i = 1; i <= size; i++) {
            String maTP = String.format("TP_%06d", i);
            String tenTP = "Thành Phần " + i;
            double donGia = Math.round((10.0 + (2000.0 - 10.0) * rand.nextDouble()) * 100.0) / 100.0;
            String donViTinh = donViTinhArr[rand.nextInt(donViTinhArr.length)];

            if (i <= numCum) {
                String maLoai = rand.nextBoolean() ? "LTP01" : "LTP02";
                thanhPhanList.add(new String[]{maTP, tenTP, maLoai, String.valueOf(donGia), donViTinh});
                cumLapRapList.add(new String[]{maTP});
                cumIds.add(maTP);
            } else {
                boolean isCoKhi = rand.nextBoolean();
                if (isCoKhi) {
                    String maLoai = "LTP03";
                    thanhPhanList.add(new String[]{maTP, tenTP, maLoai, String.valueOf(donGia), donViTinh});
                    String chatLieu = chatLieuArr[rand.nextInt(chatLieuArr.length)];
                    String kichThuoc = (rand.nextInt(46) + 5) + "x" + (rand.nextInt(46) + 5) + " mm";
                    linhKienCoKhiList.add(new String[]{maTP, chatLieu, kichThuoc});
                } else {
                    String maLoai = "LTP04";
                    thanhPhanList.add(new String[]{maTP, tenTP, maLoai, String.valueOf(donGia), donViTinh});
                    double dienAp = new double[]{5.0, 12.0, 19.0, 20.0}[rand.nextInt(4)];
                    double congSuat = new double[]{15.0, 45.0, 65.0, 100.0}[rand.nextInt(4)];
                    linhKienDienTuList.add(new String[]{maTP, String.valueOf(dienAp), String.valueOf(congSuat)});
                }
                lkIds.add(maTP);
            }
        }

        // 3. Sinh dữ liệu PHIEN_BAN_THIET_KE
        String rootCumId = cumIds.get(0);
        List<String[]> phienBanList = Arrays.asList(
            new String[]{"PB_v1.0", rootCumId, "Phiên bản Thương Mại v1.0", LocalDate.now().minusDays(365).toString()},
            new String[]{"PB_v2.0", rootCumId, "Phiên bản Nâng Cấp v2.0", LocalDate.now().minusDays(30).toString()}
        );

        // 4. Sinh dữ liệu QUAN_HE_CAU_THANH (Tạo cấu trúc DAG không chu trình)
        List<String[]> quanHeList = new ArrayList<>();
        int maxBranches = 4; // Cấu hình số con tối đa cho mỗi cụm

        for (String parentId : cumIds) {
            int parentIdx = Integer.parseInt(parentId.split("_")[1]);
            List<String> validChildren = new ArrayList<>();

            // gom tất cả các thành phần có index lớn hơn cha để tránh chu trình
            for (String cId : cumIds) {
                if (Integer.parseInt(cId.split("_")[1]) > parentIdx) validChildren.add(cId);
            }
            for (String lId : lkIds) {
                if (Integer.parseInt(lId.split("_")[1]) > parentIdx) validChildren.add(lId);
            }

            if (validChildren.isEmpty()) continue;

            int numChildren = rand.nextInt(maxBranches) + 1;
            Collections.shuffle(validChildren, rand);

            for (int k = 0; k < Math.min(numChildren, validChildren.size()); k++) {
                String childId = validChildren.get(k);
                int soLuong = rand.nextInt(10) + 1; // Ràng buộc SoLuong > 0
                String maPB = rand.nextBoolean() ? "PB_v1.0" : "PB_v2.0";
                quanHeList.add(new String[]{maPB, parentId, childId, String.valueOf(soLuong)});
            }
        }

        // Nếu bật flag enableCycle: cố ý chèn cặp A -> B và B -> A để test Trigger TRG_QHCT_CHONG_CHU_TRINH
        if (enableCycle && cumIds.size() >= 2) {
            String parentA = cumIds.get(0);
            String childB = cumIds.get(1);
            quanHeList.add(new String[]{"PB_v1.0", parentA, childB, "1"});
            quanHeList.add(new String[]{"PB_v1.0", childB, parentA, "1"});
            System.out.println("[!] Đã chèn dữ liệu chu trình ngẫu nhiên để test Trigger!");
        }

        // 5. Xuất file CSV
        writeCSV(outputDir + "/LOAI_THANH_PHAN.csv", new String[]{"MaLoai", "TenLoai"}, loaiThanhPhan);
        writeCSV(outputDir + "/THANH_PHAN.csv", new String[]{"MaThanhPhan", "TenThanhPhan", "MaLoai", "DonGia", "DonViTinh"}, thanhPhanList);
        writeCSV(outputDir + "/CUM_LAP_RAP.csv", new String[]{"MaThanhPhan"}, cumLapRapList);
        writeCSV(outputDir + "/LINH_KIEN_CO_KHI.csv", new String[]{"MaThanhPhan", "ChatLieu", "KichThuoc"}, linh_kien_co_khi_list_adapter(linhKienCoKhiList));
        writeCSV(outputDir + "/LINH_KIEN_DIEN_TU.csv", new String[]{"MaThanhPhan", "DienAp", "CongSuat"}, linhKienDienTuList);
        writeCSV(outputDir + "/PHIEN_BAN_THIET_KE.csv", new String[]{"MaPhienBan", "MaThanhPhanGoc", "TenPhienBan", "NgayTao"}, phienBanList);
        writeCSV(outputDir + "/QUAN_HE_CAU_THANH.csv", new String[]{"MaPhienBan", "MaCha", "MaCon", "SoLuong"}, quanHeList);

        System.out.println("[+] Hoàn tất xuất toàn bộ file CSV!");
    }

    private static List<String[]> linh_kien_co_khi_list_adapter(List<String[]> list) {
        return list;
    }

    private static void writeCSV(String filePath, String[] headers, List<String[]> data) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println(String.join(",", headers));
            for (String[] row : data) {
                writer.println(String.join(",", row));
            }
            System.out.println(" -> Đã ghi file: " + filePath);
        } catch (IOException e) {
            System.err.println("Lỗi khi ghi file " + filePath + ": " + e.getMessage());
        }
    }
}