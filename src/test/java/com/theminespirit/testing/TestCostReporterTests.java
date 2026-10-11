package com.theminespirit.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class TestCostReporterTests {
    private static final String CSV_HEADER = "class,test,kind,cpu_ms,wall_ms,gc_ms,warmup";

    private static Properties thresholds() {
        Properties properties = new Properties();
        properties.setProperty("junit.single.warn.ms", "100");
        properties.setProperty("junit.single.slow.ms", "500");
        properties.setProperty("jqwik.total.warn.ms", "300");
        properties.setProperty("jqwik.total.slow.ms", "1000");
        return properties;
    }

    private static TestCostReporter.CostLine line(String name, String kind, long cpu, boolean warmup) {
        return new TestCostReporter.CostLine("pkg.Class", name, kind, cpu, 0, 0, warmup);
    }

    @Test
    void report_Top10_SortedByCpuDescending() {
        List<TestCostReporter.CostLine> lines = List.of(
                line("a", "junit", 100, false),
                line("b", "junit", 500, false),
                line("c", "junit", 300, false));

        List<String> report = TestCostReporter.report(lines, thresholds());

        assertTrue(report.get(1).contains("b — 500 мс CPU"), "первый в топе — самый долгий: " + report.get(1));
        assertTrue(report.get(2).contains("c — 300 мс CPU"), "второй — средний: " + report.get(2));
        assertTrue(report.get(3).contains("a — 100 мс CPU"), "третий — самый быстрый: " + report.get(3));
    }

    @Test
    void report_WarmupLine_GoesToNoThreshold() {
        List<TestCostReporter.CostLine> lines = List.of(line("a", "junit", 5000, true));

        List<String> report = TestCostReporter.report(lines, thresholds());

        assertFalse(report.stream().anyMatch(text -> text.contains("5000 мс")),
                "строка с warmup в пороги не идёт");
        assertTrue(report.stream().anyMatch(text -> text.contains("(нет)")),
                "без строк в порогах отчёт говорит «(нет)»: " + report);
    }

    @Test
    void report_Cpu499_NotMarkedSlow() {
        List<String> report = TestCostReporter.report(List.of(line("a", "junit", 499, false)), thresholds());

        assertFalse(report.stream().anyMatch(text -> text.contains("МЕДЛЕННЫЙ") && text.contains("499")),
                "499 мс ниже порога 500 — не МЕДЛЕННЫЙ: " + report);
    }

    @Test
    void report_Cpu500_MarkedSlow() {
        List<String> report = TestCostReporter.report(List.of(line("a", "junit", 500, false)), thresholds());

        assertTrue(report.stream().anyMatch(text -> text.contains("a — 500 мс CPU (ДОЛГИЙ, порог 500 мс)")),
                "500 мс — граница МЕДЛЕННЫЙ: " + report);
    }

    @Test
    void report_JqwikTotalWarn300_MarksAsSlow() {
        List<String> report = TestCostReporter.report(List.of(line("a", "jqwik", 300, false)), thresholds());

        assertTrue(report.stream().anyMatch(text -> text.contains("a — 300 мс CPU (ДОЛГИЙ, порог 1000 мс)")
                || text.contains("a — 300 мс CPU (порог 300 мс)")),
                "jqwik-строка 300 мс выше total warn 300: " + report);
    }

    @Test
    void report_Parse_SkipsHeaderAndEmptyLines() {
        List<TestCostReporter.CostLine> lines = TestCostReporter.parse(List.of(
                CSV_HEADER,
                "",
                "pkg.Class,a,junit,100,200,0,false",
                "pkg.Class,b,jqwik,300,400,0,true",
                "broken,line"));

        assertEquals(2, lines.size(), "заголовок, пустая строка и битая строка пропускаются");
    }
}
