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
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class TasksDoneGuardTests {
    private static final Path TASKS = Path.of("tasks");
    private static final Pattern OPEN = Pattern.compile("^\\s*(?:-|\\d+\\.)\\s*\\[ \\]");
    private static final Pattern CHECKED = Pattern.compile("^\\s*(?:-|\\d+\\.)\\s*\\[[xX]\\]");
    private static final Pattern WAIT = Pattern.compile("ждёт приёмки");
    private static final Pattern BRANCH = Pattern.compile("ветк");

    @Test
    void tasks_FinishedChecklists_AreDeletedOrMarkedAsWaiting() {
        Map<String, String> files = taskFiles();
        List<String> offenders = new ArrayList<>();
        files.forEach((path, text) -> {
            if (finishedWithoutWait(text)) {
                offenders.add(path);
            }
        });

        assertEquals(
                List.of(),
                offenders,
                "В tasks/ лежит чеклист, где все пункты отмечены, но нет пометки «ждёт приёмки (ветка …)»: "
                        + "принятый чеклист обязан быть удалён, иначе папка задач превращается в архив закрытого "
                        + "и прячет открытую работу. Как надо: удалить чеклист или в статусе назвать ветку и то, "
                        + "что он ждёт приёмки. Правило: выполненный чеклист не лежит в tasks/. Нарушители");
    }

    @Test
    void finishedWithoutWait_AllCheckedNoNote_IsReported() {
        Map<String, String> files = Map.of("tasks/9-x.md", "# Чеклист\n1. [x] сделано\n2. [x] тоже\n");

        assertEquals(
                List.of("tasks/9-x.md"),
                offenders(files),
                "Сторож ослеп: все пункты отмечены, пометки о приёмке нет — обязан попасть в нарушители");
    }

    @Test
    void finishedWithoutWait_AllCheckedWithWaitNote_IsAccepted() {
        Map<String, String> files =
                Map.of("tasks/9-x.md", "Статус: выполнено в ветке `opus/work`, ждёт приёмки.\n- [x] сделано\n");

        assertEquals(
                List.of(),
                offenders(files),
                "Сторож ложно срабатывает: чеклист со статусом «ждёт приёмки» должен приниматься");
    }

    @Test
    void finishedWithoutWait_WaitNoteWithoutBranch_IsReported() {
        Map<String, String> files = Map.of("tasks/9-x.md", "Статус: ждёт приёмки.\n- [x] сделано\n");

        assertEquals(
                List.of("tasks/9-x.md"),
                offenders(files),
                "Сторож ослеп: пометка «ждёт приёмки» без названной ветки обязана попасть в нарушители");
    }

    @Test
    void finishedWithoutWait_OpenItem_IsAccepted() {
        Map<String, String> files = Map.of("tasks/9-x.md", "# Чеклист\n- [x] сделано\n- [ ] осталось\n");

        assertEquals(
                List.of(),
                offenders(files),
                "Сторож ложно срабатывает: чеклист с открытым пунктом — рабочий, не архив");
    }

    @Test
    void finishedWithoutWait_NoItems_Skipped() {
        Map<String, String> files = Map.of("tasks/questions.md", "# Вопросы\nВопрос про версии моделей.\n");

        assertEquals(
                List.of(),
                offenders(files),
                "Сторож ложно срабатывает: файл без пунктов чеклиста — не чеклист и не проверяется");
    }

    @Test
    void open_IndentedAndNumberedItems_AreFound() {
        String text = "  - [ ] вложенный\n7. [ ] номерной\n- [x] закрытый\n";

        assertEquals(2, count(OPEN, text), "Сторож ослеп: открытые пункты в отступе и с номером обязаны считаться");
        assertEquals(1, count(CHECKED, text), "Сторож ослеп: закрытый пункт в отступе обязан находиться");
    }

    private static List<String> offenders(Map<String, String> files) {
        List<String> offenders = new ArrayList<>();
        new TreeMap<>(files).forEach((path, text) -> {
            if (finishedWithoutWait(text)) {
                offenders.add(path);
            }
        });
        return offenders;
    }

    private static boolean finishedWithoutWait(String text) {
        boolean hasItem = count(OPEN, text) + count(CHECKED, text) > 0;
        boolean waiting = WAIT.matcher(text).find() && BRANCH.matcher(text).find();
        return hasItem && count(OPEN, text) == 0 && !waiting;
    }

    private static int count(Pattern pattern, String text) {
        return (int) text.lines().filter(line -> pattern.matcher(line).find()).count();
    }

    private static Map<String, String> taskFiles() {
        assertTrue(
                Files.isDirectory(TASKS),
                "Сторож не нашёл " + TASKS + ": тесты запускаются из корня проекта (./gradlew test)");
        Map<String, String> files = new TreeMap<>();
        try (Stream<Path> stream = Files.walk(TASKS)) {
            for (Path file : stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".md"))
                    .toList()) {
                files.put(file.toString(), Files.readString(file, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Сторож не смог прочитать чеклисты в " + TASKS, e);
        }
        return files;
    }
}
