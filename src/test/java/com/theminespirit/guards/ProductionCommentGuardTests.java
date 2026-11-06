package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ProductionCommentGuardTests {
    private static final Path BASELINE = Path.of("config/comment-baseline.txt");
    private static final int BASELINE_LIMIT = 164;
    private static final Set<String> SKIPPED_DIRECTORIES = Set.of(
            ".git", ".gradle", ".idea", ".vs", ".codegraph", "build", "TestResults", "temp-scripts", "run", "run-data");
    private static final Pattern RESTRICTION_LABEL = Pattern.compile("^(?:mc|lib)\\s*:");

    @Test
    void codeSources_CommentsRemain_AreAbsent() {
        Map<String, Integer> baseline = readBaseline();
        List<String> offenders = new ArrayList<>();
        for (Comment comment : allFindings()) {
            String key = key(comment.file(), comment.body());
            Integer left = baseline.getOrDefault(key, 0);
            if (left > 0) {
                baseline.put(key, left - 1);
            } else {
                offenders.add(comment.file() + ":" + comment.line() + " — «" + comment.body() + "»");
            }
        }
        List<String> stale = baseline.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> entry.getValue() + "× " + entry.getKey())
                .sorted()
                .toList();
        assertEquals(
                List.of(),
                offenders,
                "Комментарий в коде пересказывает то, что говорит имя, или прячет причину, которая протухает после "
                        + "первой правки. Как надо: напиши поясняющий быстрый тест, а комментарий удали; причина-"
                        + "ограничение Minecraft/библиотеки — только с меткой `// mc:` или `// lib:` "
                        + "(docs/CONVENTIONS.md → «Комментарии»). Нарушители (файл:строка)");
        assertEquals(
                List.of(),
                stale,
                "Список исключений config/comment-baseline.txt содержит записи, которых в коде уже нет: "
                        + "он только уменьшается. Удали названные строки — иначе исключение прикрывает будущий "
                        + "комментарий с тем же текстом. Остаток списка");
    }

    @Test
    void baseline_EntryCount_DoesNotGrow() {
        int entries =
                readBaseline().values().stream().mapToInt(Integer::intValue).sum();

        assertTrue(
                entries <= BASELINE_LIMIT,
                "Список исключений config/comment-baseline.txt только уменьшается, сейчас записей " + entries
                        + " при потолке " + BASELINE_LIMIT + " (docs/CONVENTIONS.md → «Комментарии»). "
                        + "Новый комментарий добавлением строки в список не легализуется: напиши поясняющий "
                        + "быстрый тест, а комментарий удали. Список стал меньше — уменьши BASELINE_LIMIT.");
    }

    @Test
    void commentScanner_JavaLineBlockAndJavadoc_AreReported() {
        String sample = """
                class Sample {
                    // пересказ поля
                    /* пересказ метода */
                    /** Документация члена. */
                    private int value;
                }
                """;
        assertEquals(
                List.of("2 LINE", "3 BLOCK", "4 JAVADOC"),
                findings(scan(sample, Language.JAVA)).stream()
                        .map(comment -> comment.line() + " " + comment.kind())
                        .toList(),
                "Сторож ослеп: строчный комментарий, блочный комментарий и Javadoc обязаны находиться");
    }

    @Test
    void commentScanner_RestrictionLabelsAndTailComment_AreSeparated() {
        String sample = """
                        final class Sample {
                            // mc: ванильный тик — 20 раз в секунду, иначе скорость духа не совпадёт с игроком
                            //     тик духа идёт с той же частотой
                            // lib: Jackson пишет поле только с этой аннотацией
                            /* lib: BlockState не обновляет соседей сам */
                            private int value; // хвостовой пересказ
                        }
                        """;

        assertEquals(
                List.of("6 LINE"),
                findings(scan(sample, Language.JAVA)).stream()
                        .map(comment -> comment.line() + " " + comment.kind())
                        .toList(),
                "Сторож ослеп или ложно срабатывает: метки mc:/lib: с продолжением разрешены, "
                        + "а обычный хвостовой комментарий — нет");
    }

    @Test
    void commentScanner_CommentsInsideStrings_AreIgnored() {
        String sample = String.join(
                "\n",
                "class Sample {",
                "    String url = \"https://example.org/a//b#c\";",
                "    String quote = \"знак \\\"//\\\" внутри строки\";",
                "    String block = \"\"\"",
                "            // не комментарий",
                "            /* не комментарий */",
                "            \"\"\";",
                "    char hash = '#';",
                "}",
                "");

        assertEquals(
                List.of(),
                findings(scan(sample, Language.JAVA)).stream()
                        .map(comment -> comment.line() + " " + comment.kind())
                        .toList(),
                "Сторож ложно срабатывает: `//`, `/*` и `#` внутри строки, текстового блока и символьного "
                        + "литерала — не комментарии");
    }

    @Test
    void commentScanner_ShellCommentsAndShebang_AreReportedExceptShebang() {
        String sample = """
                #!/usr/bin/env bash
                echo "не #комментарий"
                echo 'не #комментарий'
                PATH="${PATH#*:}"
                echo $#
                # пересказ шага
                cat <<'EOF'
                #!/usr/bin/env bash
                # комментарий внутри heredoc
                EOF
                """;

        assertEquals(
                List.of("6 LINE", "9 LINE"),
                findings(scan(sample, Language.SHELL)).stream()
                        .map(comment -> comment.line() + " " + comment.kind())
                        .toList(),
                "Сторож ослеп или ложно срабатывает: shebang и `#` в кавычках/подстановке — не комментарии, "
                        + "а строковый и heredoc-комментарии обязаны находиться");
    }

    @Test
    void commentScanner_PythonCommentsAndTripleStrings_AreReported() {
        String sample = String.join(
                "\n",
                "#!/usr/bin/env python3",
                "text = \"# не комментарий\"",
                "block = \"\"\"",
                "# не комментарий",
                "\"\"\"",
                "# пересказ шага",
                "value = 1  # хвостовой пересказ",
                "");

        assertEquals(
                List.of("6 LINE", "7 LINE"),
                findings(scan(sample, Language.PYTHON)).stream()
                        .map(comment -> comment.line() + " " + comment.kind())
                        .toList(),
                "Сторож ослеп или ложно срабатывает: `#` в строке и тройной строке — не комментарий, "
                        + "а строковый и хвостовой комментарии обязаны находиться");
    }

    @Test
    void scannedPaths_DataDocsAndGenerated_AreSkipped() {
        List<String> skipped = List.of(
                "src/main/resources/data/theminespirit/schematic/house.nbt",
                "src/main/resources/data/theminespirit/lang/ru_ru.json",
                "docs/CONVENTIONS.md",
                "build/generated/sources/Foo.java",
                "src/generated/data/Foo.java",
                "run/world/level.dat",
                "TestResults/task.md");
        List<String> scanned = List.of(
                "mod/src/main/java/com/theminespirit/TheMineSpirit.java",
                "src/test/java/com/theminespirit/guards/SampleTests.java",
                "api/src/main/java/com/theminespirit/core/board/BoardCase.java",
                "api/src/test/java/com/theminespirit/core/board/BoardCaseTests.java",
                "build.gradle",
                "settings.gradle.kts",
                "scripts/test.sh",
                "scripts/testing/check-test-runner.sh",
                "agents/strategist/brain.py",
                "scripts/lead/keepawake.ps1");

        assertEquals(
                List.of(),
                skipped.stream().filter(path -> isScanned(Path.of(path))).toList(),
                "Сторож придирается к данным, документации или сгенерированному коду: их правят руками, "
                        + "а правило «комментариев нет» — только к исходникам кода");
        assertEquals(
                List.of(),
                scanned.stream().filter(path -> !isScanned(Path.of(path))).toList(),
                "Сторож ослеп: исходники кода Java, Gradle, скриптов и Python обязаны попадать в проверку");
    }

    @Test
    void baseline_NewAndStaleEntries_AreReported() {
        Map<String, Integer> budget = new HashMap<>();
        budget.put("A.java\tстарый пересказ", 1);
        budget.put("B.java\tстарый пересказ", 1);
        List<Comment> current = List.of(
                new Comment("A.java", "старый пересказ", 1, "LINE", true),
                new Comment("A.java", "новый пересказ", 2, "LINE", true));

        List<String> offenders = new ArrayList<>();
        for (Comment comment : current) {
            String key = key(comment.file(), comment.body());
            Integer left = budget.getOrDefault(key, 0);
            if (left > 0) {
                budget.put(key, left - 1);
            } else {
                offenders.add(comment.file() + ":" + comment.line());
            }
        }

        assertEquals(
                List.of("A.java:2"),
                offenders,
                "Сторож ослеп: комментарий, которого нет в списке исключений, обязан быть нарушителем");
        assertEquals(
                List.of(1),
                budget.values().stream().filter(left -> left > 0).toList(),
                "Сторож ослеп: запись списка без комментария в коде обязана считаться устаревшей");
    }

    private static List<Comment> allFindings() {
        List<Comment> findings = new ArrayList<>();
        codeSources().forEach((file, text) -> findings.addAll(findings(scan(text, languageOf(file)), file)));
        return findings;
    }

    private static List<Comment> findings(List<Comment> scanned) {
        return findings(scanned, "");
    }

    private static List<Comment> findings(List<Comment> scanned, String file) {
        List<Comment> findings = new ArrayList<>();
        Comment previous = null;
        boolean runAllowed = false;
        for (Comment comment : scanned) {
            boolean abuts = previous != null
                    && comment.line() == previous.line() + 1
                    && "LINE".equals(comment.kind())
                    && "LINE".equals(previous.kind())
                    && comment.fullLine()
                    && previous.fullLine();
            if (!abuts || !"LINE".equals(comment.kind())) {
                runAllowed = RESTRICTION_LABEL.matcher(comment.body()).find();
            }
            boolean allowed = RESTRICTION_LABEL.matcher(comment.body()).find()
                    || (runAllowed && "LINE".equals(comment.kind()) && comment.fullLine());
            if (!allowed) {
                findings.add(file.isEmpty() ? comment : comment.withFile(file));
            }
            previous = comment;
        }
        return findings;
    }

    private static Map<String, Integer> readBaseline() {
        if (!Files.isRegularFile(BASELINE)) {
            return new HashMap<>();
        }
        Map<String, Integer> baseline = new HashMap<>();
        try {
            for (String line : Files.readAllLines(BASELINE, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) {
                    baseline.merge(line, 1, Integer::sum);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Сторож не смог прочитать список исключений " + BASELINE, e);
        }
        return baseline;
    }

    private static String key(String file, String body) {
        return SourceFiles.logical(Path.of(file)) + "\t" + body;
    }

    private static Map<String, String> codeSources() {
        Map<String, String> sources = new TreeMap<>();
        try (Stream<Path> files = Files.walk(Path.of("."))) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                if (isScanned(file)) {
                    sources.put(relative(file), Files.readString(file, StandardCharsets.UTF_8));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Сторож не смог обойти исходники проекта", e);
        }
        return sources;
    }

    private static boolean isScanned(Path path) {
        String normalized = path.toString().replace('\\', '/');
        if (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        for (String segment : lower.split("/", -1)) {
            if (SKIPPED_DIRECTORIES.contains(segment)) {
                return false;
            }
            if ("generated".equals(segment) || "src/main/resources".equals(segment) || "run-live".equals(segment)) {
                return false;
            }
        }
        if (lower.contains("/src/main/resources/")) {
            return false;
        }
        String name = path.getFileName().toString();
        if (name.endsWith(".java")) {
            return SourceFiles.isSourcePath(normalized);
        }
        if (name.endsWith(".gradle") || name.endsWith(".kts")) {
            return true;
        }
        if (name.endsWith(".sh")) {
            return normalized.startsWith("scripts/");
        }
        return name.endsWith(".py") || name.endsWith(".ps1");
    }

    private static String relative(Path file) {
        return Path.of(".").relativize(file).toString().replace('\\', '/');
    }

    private static Language languageOf(String file) {
        if (file.endsWith(".sh") || file.endsWith(".ps1")) {
            return Language.SHELL;
        }
        if (file.endsWith(".py")) {
            return Language.PYTHON;
        }
        return Language.JAVA;
    }

    private static List<Comment> scan(String text, Language language) {
        return switch (language) {
            case JAVA -> scanJava(text);
            case SHELL -> scanShell(text);
            case PYTHON -> scanPython(text);
        };
    }

    private static List<Comment> scanJava(String text) {
        List<Comment> found = new ArrayList<>();
        int index = 0;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (current == '"' || current == '\'') {
                index = skipQuoted(text, index, current);
            } else if (text.startsWith("//", index)) {
                int end = text.indexOf('\n', index);
                end = end < 0 ? text.length() : end;
                add(found, text, index, end, "LINE", 2, 0);
                index = end;
            } else if (text.startsWith("/*", index)) {
                int end = text.indexOf("*/", index + 2);
                end = end < 0 ? text.length() : end + 2;
                add(found, text, index, end, text.startsWith("/**", index) ? "JAVADOC" : "BLOCK", 2, 2);
                index = end;
            } else {
                index++;
            }
        }
        return found;
    }

    private static List<Comment> scanPython(String text) {
        List<Comment> found = new ArrayList<>();
        int index = 0;
        while (index < text.length()) {
            String delimiter = tripleQuoteAt(text, index);
            if (delimiter != null) {
                index = skipTripleQuoted(text, index, delimiter);
            } else {
                char current = text.charAt(index);
                if (current == '"' || current == '\'') {
                    index = skipQuoted(text, index, current);
                } else if (current == '#' && !(index == 0 && text.startsWith("#!"))) {
                    int end = text.indexOf('\n', index);
                    end = end < 0 ? text.length() : end;
                    add(found, text, index, end, "LINE", 1, 0);
                    index = end;
                } else {
                    index++;
                }
            }
        }
        return found;
    }

    private static List<Comment> scanShell(String text) {
        List<Comment> found = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        char quote = 0;
        String heredoc = null;
        boolean stripTabs = false;
        for (int number = 1; number <= lines.length; number++) {
            String line = lines[number - 1];
            if (heredoc != null) {
                String candidate = stripTabs ? line.stripLeading() : line;
                if (candidate.equals(heredoc)) {
                    heredoc = null;
                } else {
                    scanShellLine(line, number, found);
                }
                continue;
            }
            quote = scanShellLine(line, number, found, quote);
            String[] pending = heredocOf(line);
            if (pending != null) {
                heredoc = pending[0];
                stripTabs = "-".equals(pending[1]);
            }
        }
        return found;
    }

    private static char scanShellLine(String line, int number, List<Comment> found) {
        return scanShellLine(line, number, found, (char) 0);
    }

    private static char scanShellLine(String line, int number, List<Comment> found, char initialQuote) {
        char quote = initialQuote;
        int index = 0;
        while (index < line.length()) {
            char current = line.charAt(index);
            if (quote == '\'') {
                if (current == '\'') {
                    quote = 0;
                }
                index++;
            } else if (quote == '"') {
                if (current == '\\') {
                    index += 2;
                } else {
                    if (current == '"') {
                        quote = 0;
                    }
                    index++;
                }
            } else if (current == '\\') {
                index += 2;
            } else if (current == '\'' || current == '"') {
                quote = current;
                index++;
            } else if (current == '#' && isCommentStart(line, index)) {
                if (!(index == 0 && line.startsWith("#!"))) {
                    found.add(new Comment(
                            "",
                            line.substring(index + 1).strip(),
                            number,
                            "LINE",
                            line.substring(0, index).isBlank()));
                }
                break;
            } else {
                index++;
            }
        }
        return quote;
    }

    private static boolean isCommentStart(String line, int index) {
        if (index == 0) {
            return true;
        }
        char previous = line.charAt(index - 1);
        return Character.isWhitespace(previous) || previous == ';' || previous == '|' || previous == '&';
    }

    private static String[] heredocOf(String line) {
        Matcher matcher =
                Pattern.compile("<<(-?)\\s*(['\"]?)([A-Za-z_][A-Za-z0-9_]*)\\2").matcher(line);
        if (matcher.find()) {
            return new String[] {matcher.group(3), matcher.group(1)};
        }
        return null;
    }

    private static int skipQuoted(String text, int start, char quote) {
        if (quote == '"' && text.startsWith("\"\"\"", start)) {
            int end = text.indexOf("\"\"\"", start + 3);
            return end < 0 ? text.length() : end + 3;
        }
        for (int index = start + 1; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '\\') {
                index++;
            } else if (current == quote) {
                return index + 1;
            }
        }
        return text.length();
    }

    private static String tripleQuoteAt(String text, int index) {
        if (text.startsWith("\"\"\"", index)) {
            return "\"\"\"";
        }
        if (text.startsWith("'''", index)) {
            return "'''";
        }
        return null;
    }

    private static int skipTripleQuoted(String text, int start, String delimiter) {
        int end = text.indexOf(delimiter, start + 3);
        return end < 0 ? text.length() : end + 3;
    }

    private static void add(List<Comment> found, String text, int start, int end, String kind, int open, int close) {
        String raw = text.substring(start, end);
        String body = raw.substring(open, Math.max(open, raw.length() - close));
        body = body.replaceAll("(?m)^\\s*\\*+\\s?", "").replaceAll("\\s+", " ").strip();
        int lineStart = text.lastIndexOf('\n', start) + 1;
        found.add(new Comment("", body, lineNumber(text, start), kind, startsWithWhitespace(text, lineStart, start)));
    }

    private static int lineNumber(String text, int offset) {
        int line = 1;
        for (int index = 0; index < offset; index++) {
            if (text.charAt(index) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static boolean startsWithWhitespace(String text, int lineStart, int start) {
        for (int index = lineStart; index < start; index++) {
            if (!Character.isWhitespace(text.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private enum Language {
        JAVA,
        SHELL,
        PYTHON
    }

    private record Comment(String file, String body, int line, String kind, boolean fullLine) {
        Comment withFile(String newFile) {
            return new Comment(newFile, body, line, kind, fullLine);
        }
    }
}
