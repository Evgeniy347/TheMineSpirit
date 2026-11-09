package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ScriptsMapGuardTests {
    private static final Path README = Path.of("scripts/README.md");
    private static final Path SCRIPTS = Path.of("scripts");
    private static final Pattern LINK = Pattern.compile("\\[[^\\]]*\\]\\(([^)]+)\\)");

    @Test
    void scriptsMap_EveryScript_HasRowAndExample() {
        List<String> problems = new ArrayList<>();
        for (Path file : scriptFiles()) {
            String relative = SCRIPTS.relativize(file).toString().replace('\\', '/');
            Row row = find(relative);
            if (row == null) {
                problems.add(relative + ": нет строки в таблице scripts/README.md");
                continue;
            }
            if (row.cells.size() < 5) {
                problems.add(relative + ": в строке " + row.cells.size() + " столбцов, нужно 5");
                continue;
            }
            if (row.cells.get(2).isBlank() || "—".equals(row.cells.get(2))) {
                problems.add(relative + ": нет примера запуска");
            }
            if (row.cells.get(3).isBlank() || "—".equals(row.cells.get(3))) {
                problems.add(relative + ": не сказано, где работает");
            }
        }
        assertEquals(
                List.of(),
                problems,
                "Карта scripts/README.md должна знать каждый скрипт и давать пример запуска: иначе исполнитель "
                        + "пишет разовый скрипт заново. Как надо: строка таблицы с ссылкой на файл, примером, "
                        + "местом работы и ответом «меняет ли что-либо». Проблемы");
    }

    @Test
    void mapRows_RowWithoutExample_IsReported() {
        Row row = Row.parse("| [x](x.sh) | делает | | ПК | нет |");

        assertEquals(
                List.of("x.sh: нет примера запуска"),
                problemsFor(List.of("x.sh"), List.of(row)),
                "Сторож ослеп: строка без примера запуска обязана попасть в проблемы");
    }

    @Test
    void mapRows_DirectoryRow_IsSkipped() {
        assertEquals(
                List.of(),
                problemsFor(List.of("lead/x.sh"), List.of(Row.parse("| [lead/](lead/) | каталог | — | ПК | нет |"))),
                "Сторож ложно срабатывает: файл под названным каталогом не обязан иметь отдельную строку");
    }

    private static List<String> problemsFor(List<String> files, List<Row> rows) {
        List<String> problems = new ArrayList<>();
        for (String file : files) {
            Row row = rows.stream()
                    .filter(candidate -> candidate.path != null
                            && (candidate.path.equals(file)
                                    || file.startsWith(
                                            candidate.path.endsWith("/") ? candidate.path : candidate.path + "/")))
                    .findFirst()
                    .orElse(null);
            if (row == null) {
                problems.add(file + ": нет строки в таблице scripts/README.md");
            } else if (row.cells.get(2).isBlank()) {
                problems.add(file + ": нет примера запуска");
            }
        }
        return problems;
    }

    private static Row find(String relative) {
        for (Row row : rows()) {
            if (row.path != null
                    && (row.path.equals(relative)
                            || relative.startsWith(row.path.endsWith("/") ? row.path : row.path + "/"))) {
                return row;
            }
        }
        return null;
    }

    private static List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(README, StandardCharsets.UTF_8)) {
                if (line.startsWith("|")) {
                    rows.add(Row.parse(line));
                }
            }
        } catch (IOException error) {
            throw new IllegalStateException(
                    "Карта скриптов '" + README + "' не читается: " + error.getMessage(), error);
        }
        return rows;
    }

    private static Set<Path> scriptFiles() {
        Set<Path> files = new LinkedHashSet<>();
        try (Stream<Path> walk = Files.walk(SCRIPTS)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> !path.equals(README))
                    .filter(path -> !path.toString().replace('\\', '/').contains("/logdb/fixtures/"))
                    .filter(path -> !path.toString().replace('\\', '/').contains("__pycache__"))
                    .forEach(files::add);
        } catch (IOException error) {
            throw new IllegalStateException("Сторож не собрал скрипты: " + error.getMessage(), error);
        }
        return files;
    }

    private record Row(String path, List<String> cells) {
        static Row parse(String line) {
            String text = line.trim();
            if (text.endsWith("|")) {
                text = text.substring(0, text.length() - 1);
            }
            if (text.startsWith("|")) {
                text = text.substring(1);
            }
            List<String> cells = new ArrayList<>();
            for (String cell : text.split("\\|", -1)) {
                cells.add(cell.trim());
            }
            Matcher link = LINK.matcher(cells.isEmpty() ? "" : cells.get(0));
            return new Row(link.find() ? link.group(1) : null, List.copyOf(cells));
        }
    }
}
