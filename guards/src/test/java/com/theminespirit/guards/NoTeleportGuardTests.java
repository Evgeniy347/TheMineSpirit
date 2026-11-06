package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoTeleportGuardTests {
    private static final List<String> RELOCATION = List.of(".teleportTo(", "spirit.moveTo(", "player.moveTo(");
    private static final List<String> ALLOWED_SETUP =
            List.of("SpiritRespawnService.java", "SpiritSpawnCommand.java", "DemoService.java", "/gametest/");

    @Test
    void production_TeleportOrMoveToCalls_AreAbsent() {
        List<String> found = new ArrayList<>();
        SourceFiles.allMain().forEach((path, text) -> {
            String file = path.toString().replace(File.separatorChar, '/');
            if (allowed(file)) {
                return;
            }
            for (String needle : RELOCATION) {
                if (text.contains(needle)) {
                    found.add(file + " — " + needle);
                }
            }
        });
        assertEquals(
                List.of(),
                found,
                "Телепорт духа отключён решением владельца 02.11: позиция меняется только ходьбой. "
                        + "Для возврата используйте маршрут/хлебные крошки и расширенный поиск, "
                        + "а не teleportTo/moveTo. Разрешены только спавн/команды/демо/gametest. Найдено: " + found);
    }

    private static boolean allowed(String path) {
        return ALLOWED_SETUP.stream().anyMatch(path::contains);
    }
}
