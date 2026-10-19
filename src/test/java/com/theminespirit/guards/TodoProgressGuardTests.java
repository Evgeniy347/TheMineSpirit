package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TodoProgressGuardTests {
    private static final Path TODO = Path.of("todo.md");
    private static final Pattern PROGRESS =
            Pattern.compile("^Прогресс: сделано (\\d+) из (\\d+) \\((\\d+) %\\)\\.?\\s*$");
    private static final Pattern OPEN = Pattern.compile("^\\s*- \\[ \\]");
    private static final Pattern CHECKED = Pattern.compile("^\\s*- \\[[xX]\\]");

    @Test
    void todo_ProgressLine_MatchesOpenAndDoneCounts() {
        List<String> lines = readTodo();

        assertEquals(
                List.of(),
                progressProblems(lines),
                "Строка «Прогресс: сделано N из M (P %)» в todo.md не согласована с числом открытых пунктов: "
                        + "M обязано равняться N + число строк `- [ ]`, P = ⌊100·N/M⌋. Цена: счётчик и план "
                        + "расходятся, никто не знает реального прогресса. Как надо: поправить числа или удалить "
                        + "выполненные пункты. Правило: счётчик прогресса держит TodoProgressGuardTests. "
                        + "Нарушения");
    }

    @Test
    void todo_CheckedItems_AreAbsent() {
        List<String> checked = checkedItems(readTodo());

        assertEquals(
                List.of(),
                checked,
                "В todo.md осталась строка `- [x]`: выполненный пункт должен быть удалён из плана, а не помечен. "
                        + "Цена: план пухнет закрытыми пунктами, счётчик врёт. Как надо: удалить строку, "
                        + "счётчик «сделано» увеличить. Правило: todo.md — только открытые задачи. Нарушители");
    }

    @Test
    void progressProblems_ConsistentLine_ReportsNothing() {
        assertEquals(
                List.of(),
                progressProblems(List.of("Прогресс: сделано 3 из 5 (60 %).", "- [ ] a", "- [ ] b")),
                "Сторож ложно срабатывает: 3 + 2 = 5, ⌊100·3/5⌋ = 60 — согласовано");
    }

    @Test
    void progressProblems_WrongTotal_IsReported() {
        assertEquals(
                List.of("Прогресс: сделано 3 из 9 (60 %)."),
                progressProblems(List.of("Прогресс: сделано 3 из 9 (60 %).", "- [ ] a", "- [ ] b")),
                "Сторож ослеп: итог M, не равный N + число открытых, обязан попасть в нарушения");
    }

    @Test
    void progressProblems_WrongPercent_IsReported() {
        assertEquals(
                List.of("Прогресс: сделано 3 из 5 (59 %)."),
                progressProblems(List.of("Прогресс: сделано 3 из 5 (59 %).", "- [ ] a", "- [ ] b")),
                "Сторож ослеп: процент P, не равный ⌊100·N/M⌋, обязан попасть в нарушения");
    }

    @Test
    void progressProblems_CheckedLine_IsReportedAsMissingOpenItem() {
        List<String> violations = progressProblems(List.of("Прогресс: сделано 3 из 5 (60 %).", "- [x] a", "- [ ] b"));

        assertEquals(
                List.of("Прогресс: сделано 3 из 5 (60 %)."),
                violations,
                "Сторож ослеп: помеченный пункт не считается открытым, и согласованность обязана упасть");
    }

    @Test
    void checkedItems_CheckedBox_IsFound() {
        assertEquals(
                List.of("- [x] готово"),
                checkedItems(List.of("- [ ] ждёт", "- [x] готово")),
                "Сторож ослеп: строка `- [x]` обязана находиться");
    }

    @Test
    void checkedItems_OpenBox_IsAccepted() {
        assertEquals(
                List.of(),
                checkedItems(List.of("- [ ] ждёт", "  - [ ] вложенный")),
                "Сторож ложно срабатывает: открытые пункты, в том числе вложенные, — не нарушение");
    }

    private static List<String> readTodo() {
        assertTrue(
                Files.isRegularFile(TODO),
                "Сторож не нашёл " + TODO + ": тесты запускаются из корня проекта (./gradlew test)");
        try {
            return Files.readAllLines(TODO, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Сторож не смог прочитать " + TODO, e);
        }
    }

    private static List<String> progressProblems(List<String> lines) {
        List<String> problems = new ArrayList<>();
        Matcher progress = null;
        for (String line : lines) {
            Matcher candidate = PROGRESS.matcher(line);
            if (candidate.matches()) {
                progress = candidate;
            }
        }
        if (progress == null) {
            problems.add("нет строки «Прогресс: сделано N из M (P %).»");
            return problems;
        }
        long done = Long.parseLong(progress.group(1));
        long total = Long.parseLong(progress.group(2));
        long percent = Long.parseLong(progress.group(3));
        long open = lines.stream().filter(line -> OPEN.matcher(line).find()).count();
        long expectedPercent = total == 0 ? 0 : Math.floorDiv(100 * done, total);
        if (total != done + open || percent != expectedPercent) {
            problems.add(progress.group(0).strip());
        }
        return problems;
    }

    private static List<String> checkedItems(List<String> lines) {
        List<String> checked = new ArrayList<>();
        for (String line : lines) {
            if (CHECKED.matcher(line).find()) {
                checked.add(line.strip());
            }
        }
        return checked;
    }
}
