package com.theminespirit.neoforge.log;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModLogTests {
    @Test
    void logsDirectory_RelativeWorldPath_PutsLogsBesideWorld() {
        Path world = Path.of(".", "world", ".");

        assertEquals(
                Path.of("").toAbsolutePath().normalize().resolve("logs"),
                ModLog.logsDirectory(world),
                "getWorldPath(ROOT) даёт относительный 'world/.': без toAbsolutePath логи уезжают в world/logs "
                        + "или падают, а поиск их не видит");
    }

    @Test
    void logsDirectory_AbsoluteWorldPath_StaysBesideWorld(@TempDir Path root) {
        Path world = root.resolve("world").resolve(".");

        assertEquals(
                root.resolve("logs"),
                ModLog.logsDirectory(world),
                "Абсолютный путь мира обязан давать logs рядом с миром, как latest.log");
    }
}
