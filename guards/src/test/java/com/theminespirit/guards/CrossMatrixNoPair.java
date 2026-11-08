package com.theminespirit.guards;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CrossMatrixNoPair {
    private static final Pattern ROW = Pattern.compile("^\\| (\\d+)-(\\d+) \\| ([^|]+) \\|");

    private CrossMatrixNoPair() {}

    static List<String> issues(String matrix, String noPair) {
        List<String> found = new ArrayList<>();
        Map<String, String> states = CrossMatrixDocument.states(matrix);
        List<String> seen = new ArrayList<>();
        for (Map<String, String> row : rows(noPair)) {
            String pair = row.get("pair");
            if (seen.contains(pair)) {
                found.add("пара " + pair + " в списке н/п описана дважды");
            }
            seen.add(pair);
            if (row.get("reason").length() < 8) {
                found.add("пара " + pair + " помечена н/п без внятного обоснования: " + row.get("reason"));
            }
            if (!"н/п".equals(states.get(pair))) {
                found.add("пара " + pair + " обоснована как н/п, но её клетка «" + states.getOrDefault(pair, "?")
                        + "»: обоснование относится к другой паре");
            }
        }
        for (Map.Entry<String, String> cell : states.entrySet()) {
            if ("н/п".equals(cell.getValue()) && !seen.contains(cell.getKey())) {
                found.add("клетка н/п пары " + cell.getKey()
                        + " без обоснования: непересекающаяся пара обязана назвать причину");
            }
        }
        return found;
    }

    private static List<Map<String, String>> rows(String text) {
        List<Map<String, String>> rows = new ArrayList<>();
        for (String line : text.lines().toList()) {
            Matcher matcher = ROW.matcher(line);
            if (!matcher.find()) {
                continue;
            }
            Map<String, String> row = new TreeMap<>();
            row.put("pair", matcher.group(1) + "-" + matcher.group(2));
            row.put("reason", matcher.group(3).trim());
            rows.add(row);
        }
        return rows;
    }
}
