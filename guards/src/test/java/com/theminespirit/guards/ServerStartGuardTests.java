package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ServerStartGuardTests {
    private static final Path COMMON = Path.of("server/scripts/common.sh");
    private static final Path START = Path.of("server/scripts/start.sh");
    private static final Path CHECK_START = Path.of("scripts/testing/check-start.sh");
    private static final Path CHKQ = Path.of("scripts/lead/chkq.sh");

    @Test
    void readyWait_LateLogRotation_IsHandled() {
        String common = read(COMMON);

        assertTrue(
                common.contains("size=\"$(log_size)\"") && common.contains("offset=0"),
                "wait_for_log обязан сбрасывать смещение в каждом тике ожидания, когда игра пересоздала "
                        + "latest.log позже первых секунд старта: иначе старый offset больше нового лога, строка "
                        + "«Done (» не находится, start.sh возвращает 1 при уже живом сервере и той же версии");
    }

    @Test
    void readyWait_Timeout_IsOverridableForCheck() {
        assertTrue(
                read(START).contains("START_READY_TIMEOUT"),
                "start.sh ждёт готовность в пределах START_READY_TIMEOUT: без переменной опыт ожидания не "
                        + "уложить в тест, а подмена таймаута ради зелёного — костыль");
    }

    @Test
    void checkStart_LateRotationScenario_IsPresent() {
        assertTrue(
                Files.isRegularFile(CHECK_START),
                "Нет scripts/testing/check-start.sh: нечем проверить, что поздняя ротация лога не ломает запуск");
        String check = read(CHECK_START);
        assertTrue(
                check.contains("rotate_late") && check.contains("FAKE_ROTATE_DELAY=5"),
                "Опыт check-start.sh потерял сценарий поздней ротации latest.log: без него гонка проверки "
                        + "готовности снова пройдёт незамеченной");
    }

    @Test
    void acceptance_CheckStart_IsRun() {
        assertTrue(
                read(CHKQ).contains("scripts/testing/check-start.sh"),
                "Приёмка scripts/lead/chkq.sh перестала запускать scripts/testing/check-start.sh: опыт поздней "
                        + "ротации latest.log снова держится только текстовым сторожем, а не настоящим прогоном "
                        + "на каждой принимаемой ветке (debtmisc21)");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new UncheckedIOException("Сторож не смог прочитать " + path, error);
        }
    }
}
