package com.theminespirit.neoforge.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModLogTests {
    @Test
    void stop_WithoutStart_LeavesLogInactive() {
        ModLog.stop();
        assertFalse(
                ModLog.active(),
                "Остановка журнала до старта не должна падать: закрывать нечего"
                        + " Иначе тест stop_WithoutStart_LeavesLogInactive перестанет ловить эту поломку. При смене stop_WithoutStart_LeavesLogInactive проверь это утверждение.");
        ModLog.stop();
        assertFalse(
                ModLog.active(),
                "Повторная остановка журнала не должна падать: shutdown зовут и при отказе старта"
                        + " Иначе тест stop_WithoutStart_LeavesLogInactive перестанет ловить эту поломку. При смене stop_WithoutStart_LeavesLogInactive проверь это утверждение.");
    }

    @Test
    void logsDirectory_RelativeWorldPath_PutsLogsBesideWorld() {
        Path world = Path.of(".", "world", ".");

        assertEquals(
                Path.of("").toAbsolutePath().normalize().resolve("logs"),
                ModLog.logsDirectory(world),
                "getWorldPath(ROOT) даёт относительный 'world/.': без toAbsolutePath логи уезжают в world/logs "
                        + "или падают, а поиск их не видит"
                        + " Иначе тест logsDirectory_RelativeWorldPath_PutsLogsBesideWorld перестанет ловить эту поломку. При смене logsDirectory_RelativeWorldPath_PutsLogsBesideWorld проверь это утверждение.");
    }

    @Test
    void logsDirectory_AbsoluteWorldPath_StaysBesideWorld(@TempDir Path root) {
        Path world = root.resolve("world").resolve(".");

        assertEquals(
                root.resolve("logs"),
                ModLog.logsDirectory(world),
                "Абсолютный путь мира обязан давать logs рядом с миром, как latest.log"
                        + " Иначе тест logsDirectory_AbsoluteWorldPath_StaysBesideWorld перестанет ловить эту поломку. При смене logsDirectory_AbsoluteWorldPath_StaysBesideWorld проверь это утверждение.");
    }
}
