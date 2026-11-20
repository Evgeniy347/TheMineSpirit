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
        List<TestCostReporter.CostLine> lines =
                List.of(line("a", "junit", 100, false), line("b", "junit", 500, false), line("c", "junit", 300, false));

        List<String> report = TestCostReporter.report(lines, thresholds());

        assertTrue(
                report.get(1).contains("b — 500 мс CPU"),
                "Топ отчёта начинается с самого долгого теста: иначе оптимизируют случайный класс, а бюджет "
                        + "200 с CPU уходит на второстепенное. При смене сортировки в TestCostReporter.report "
                        + "проверь testCostReport на живом TestResults/unit/test-cost.csv: b, c, a по убыванию CPU");
        assertTrue(
                report.get(2).contains("c — 300 мс CPU"),
                "Второй в топе — средний по стоимости: иначе отчёт сваливается в произвольный порядок и "
                        + "долгий тест прячется за быстрым. При смене сортировки проверь ту же тройку b, c, a "
                        + "на реальном test-cost.csv после ближайшего полного unit-прогона");
        assertTrue(
                report.get(3).contains("a — 100 мс CPU"),
                "Третий в топе — самый быстрый из троих: иначе отчёт не даёт выбрать, что оптимизировать "
                        + "первым, и правило «процессор тестов ≤ 200 с» теряет измеримость. При смене сортировки "
                        + "проверь order b, c, a в testCostReport вместе с соседними проверками порядка");
    }

    @Test
    void report_WarmupLine_GoesToNoThreshold() {
        List<TestCostReporter.CostLine> lines = List.of(line("a", "junit", 5000, true));

        List<String> report = TestCostReporter.report(lines, thresholds());

        assertFalse(
                report.stream().anyMatch(text -> text.contains("5000 мс")),
                "Строка с warmup в пороги не идёт: прогрев JVM — не стоимость теста, иначе отчёт навсегда "
                        + "краснеет на первом классе и реальный долг перестаёт быть виден. При смене фильтра "
                        + "warmup в TestCostReporter.report проверь CSV, где единственная строка warmup=true");
        assertTrue(
                report.stream().anyMatch(text -> text.contains("(нет)")),
                "Без строк в порогах отчёт печатает «(нет)», а не пустоту: иначе читатель не отличит "
                        + "«всё в норме» от сломанного отчёта и не заметит протухшие пороги. При смене формата "
                        + "секции порогов проверь пустой CSV: в разделе порогов стоит «(нет)»");
    }

    @Test
    void report_Cpu499_NotMarkedSlow() {
        List<String> report = TestCostReporter.report(List.of(line("a", "junit", 499, false)), thresholds());

        assertFalse(
                report.stream().anyMatch(text -> text.contains("499") && text.contains("ДОЛГИЙ")),
                "499 мс ниже порога 500 — метки ДОЛГИЙ нет: иначе одна миллисекунда отделяет нормальный тест "
                        + "от тревоги и порог перестаёт быть границей. При смене сравнения с slow-порогом проверь "
                        + "соседний report_Cpu500_MarkedSlow: 499 без метки, 500 с меткой");
        assertTrue(
                report.stream().anyMatch(text -> text.contains("a — 499 мс CPU (порог 100 мс)")),
                "499 мс выше warn 100 — строка с порогом в отчёте: иначе приближение к пределу не видно и долг "
                        + "копится молча. При смене форматирования строки в TestCostReporter проверь текст "
                        + "«a — 499 мс CPU (порог 100 мс)» на этом же входе");
    }

    @Test
    void report_Cpu500_MarkedSlow() {
        List<String> report = TestCostReporter.report(List.of(line("a", "junit", 500, false)), thresholds());

        assertTrue(
                report.stream().anyMatch(text -> text.contains("a — 500 мс CPU (ДОЛГИЙ, порог 500 мс)")),
                "500 мс — граница ДОЛГИЙ, порог включается при достижении: иначе тест ровно в лимит считается "
                        + "нормальным и долг снова копится молча. При смене сравнения в TestCostReporter проверь "
                        + "соседний report_Cpu499_NotMarkedSlow: 499 без метки, 500 с меткой");
    }

    @Test
    void report_JqwikTotalWarn300_MarksAsSlow() {
        List<String> report = TestCostReporter.report(List.of(line("a", "jqwik", 300, false)), thresholds());

        assertTrue(
                report.stream()
                        .anyMatch(text -> text.contains("a — 300 мс CPU (ДОЛГИЙ, порог 1000 мс)")
                                || text.contains("a — 300 мс CPU (порог 300 мс)")),
                "Свойство jqwik сравнивается с total-порогами, а не с одиночными: иначе 300 мс свойства "
                        + "читаются как один медленный тест и лимит tries перестаёт иметь цену. При смене выбора "
                        + "порога по kind в TestCostReporter.report проверь CSV, где kind=jqwik и cpu 300");
    }

    @Test
    void report_Parse_SkipsHeaderAndEmptyLines() {
        List<TestCostReporter.CostLine> lines = TestCostReporter.parse(List.of(
                CSV_HEADER,
                "",
                "pkg.Class,a,junit,100,200,0,false",
                "pkg.Class,b,jqwik,300,400,0,true",
                "broken,line"));

        assertEquals(
                2,
                lines.size(),
                "Заголовок, пустая и битая строки тест-cost.csv пропускаются: иначе первый класс считается "
                        + "тестом «class», а битая запись роняет весь отчёт и ночной прогон. При смене "
                        + "TestCostReporter.parse проверь CSV с шапкой, пустой строкой и строкой из двух полей: "
                        + "остаются ровно две записи");
    }
}
