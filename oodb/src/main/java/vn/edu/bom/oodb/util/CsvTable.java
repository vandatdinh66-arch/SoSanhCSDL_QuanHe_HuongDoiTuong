package vn.edu.bom.oodb.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bang CSV da doc, ho tro truy cap theo ten cot va bo qua dong tieu de.
 */
public final class CsvTable {

    private final List<String> headers;
    private final Map<String, Integer> index;
    private final List<String[]> rows;

    public CsvTable(List<String> headers, List<String[]> rows) {
        this.headers = List.copyOf(headers);
        this.index = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            index.put(headers.get(i), i);
        }
        this.rows = List.copyOf(rows);
    }

    public List<String> headers() {
        return headers;
    }

    public List<String[]> rows() {
        return rows;
    }

    public int size() {
        return rows.size();
    }

    public String get(String[] row, String column) {
        Integer i = index.get(column);
        if (i == null) {
            throw new IllegalArgumentException("Khong co cot '" + column + "', cac cot hien co: " + headers);
        }
        return i < row.length ? row[i] : "";
    }

    public int columnIndex(String column) {
        Integer i = index.get(column);
        if (i == null) {
            throw new IllegalArgumentException("Khong co cot '" + column + "', cac cot hien co: " + headers);
        }
        return i;
    }

    public static CsvTable empty() {
        return new CsvTable(new ArrayList<>(), new ArrayList<>());
    }
}
