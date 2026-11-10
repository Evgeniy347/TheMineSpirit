package com.theminespirit.guards;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("StringConcatToTextBlock")
final class CrossMatrixInterrupt {
    static final Path DOC = Path.of("docs/testing/CROSS-MATRIX-INTERRUPT.md");
    static final int MIN = 25;
    private static final Set<String> MECHANICS = Set.of("столб", "мост", "расчистка", "шахта", "dig_out");
    private static final Pattern ROW = Pattern.compile("^\\| (I\\d{2}) \\|");
    private static final Set<String> MARKS = Set.of("ОК", "Н");

    private CrossMatrixInterrupt() {}

    static List<String> issues(String text, Map<String, String> sources) {
        List<String> found = new ArrayList<>();
        List<Map<String, String>> rows = rows(text);
        if (rows.size() < MIN) {
            found.add("пар оси прерывания " + rows.size() + " меньше " + MIN);
        }
        Set<String> seen = new HashSet<>();
        for (Map<String, String> row : rows) {
            String id = row.get("id");
            if (!seen.add(id)) {
                found.add("пара " + id + " оси прерывания описана дважды");
            }
            if (!MECHANICS.contains(row.get("mechanic"))) {
                found.add(
                        "механика «" + row.get("mechanic") + "» оси прерывания вне столб/мост/расчистка/шахта/dig_out");
            }
            String mark = row.get("mark");
            if (!MARKS.contains(mark)) {
                found.add("пара " + id + " оси прерывания без отметки ОК/Н: " + mark);
            }
            if ("ОК".equals(mark)) {
                String ref = row.get("test").replace("`", "").strip();
                if (!CrossMatrixDocument.refProblem(ref, sources).isEmpty()) {
                    found.add("пара " + id + " оси прерывания ОК без существующего теста: "
                            + CrossMatrixDocument.refProblem(ref, sources));
                }
            }
            if ("Н".equals(mark) && row.get("test").isBlank()) {
                found.add("пара " + id + " оси прерывания Н без задачи починки");
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
            String[] parts = line.split("\\|", -1);
            if (parts.length < 8) {
                continue;
            }
            Map<String, String> row = new TreeMap<>();
            row.put("id", matcher.group(1));
            row.put("mechanic", parts[2].trim());
            row.put("interruption", parts[3].trim());
            row.put("test", parts[7].trim());
            row.put("mark", parts[6].trim());
            rows.add(row);
        }
        return rows;
    }
}
