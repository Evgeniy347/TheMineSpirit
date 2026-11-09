package com.theminespirit.guards;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CrossMatrixDocument {
    static final Path DOC = Path.of("docs/testing/CROSS-MATRIX.md");
    static final Path NO_PAIR = Path.of("docs/testing/CROSS-MATRIX-NO-PAIR.md");
    static final int MECHANICS = 24;
    static final int NEW_PAIRS_MIN = 30;
    private static final Pattern REGISTRY = Pattern.compile("^\\| (M\\d{2}) \\|");
    private static final Pattern MATRIX_HEADER = Pattern.compile("^\\| \\| (M\\d{2})");
    private static final Pattern MATRIX_ROW = Pattern.compile("^\\| (M\\d{2}) \\|");
    private static final Pattern DETAIL = Pattern.compile("^\\| ((?:\\d+-\\d+)(?:, \\d+-\\d+)*) \\| `([^`]+)` \\|");
    private static final Pattern TEST_REF = Pattern.compile("`([A-Z][A-Za-z0-9]*Tests)\\.([A-Za-z0-9_]+)`");
    private static final Set<String> STATES = Set.of("Е", "Ч", "Н", "н/п", "—");

    private CrossMatrixDocument() {}

    static List<String> check(Map<String, String> sources) {
        String matrix = read(DOC);
        List<String> found = new ArrayList<>(violations(matrix, sources));
        found.addAll(CrossMatrixNoPair.issues(matrix, read(NO_PAIR)));
        return found;
    }

    static List<String> violations(String text, Map<String, String> sources) {
        List<String> found = new ArrayList<>(issues(text, sources));
        List<String> registry = registry(text);
        if (registry.size() != MECHANICS) {
            found.add("в реестре " + registry.size() + " механик вместо " + MECHANICS);
        }
        List<String> pairs = detailPairs(text);
        if (pairs.size() < NEW_PAIRS_MIN) {
            found.add("пар с общим тестом " + pairs.size() + " меньше " + NEW_PAIRS_MIN);
        }
        long cross = details(text).stream()
                .filter(row -> row.get("test").startsWith("Cross"))
                .count();
        if (cross < NEW_PAIRS_MIN) {
            found.add("новых парных сценариев " + cross + " меньше " + NEW_PAIRS_MIN);
        }
        for (String pair : pairs) {
            boolean known = details(text).stream().anyMatch(row -> pair.equals(row.get("pair")));
            if (!known) {
                found.add("клетка Е пары " + pair + " без строки в таблице пар");
            }
        }
        return found;
    }

    static List<String> issues(String text, Map<String, String> sources) {
        List<String> found = new ArrayList<>();
        List<String> ids = registry(text);
        List<String> header = header(text);
        if (!ids.equals(header)) {
            found.add("шапка матрицы " + header + " не совпадает с реестром " + ids);
        }
        Map<String, List<String>> rows = matrix(text);
        for (String id : ids) {
            List<String> cells = rows.get(id);
            if (cells == null || cells.size() != ids.size()) {
                found.add("строка матрицы " + id + " не покрывает все механики: " + cells);
                continue;
            }
            for (int column = 0; column < cells.size(); column++) {
                if (!STATES.contains(cells.get(column))) {
                    found.add("клетка " + id + "/" + ids.get(column) + " = «" + cells.get(column) + "» вне Е/Ч/Н");
                }
            }
        }
        for (int row = 0; row < ids.size(); row++) {
            for (int column = row + 1; column < ids.size(); column++) {
                List<String> first = rows.getOrDefault(ids.get(row), List.of());
                List<String> second = rows.getOrDefault(ids.get(column), List.of());
                if (first.size() <= column || second.size() <= row) {
                    continue;
                }
                if (!first.get(column).equals(second.get(row))) {
                    found.add("пара (" + ids.get(row) + "," + ids.get(column) + ") несимметрична: " + first.get(column)
                            + " и " + second.get(row));
                }
            }
        }
        Set<String> known = new HashSet<>();
        for (Map<String, String> detail : details(text)) {
            String pair = detail.get("pair");
            if (!known.add(pair)) {
                found.add("пара " + pair + " описана дважды");
            }
            String test = detail.get("test");
            if (!refProblem(test, sources).isEmpty()) {
                found.add("ссылка `" + test + "` ведёт на несуществующий тест: " + refProblem(test, sources));
            }
            if (!"Е".equals(states(text).getOrDefault(pair, ""))) {
                found.add("пара " + pair + " есть в таблице пар, но её клетка не Е");
            }
            if (detail.get("graph").isBlank()) {
                found.add("пара " + pair + " не говорит, что дал граф");
            }
            if (detail.get("mechanic").isBlank()) {
                found.add("пара " + pair + " не говорит, что сделала механика");
            }
            if (!Set.of("ОК", "НЕ ОК").contains(detail.get("mark"))) {
                found.add("пара " + pair + " без отметки ОК/НЕ ОК: " + detail.get("mark"));
            }
            if (!Set.of("полный", "слабо").contains(detail.get("strength"))) {
                found.add("пара " + pair + " не размечена силой полный/слабо: " + detail.get("strength"));
            }
        }
        for (Map.Entry<String, String> cell : states(text).entrySet()) {
            if ("Е".equals(cell.getValue())
                    && details(text).stream().noneMatch(row -> cell.getKey().equals(row.get("pair")))) {
                found.add("клетка Е пары " + cell.getKey() + " без строки в таблице пар");
            }
        }
        for (String ref : references(text)) {
            if (!refProblem(ref, sources).isEmpty()) {
                found.add("ссылка `" + ref + "` ведёт на несуществующий тест: " + refProblem(ref, sources));
            }
        }
        return found;
    }

    private static String refProblem(String ref, Map<String, String> sources) {
        String[] parts = ref.split("\\.", 2);
        if (parts.length != 2) {
            return "ожидали Class.method";
        }
        String source = sources.get(parts[0]);
        if (source == null) {
            return "нет класса " + parts[0] + "Tests";
        }
        Pattern method = Pattern.compile("void\\s+" + Pattern.quote(parts[1]) + "\\s*\\(");
        return method.matcher(source).find() ? "" : "нет метода " + parts[1];
    }

    private static List<String> references(String text) {
        List<String> refs = new ArrayList<>();
        Matcher matcher = TEST_REF.matcher(text);
        while (matcher.find()) {
            refs.add(matcher.group(1) + "." + matcher.group(2));
        }
        return refs;
    }

    private static List<String> registry(String text) {
        List<String> ids = new ArrayList<>();
        boolean section = false;
        for (String line : text.lines().toList()) {
            if (line.startsWith("## Реестр механик")) {
                section = true;
                continue;
            }
            if (line.startsWith("## ")) {
                section = false;
            }
            if (!section) {
                continue;
            }
            Matcher matcher = REGISTRY.matcher(line);
            if (matcher.find()) {
                ids.add(matcher.group(1));
            }
        }
        return ids;
    }

    private static List<String> header(String text) {
        List<String> ids = new ArrayList<>();
        boolean after = false;
        for (String line : text.lines().toList()) {
            if (!after && MATRIX_HEADER.matcher(line).find()) {
                after = true;
            }
            if (after && MATRIX_HEADER.matcher(line).find()) {
                ids.addAll(cells(line));
                break;
            }
        }
        return ids;
    }

    private static Map<String, List<String>> matrix(String text) {
        Map<String, List<String>> rows = new LinkedHashMap<>();
        boolean body = false;
        for (String line : text.lines().toList()) {
            if (MATRIX_HEADER.matcher(line).find()) {
                body = true;
                continue;
            }
            if (!body) {
                continue;
            }
            Matcher matcher = MATRIX_ROW.matcher(line);
            if (!matcher.find()) {
                if (!line.startsWith("|")) {
                    break;
                }
                continue;
            }
            rows.put(matcher.group(1), cells(line));
        }
        return rows;
    }

    private static List<String> cells(String line) {
        String[] parts = line.split("\\|", -1);
        List<String> cells = new ArrayList<>();
        for (int index = 2; index < parts.length - 1; index++) {
            cells.add(parts[index].trim());
        }
        return cells;
    }

    private static List<Map<String, String>> details(String text) {
        List<Map<String, String>> rows = new ArrayList<>();
        for (String line : text.lines().toList()) {
            Matcher matcher = DETAIL.matcher(line);
            if (!matcher.find()) {
                continue;
            }
            String[] parts = line.split("\\|", -1);
            for (String pair : matcher.group(1).split(", ", -1)) {
                Map<String, String> row = new TreeMap<>();
                row.put("pair", pair);
                row.put("test", matcher.group(2));
                row.put("graph", parts.length > 3 ? parts[3].trim() : "");
                row.put("mechanic", parts.length > 4 ? parts[4].trim() : "");
                row.put("mark", parts.length > 5 ? parts[5].trim() : "");
                row.put("strength", parts.length > 6 ? parts[6].trim() : "");
                rows.add(row);
            }
        }
        return rows;
    }

    private static List<String> detailPairs(String text) {
        List<String> pairs = new ArrayList<>();
        for (Map<String, String> row : details(text)) {
            pairs.add(row.get("pair"));
        }
        return pairs;
    }

    static Map<String, String> states(String text) {
        Map<String, String> states = new TreeMap<>();
        for (Map.Entry<String, List<String>> row : matrix(text).entrySet()) {
            for (int index = 0; index < row.getValue().size(); index++) {
                int mechanic = Integer.parseInt(row.getKey().substring(1));
                String pair = Math.min(mechanic, index + 1) + "-" + Math.max(mechanic, index + 1);
                states.merge(
                        pair,
                        row.getValue().get(index),
                        (first, second) -> "Е".equals(first) || "Е".equals(second) ? "Е" : first);
            }
        }
        return states;
    }

    static String baseDocument() {
        List<String> lines = new ArrayList<>();
        lines.add("## Реестр механик");
        lines.add("| M01 | a |");
        lines.add("| M02 | b |");
        lines.add("");
        lines.add("## Матрица пар");
        lines.add("| | M01 | M02 |");
        lines.add("|---|---|---|");
        lines.add("| M01 | — | Н |");
        lines.add("| M02 | Н | — |");
        lines.add("");
        lines.add("| пара | тест | граф дал | механика сделала | отметка | сила |");
        lines.add("|---|---|---|---|---|---|");
        lines.add("| 1-2 | `ATests.ok` | граф | механика | ОК | полный |");
        return String.join("\n", lines) + "\n";
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Сторож не смог прочитать " + path, e);
        }
    }
}
