package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class StubExemptionGuardTests {
    private static final Path BASELINE = Path.of("config", "stub-orphan-baseline.txt");
    private static final String PREFIX = "Заготовка под открытую задачу: ";
    private static final Pattern REFERENCE = Pattern.compile("^Заготовка под открытую задачу: todo\\.md → «([^»]+)»");
    private static final String RULE = "Заготовка ссылается на задачу, которой больше нет в todo.md: код на "
            + "будущее живёт без плана, стареет вместе с API и не удаляется. Как надо: заготовку реализуют в "
            + "открытой задаче или удаляют вместе с пунктом todo.md (восстановить из git дешевле, чем "
            + "поддерживать); ссылка в причине — ровно «todo.md → «Название»». Список "
            + "config/stub-orphan-baseline.txt — уже известные сироты, он только уменьшается.";

    @Test
    void stub_WithLiveTodoReference_IsAccepted() {
        assertEquals(
                List.of(),
                problems(List.of(stub("a/B.java|method|B#x", "Строительство")), "## Строительство", Set.of()),
                "Сторож ложно срабатывает: заготовка с живой задачей допустима"
                        + " Иначе тест stub_WithLiveTodoReference_IsAccepted перестанет ловить эту поломку. При смене stub_WithLiveTodoReference_IsAccepted проверь это утверждение.");
    }

    @Test
    void stub_WithoutTodoReference_IsReported() {
        assertEquals(
                List.of("a/B.java|method|B#x → «Дети»: такой задачи нет в todo.md"),
                problems(List.of(stub("a/B.java|method|B#x", "Дети")), "## Строительство", Set.of()),
                "Сторож ослеп: заготовка на исчезнувшую задачу обязана попасть в нарушители"
                        + " Иначе тест stub_WithoutTodoReference_IsReported перестанет ловить эту поломку. При смене stub_WithoutTodoReference_IsReported проверь это утверждение.");
    }

    @Test
    void stub_KnownOrphan_IsAccepted() {
        assertEquals(
                List.of(),
                problems(
                        List.of(stub("a/B.java|method|B#x", "Дети")),
                        "## Строительство",
                        Set.of("a/B.java|method|B#x")),
                "Сторож ложно срабатывает: известный сирота из ратчета допустим до удаления"
                        + " Иначе тест stub_KnownOrphan_IsAccepted перестанет ловить эту поломку. При смене stub_KnownOrphan_IsAccepted проверь это утверждение.");
    }

    @Test
    void baseline_FixedOrphan_IsReportedAsStale() {
        assertEquals(
                List.of("a/B.java|method|B#x: запись устарела — заготовки нет или задача снова в todo.md;"
                        + " убери строку из списка"),
                problems(List.of(), "## Строительство", Set.of("a/B.java|method|B#x")),
                "Сторож ослеп: исправленная заготовка обязана уйти из списка исключений"
                        + " Иначе тест baseline_FixedOrphan_IsReportedAsStale перестанет ловить эту поломку. При смене baseline_FixedOrphan_IsReportedAsStale проверь это утверждение.");
    }

    @Test
    void stub_ReasonWithoutTodoLink_IsReported() {
        assertEquals(
                List.of("a/B.java|method|B#x: причина заготовки без ссылки «todo.md → «задача»»"),
                problems(
                        List.of(new DeadCodeExemptions.Exemption("a/B.java|method|B#x", PREFIX + "«Дети»")),
                        "## Дети",
                        Set.of()),
                "Сторож ослеп: причина обязана называть пункт todo.md, иначе задачу не найти"
                        + " При смене stub_ReasonWithoutTodoLink_IsReported проверь это утверждение.");
    }

    @Test
    void stubs_RealExemptions_AreAnchored() {
        assertEquals(
                List.of(),
                problems(DeadCodeExemptions.OPEN_TASKS, read(Path.of("todo.md")), readLines(BASELINE)),
                RULE + System.lineSeparator() + "Нарушители (цель: причина):"
                        + " Иначе тест stubs_RealExemptions_AreAnchored перестанет ловить эту поломку. При смене stubs_RealExemptions_AreAnchored проверь это утверждение.");
    }

    private static DeadCodeExemptions.Exemption stub(String key, String title) {
        return new DeadCodeExemptions.Exemption(key, PREFIX + "todo.md → «" + title + "»");
    }

    private static List<String> problems(
            List<DeadCodeExemptions.Exemption> exemptions, String todo, Set<String> baseline) {
        List<String> found = new ArrayList<>();
        Set<String> orphans = new TreeSet<>();
        for (DeadCodeExemptions.Exemption exemption : exemptions) {
            if (!exemption.reason().startsWith(PREFIX)) {
                continue;
            }
            Matcher matcher = REFERENCE.matcher(exemption.reason());
            if (!matcher.find()) {
                found.add(exemption.key() + ": причина заготовки без ссылки «todo.md → «задача»»");
                continue;
            }
            String title = matcher.group(1);
            if (todo.contains(title)) {
                continue;
            }
            orphans.add(exemption.key());
            if (!baseline.contains(exemption.key())) {
                found.add(exemption.key() + " → «" + title + "»: такой задачи нет в todo.md");
            }
        }
        new TreeSet<>(baseline).forEach(key -> {
            if (!orphans.contains(key)) {
                found.add(key + ": запись устарела — заготовки нет или задача снова в todo.md;"
                        + " убери строку из списка");
            }
        });
        return found;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Сторож не смог прочитать " + path, e);
        }
    }

    private static Set<String> readLines(Path path) {
        Set<String> lines = new TreeSet<>();
        read(path).lines().map(String::trim).filter(line -> !line.isEmpty()).forEach(lines::add);
        return lines;
    }
}
