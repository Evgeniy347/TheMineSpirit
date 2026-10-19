package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ProductionCommentGuardTests {
    private static final Pattern TODO = Pattern.compile("\\b(?:TODO|FIXME|HACK)\\b");
    private static final Pattern RESTRICTION_LABEL = Pattern.compile("^(?:mc|lib)\\s*:");
    private static final Pattern SEPARATOR = Pattern.compile("[\\u2500\\u2501\\u2550=#*_\\-\\u00b7\\u2022]{3,}");
    private static final Pattern ANNOTATION = Pattern.compile("@\\w+(?:\\s*\\([^)]*\\))?");
    private static final Pattern MODIFIERS =
            Pattern.compile("(?:public|protected|private|static|final|abstract|synchronized|native|strictfp|default|"
                    + "sealed|non-sealed|transient|volatile)\\b");
    private static final List<Pattern> CODE_LINES = List.of(
            Pattern.compile("^(?:return|throw|break|continue|import|package|case|else|if|for|while|switch|do|"
                    + "try|catch|finally|assert|yield|new)\\b.*$"),
            Pattern.compile("^@\\w+.*$"),
            Pattern.compile("^[\\w$.<>\\[\\], ?]+\\s+\\w+\\s*=.*;$"),
            Pattern.compile("^[\\w$.]+\\s*=\\s*.*;$"),
            Pattern.compile("^[\\w$.<>\\[\\], ?]+\\s+\\w+\\s*\\(.*\\)\\s*[;{]?$"),
            Pattern.compile("^[\\w$.]+\\s*\\(.*\\)\\s*;$"),
            Pattern.compile("^[})\\];]+$"),
            Pattern.compile("^[\\w$.]+\\s*:\\s*$"));

    @Test
    void productionSources_ForbiddenComments_AreAbsent() {
        List<String> offenders = new ArrayList<>();
        collect(SourceFiles.javaUnder(SourceFiles.MAIN), offenders);

        assertEquals(
                List.of(),
                offenders,
                "Комментарий в production-коде пересказывает код, делит файл чертой, прячет TODO или Javadoc на "
                        + "члене. Такой комментарий врёт после первой правки, а Javadoc на поле/методе дублирует "
                        + "имя. Как надо: «что» — в имени, «почему» — в тесте, который краснеет при нарушении; "
                        + "ограничение Minecraft/библиотеки — только с меткой `// mc:` или `// lib:` "
                        + "(docs/CONVENTIONS.md → «Комментарии»). Нарушители (файл:строка)");
    }

    @Test
    void commentPattern_ForbiddenForms_AreReported() {
        String sample = "class Sample {\n"
                + "    /** Пересказ поля. */\n"
                + "    private int value;\n"
                + "    // " + "-".repeat(20) + "\n"
                + "    // TODO: вернуть после отладки\n"
                + "    // value = 2;\n"
                + "}\n";

        assertEquals(
                List.of("2 JAVADOC", "4 SEPARATOR", "5 TODO", "6 CODE"),
                findings(sample),
                "Сторож ослеп: Javadoc на члене, разделитель, TODO и закомментированный код обязаны находиться");
    }

    @Test
    void commentPattern_ClassJavadocAndRestrictionLabels_AreClean() {
        String sample = "/** Документация класса. */\n"
                + "final class Sample {\n"
                + "    // mc: ванильный тик — 20 раз в секунду, иначе скорость духа не совпадёт с игроком\n"
                + "    // lib: Jackson пишет поле только с этой аннотацией\n"
                + "    private int value;\n"
                + "}\n";

        assertEquals(
                List.of(),
                findings(sample),
                "Сторож ложно срабатывает: Javadoc класса и помеченные причины-ограничения разрешены");
    }

    private static void collect(Map<Path, String> sources, List<String> offenders) {
        sources.forEach((file, text) -> findings(text).forEach(finding -> offenders.add(file + ":" + finding)));
    }

    private static List<String> findings(String text) {
        String masked = maskComments(text);
        List<String> findings = new ArrayList<>();
        for (Comment comment : comments(text)) {
            String body = comment.body();
            if (RESTRICTION_LABEL.matcher(body).find()) {
                continue;
            }
            if (TODO.matcher(body).find()) {
                findings.add(comment.line() + " TODO");
            }
            if (SEPARATOR.matcher(body.replaceAll("\\s+", "")).matches()) {
                findings.add(comment.line() + " SEPARATOR");
            }
            if (isCommentedCode(body)) {
                findings.add(comment.line() + " CODE");
            }
            if (comment.javadoc() && !documentsType(masked, comment.end())) {
                findings.add(comment.line() + " JAVADOC");
            }
        }
        return findings;
    }

    private static boolean documentsType(String masked, int commentEnd) {
        String rest = masked.substring(commentEnd).stripLeading();
        while (true) {
            if (rest.startsWith("@interface")) {
                return true;
            }
            Matcher annotation = ANNOTATION.matcher(rest);
            Matcher modifier = MODIFIERS.matcher(rest);
            if (annotation.lookingAt()) {
                rest = rest.substring(annotation.end()).stripLeading();
            } else if (modifier.lookingAt()) {
                rest = rest.substring(modifier.end()).stripLeading();
            } else {
                break;
            }
        }
        return rest.startsWith("class ")
                || rest.startsWith("interface ")
                || rest.startsWith("enum ")
                || rest.startsWith("record ")
                || rest.startsWith("@interface");
    }

    private static boolean isCommentedCode(String body) {
        List<String> lines =
                body.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        if (lines.isEmpty()) {
            return false;
        }
        int code = 0;
        for (String line : lines) {
            if (CODE_LINES.stream().anyMatch(pattern -> pattern.matcher(line).matches())) {
                code++;
            }
        }
        return code * 2 >= lines.size();
    }

    private static List<Comment> comments(String text) {
        List<Comment> comments = new ArrayList<>();
        int index = 0;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (current == '"') {
                index = skipQuoted(text, index, '"');
            } else if (current == '\'') {
                index = skipQuoted(text, index, '\'');
            } else if (text.startsWith("//", index)) {
                int end = text.indexOf('\n', index);
                end = end < 0 ? text.length() : end;
                comments.add(new Comment(
                        body(text.substring(index, end)), index, end, false, SourceFiles.lineOf(text, index)));
                index = end;
            } else if (text.startsWith("/*", index)) {
                int end = text.indexOf("*/", index + 2);
                end = end < 0 ? text.length() : end + 2;
                String raw = text.substring(index, end);
                comments.add(
                        new Comment(body(raw), index, end, raw.startsWith("/**"), SourceFiles.lineOf(text, index)));
                index = end;
            } else {
                index++;
            }
        }
        return comments;
    }

    private static String body(String raw) {
        String body;
        if (raw.startsWith("//")) {
            body = raw.substring(2);
        } else if (raw.startsWith("/**")) {
            body = raw.substring(3, Math.max(3, raw.length() - 2));
        } else {
            body = raw.substring(2, Math.max(2, raw.length() - 2));
        }
        return body.replaceAll("(?m)^\\s*\\*+\\s?", "").strip();
    }

    private static String maskComments(String text) {
        StringBuilder masked = new StringBuilder(text);
        for (Comment comment : comments(text)) {
            for (int i = comment.start(); i < comment.end(); i++) {
                if (masked.charAt(i) != '\n') {
                    masked.setCharAt(i, ' ');
                }
            }
        }
        return masked.toString();
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

    private record Comment(String body, int start, int end, boolean javadoc, int line) {}
}
