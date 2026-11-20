package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AssertionMessageQualityGuardTests {
    @Test
    void touchedTestSources_AssertionMessages_ExplainCostAndWhatToRecheck() {
        Map<String, AssertionMessageQuality.Touch> touched = AssertionMessageQuality.touchedTestSources();
        List<String> offenders = new ArrayList<>();
        SourceFiles.allTests().forEach((file, text) -> {
            AssertionMessageQuality.Touch touch = touched.get(AssertionMessageQuality.portable(file));
            if (touch == null) {
                return;
            }
            for (AssertionMessageQuality.Finding finding : AssertionMessageQuality.thinMessages(text)) {
                if (touch.wholeFile() || touch.addedLines().contains(finding.line())) {
                    offenders.add(AssertionMessageQuality.portable(file) + ":" + finding.line() + " «"
                            + finding.problem() + "»");
                }
            }
        });

        assertEquals(
                List.of(),
                offenders,
                "Изменённый тестовый файл несёт сообщения-факты («ступень берётся прыжком») без последствий и без "
                        + "того, что перепроверить: при красном прогоне не видно, какую механику чинят и что рядом "
                        + "сломается, а при смене поведения ожидание сдвигают вслепую — так одна механика чинится "
                        + "по третьему разу. Как надо: контракт с ценой («иначе …», «без этого …») плюс концовка "
                        + "«При смене <механики> проверь <что>», и всё это в одном комментарии не короче "
                        + AssertionMessageQuality.MIN_CHARS + " символов. Правило: docs/TESTING.md → «Имя теста и "
                        + "сообщение assertion». Нарушители (файл, строка, чего не хватает)");
    }

    @Test
    void messageQuality_FactOnly_IsReported() {
        String sample = "        assert" + "Equals(Step.JUMP, command.step(), \"Обычная ступень берётся прыжком\");\n";

        assertEquals(
                List.of(new AssertionMessageQuality.Finding(
                        1, "короче 120 символов; нет цены («иначе …», «без этого …»); нет «при смене … проверь …»")),
                AssertionMessageQuality.thinMessages(sample),
                "Сторож ослеп: сообщение-факт «Обычная ступень берётся прыжком» без цены и без «что перепроверить» "
                        + "— ровно тот случай, ради которого правило ужесточено; иначе такой комментарий пройдёт "
                        + "сборку и при красном прогоне снова не будет видно, какую механику чинят. При смене "
                        + "порога длины или набора маркеров проверь оба образца этого класса: короткий факт обязан "
                        + "краснеть, а полный комментарий рядом — оставаться зелёным");
    }

    @Test
    void messageQuality_FullComment_IsAccepted() {
        String sample = "        assert" + "Equals(Step.JUMP, command.step(), \"Ступень в один блок выше берётся "
                + "прыжком: иначе дух не поднимется на бордюр и обойдёт сад кругом, а маршрут сорвётся по "
                + "тайм-ауту. При смене правила выбора шага в WalkAscent проверь ClimbRouteTests, DrillDown и "
                + "живой случай подъёма по ступеням от 2026-11-20\");\n";

        assertEquals(
                List.of(),
                AssertionMessageQuality.thinMessages(sample),
                "Сторож ложно срабатывает: полный комментарий с ценой и концовкой «при смене … проверь …» — "
                        + "законное сообщение, его нельзя помечать нарушением, иначе авторы начнут обходить "
                        + "сторожа шаблонной фразой. При смене проверки содержательности проверь этот образец "
                        + "вместе с образцом-фактом выше: один зелёный, другой красный");
    }

    @Test
    void hunkParser_AddedRanges_AreReported() {
        String diff = "@@ -142,7 +142,2 @@ class BoardLayoutTests {\n@@ -160 +170 @@ class BoardLayoutTests {\n";

        assertEquals(
                Set.of(142, 143, 170),
                AssertionMessageQuality.addedLinesIn(diff),
                "Сторож ослеп: новые строки диффа — единственное, что он проверяет, поэтому новую проверку без "
                        + "цены и без «что перепроверить» он пропустит молча, иначе правило снова станет текстом "
                        + "без зуба. При смене разбора хаков проверь оба вида: с количеством строк (+142,2) и без "
                        + "него (+170 значит одну строку)");
    }
}
