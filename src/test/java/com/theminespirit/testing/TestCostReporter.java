package com.theminespirit.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

public final class TestCostReporter {
    public record CostLine(
            String className, String testName, String kind, long cpuMs, long wallMs, long gcMs, boolean warmup) {}

    private TestCostReporter() {}

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Использование: TestCostReporter <csv> <thresholds.properties>");
            System.exit(2);
        }
        Path csv = Path.of(args[0]);
        Properties thresholds = new Properties();
        try (var in = Files.newInputStream(Path.of(args[1]))) {
            thresholds.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось прочитать пороги " + args[1], e);
        }
        List<String> csvLines;
        try {
            csvLines = Files.readAllLines(csv, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось прочитать CSV " + csv, e);
        }
        for (String line : report(parse(csvLines), thresholds)) {
            System.out.println(line);
        }
    }

    public static List<CostLine> parse(List<String> csvLines) {
        List<CostLine> lines = new ArrayList<>();
        for (int i = 1; i < csvLines.size(); i++) {
            String line = csvLines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split(",", -1);
            if (parts.length < 7) {
                continue;
            }
            try {
                lines.add(new CostLine(
                        parts[0],
                        parts[1],
                        parts[2],
                        Long.parseLong(parts[3]),
                        Long.parseLong(parts[4]),
                        Long.parseLong(parts[5]),
                        Boolean.parseBoolean(parts[6])));
            } catch (NumberFormatException ignored) {
                // lib: Integer.parseInt бросает NumberFormatException — битая строка CSV в отчёт не идёт
            }
        }
        return lines;
    }

    public static List<String> report(List<CostLine> lines, Properties thresholds) {
        long junitWarn = Long.parseLong(thresholds.getProperty("junit.single.warn.ms", "100"));
        long junitSlow = Long.parseLong(thresholds.getProperty("junit.single.slow.ms", "500"));
        long jqwikWarn = Long.parseLong(thresholds.getProperty("jqwik.total.warn.ms", "300"));
        long jqwikSlow = Long.parseLong(thresholds.getProperty("jqwik.total.slow.ms", "1000"));
        List<CostLine> timed = lines.stream().filter(line -> !line.warmup()).toList();

        List<String> out = new ArrayList<>();
        List<CostLine> top = timed.stream()
                .sorted(Comparator.comparingLong(CostLine::cpuMs).reversed())
                .limit(10)
                .toList();
        out.add("=== Топ-10 по CPU потока (warmup исключены) ===");
        int rank = 1;
        for (CostLine line : top) {
            out.add(String.format(
                    Locale.ROOT,
                    "%d. %s.%s — %d мс CPU, %d мс wall, %s",
                    rank++,
                    line.className(),
                    line.testName(),
                    line.cpuMs(),
                    line.wallMs(),
                    line.kind()));
        }

        out.add("=== МЕДЛЕННЫЙ (выше порога по CPU) ===");
        boolean any = false;
        for (CostLine line : timed) {
            long warn = "jqwik".equals(line.kind()) ? jqwikWarn : junitWarn;
            long slow = "jqwik".equals(line.kind()) ? jqwikSlow : junitSlow;
            if (line.cpuMs() >= slow) {
                out.add(String.format(
                        Locale.ROOT,
                        "%s.%s — %d мс CPU (ДОЛГИЙ, порог %d мс)",
                        line.className(),
                        line.testName(),
                        line.cpuMs(),
                        slow));
                any = true;
            } else if (line.cpuMs() >= warn) {
                out.add(String.format(
                        Locale.ROOT,
                        "%s.%s — %d мс CPU (порог %d мс)",
                        line.className(),
                        line.testName(),
                        line.cpuMs(),
                        warn));
                any = true;
            }
        }
        if (!any) {
            out.add("(нет)");
        }
        return out;
    }
}
