package vn.edu.bom.oodb.dto;

import java.util.Map;

/**
 * Ket qua so sanh hai phien ban (N8).
 *
 * @param themMoi    cac canh chi co o phien ban moi (cha|con -> so luong)
 * @param biXoa      cac canh chi co o phien ban cu (cha|con -> so luong)
 * @param doiSoLuong cac canh co o ca hai nhung khac so luong (cha|con -> [cu, moi])
 */
public record VersionDiff(
        Map<String, Integer> themMoi,
        Map<String, Integer> biXoa,
        Map<String, int[]> doiSoLuong) {
}
