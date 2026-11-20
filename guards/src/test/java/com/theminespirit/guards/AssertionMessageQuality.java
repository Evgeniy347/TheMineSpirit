package com.theminespirit.guards;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AssertionMessageQuality {
    static final int MIN_CHARS = 120;
    static final Map<String, Integer> ARGUMENTS_WITH_MESSAGE = Map.ofEntries(
            Map.entry("assertTrue", 2),
            Map.entry("assertFalse", 2),
            Map.entry("assertNull", 2),
            Map.entry("assertEquals", 3),
            Map.entry("assertNotEquals", 3),
            Map.entry("assertSame", 3),
            Map.entry("assertNotSame", 3),
            Map.entry("assertArrayEquals", 3),
            Map.entry("assertIterableEquals", 3),
            Map.entry("assertLinesMatch", 3),
            Map.entry("assertThrows", 3),
            Map.entry("assertInstanceOf", 3));
    static final Pattern ASSERT_CALL = Pattern.compile("(?<![\\w.])(assert[A-Z]\\w*)\\s*\\(");
    private static final Pattern STRING_LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern HUNK = Pattern.compile("@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,(\\d+))? @@");
    private static final List<String> COST_MARKERS = List.of(
            "иначе",
            "без этого",
            "ломает",
            "сломается",
            "потеряет",
            "застрянет",
            "не сможет",
            "не дойдёт",
            "не найдёт",
            "цена",
            "из-за этого",
            "потому что");
    private static final List<String> CHANGE_MARKERS =
            List.of("при смене", "при изменении", "при правке", "если менять", "если изменить");
    private static final String RECHECK_MARKER = "провер";

    record Touch(boolean wholeFile, Set<Integer> addedLines) {}

    record Finding(int line, String problem) {}

    static List<Finding> thinMessages(String text) {
        List<Finding> findings = new ArrayList<>();
        Matcher matcher = ASSERT_CALL.matcher(text);
        while (matcher.find()) {
            Integer required = ARGUMENTS_WITH_MESSAGE.get(matcher.group(1));
            if (required == null) {
                continue;
            }
            List<String> arguments = arguments(text, matcher.end());
            if (arguments.size() < required) {
                continue;
            }
            String message = messageText(arguments.get(arguments.size() - 1));
            String problem = missing(message);
            if (!problem.isEmpty()) {
                findings.add(new Finding(SourceFiles.lineOf(text, matcher.start()), problem));
            }
        }
        return findings;
    }

    static Map<String, Touch> touchedTestSources() {
        Map<String, Touch> touched = new TreeMap<>();
        for (String path : testSources(git("ls-files", "--others", "--exclude-standard"))) {
            touched.put(path, new Touch(true, Set.of()));
        }
        for (String path : testSources(git("diff", "--name-only", "--diff-filter=AMR", "HEAD", "--"))) {
            touched.putIfAbsent(path, new Touch(false, addedLines(path)));
        }
        return touched;
    }

    static Set<Integer> addedLinesIn(String diff) {
        Set<Integer> lines = new TreeSet<>();
        Matcher matcher = HUNK.matcher(diff);
        while (matcher.find()) {
            int start = Integer.parseInt(matcher.group(1));
            int count = matcher.group(2) == null ? 1 : Integer.parseInt(matcher.group(2));
            for (int i = 0; i < count; i++) {
                lines.add(start + i);
            }
        }
        return lines;
    }

    static int skipLiteral(String text, int start, char quote) {
        for (int i = start + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == quote) {
                return i;
            }
        }
        return text.length();
    }

    private static String missing(String message) {
        String normalized = message.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        List<String> missing = new ArrayList<>();
        if (normalized.length() < MIN_CHARS) {
            missing.add("короче " + MIN_CHARS + " символов");
        }
        if (COST_MARKERS.stream().noneMatch(normalized::contains)) {
            missing.add("нет цены («иначе …», «без этого …»)");
        }
        if (CHANGE_MARKERS.stream().noneMatch(normalized::contains) || !normalized.contains(RECHECK_MARKER)) {
            missing.add("нет «при смене … проверь …»");
        }
        return String.join("; ", missing);
    }

    private static String messageText(String argument) {
        StringBuilder text = new StringBuilder();
        Matcher matcher = STRING_LITERAL.matcher(argument);
        while (matcher.find()) {
            text.append(matcher.group(1).replaceAll("\\\\(.)", "$1"));
        }
        return text.toString();
    }

    private static List<String> arguments(String text, int afterOpenParen) {
        List<String> arguments = new ArrayList<>();
        int depth = 0;
        int start = afterOpenParen;
        for (int i = afterOpenParen; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipLiteral(text, i, c);
            } else if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                if (depth == 0) {
                    arguments.add(text.substring(start, i));
                    return arguments;
                }
                depth--;
            } else if (c == ',' && depth == 0) {
                arguments.add(text.substring(start, i));
                start = i + 1;
            }
        }
        return arguments;
    }

    private static Set<String> testSources(String output) {
        Set<String> sources = new TreeSet<>();
        for (String line : output.split("\n", -1)) {
            String path = line.trim().replace('\\', '/');
            if (path.endsWith(".java") && path.contains("/src/test/java/")) {
                sources.add(path);
            }
        }
        return sources;
    }

    private static Set<Integer> addedLines(String path) {
        return addedLinesIn(git("diff", "-U0", "HEAD", "--", path));
    }

    private static String git(String... arguments) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        try {
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.waitFor() != 0) {
                throw new IllegalStateException("Сторож не смог спросить git " + String.join(" ", command)
                        + ": без списка изменённых тестов правило «тронул класс — доведи сообщения» не проверить. "
                        + "Вывод: " + output.trim());
            }
            return output;
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Сторож не смог запустить git " + String.join(" ", command)
                            + ": без списка изменённых тестов правило «тронул класс — доведи сообщения» не проверить",
                    e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Сторож прерван на git " + String.join(" ", command), e);
        }
    }

    static String portable(Path file) {
        return file.toString().replace('\\', '/');
    }

    private AssertionMessageQuality() {}
}
