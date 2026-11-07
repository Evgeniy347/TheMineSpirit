package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TestRunnerLifecycleGuardTests {
    private static final Path SCRIPT = Path.of("scripts/test.sh");
    private static final Path DETECTOR = Path.of("scripts/lead/orphans-win.sh");
    private static final Path CHECK = Path.of("scripts/testing/check-test-runner.sh");
    private static final Path LEAD = Path.of("docs/LEAD-AGENT.md");

    @Test
    void testScript_Supervision_DoesNotForkPauseProcesses() {
        String script = read(SCRIPT);
        assertTrue(
                script.contains("read -r -t \"$seconds\" -u 9"),
                "Ожидание test.sh должно быть встроенным (read -t на fifo), иначе на Windows Git Bash "
                        + "pause-процесс может не запуститься и цикл надзора выродится в busy-loop на 100 % "
                        + "ядра. Правило: scripts/test.sh → pause_tick, без «sleep N & wait» в надзоре");
        for (String forked : new String[] {"sleep 1 &", "sleep 20 &", "sleep \"$GAMETEST_BEAT_INTERVAL\" &"}) {
            assertFalse(
                    script.contains(forked),
                    "В test.sh вернулся fork-пауза «" + forked + "»: под нагрузкой пауза теряется, "
                            + "надзор крутит ядро. Цена: сироты test.sh съедают машину у пяти исполнителей. "
                            + "Как надо: pause_tick (docs/LEAD-AGENT.md → «Сироты»)");
        }
        int pauses = script.split("pause_tick", -1).length - 1;
        assertTrue(
                pauses >= 6,
                "Надзор test.sh (монитор, пульс, родитель, замок, ожидание смерти) обязан ждать через "
                        + "pause_tick; найдено вызовов: " + pauses);
    }

    @Test
    void testScript_LosesParent_RunStops() {
        String script = read(SCRIPT);
        assertTrue(
                script.contains("start_parent_watch") && script.contains("THEMINESPIRIT_TEST_DETACHED"),
                "test.sh обязан замечать смерть родителя (обёртки исполнителя) и гасить прогон, иначе "
                        + "осиротевший прогон живёт часами; осознанно отвязанный запуск — "
                        + "THEMINESPIRIT_TEST_DETACHED=1 (docs/LEAD-AGENT.md → «Сироты»)");
    }

    @Test
    void orphansDetector_OnlyShows_AndChecksAreInPlace() {
        String detector = read(DETECTOR);
        assertFalse(
                detector.contains("taskkill") || detector.contains("Stop-Process") || detector.contains("kill -"),
                "Детектор сирот scripts/lead/orphans-win.sh обязан только показывать: слепой kill по шаблону "
                        + "гасит чужие прогоны параллельных исполнителей. Решение и kill — по PID и вручную");
        assertTrue(
                detector.contains("opencode") && detector.contains("test.sh"),
                "Детектор должен узнавать сироту по цепочке родителей до живого opencode и по test.sh/gradlew/java");
        String check = read(CHECK);
        assertTrue(
                check.contains("wait-cpu") && check.contains("parent-death") && check.contains("orphans-detector"),
                "Проверка scripts/testing/check-test-runner.sh обязана держать три опыта: ожидание без fork, "
                        + "смерть родителя гасит прогон, детектор показывает сирот и никого не убивает");
        String lead = read(LEAD);
        assertTrue(
                lead.contains("orphans-win.sh") && lead.contains("сирот"),
                "docs/LEAD-AGENT.md → «Сироты»: главный агент обязан знать, как увидеть сирот test.sh/gradlew/java "
                        + "с CPU и worktree, прежде чем гасить их по PID");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Не прочитан " + path, e);
        }
    }
}
