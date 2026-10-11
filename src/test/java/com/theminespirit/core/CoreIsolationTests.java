package com.theminespirit.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.guards.SourceFiles;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class CoreIsolationTests {
    private static final Path CORE_SOURCES = Path.of("src/main/java/com/theminespirit/core");
    private static final Pattern GAME_IMPORT =
            Pattern.compile("^import\\s+(static\\s+)?(net\\.minecraft|net\\.neoforged|com\\.mojang)\\.", Pattern.MULTILINE);

    @Test
    void coreSources_Scanned_ImportNothingFromMinecraftOrNeoForge() {
        List<String> offenders = SourceFiles.javaUnder(SourceFiles.MAIN).entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(CORE_SOURCES))
                .filter(entry -> GAME_IMPORT.matcher(entry.getValue()).find())
                .map(entry -> entry.getKey().toString())
                .toList();

        assertEquals(
                List.of(),
                offenders,
                "Пакет core — быстрый путь: его тестирует чистый JUnit без запуска игры, и classpath тестов "
                        + "не содержит Minecraft/NeoForge. Код, которому нужна игра, переносится в другой пакет "
                        + "и проверяется GameTest (docs/TESTING.md)");
    }

    @Test
    void gameImportPattern_ImportOfMinecraftClass_IsDetected() {
        assertTrue(
                GAME_IMPORT.matcher("import net.minecraft.world.level.Level;").find(),
                "Сторож CoreIsolationTests ослеп: его шаблон не видит импорт Minecraft");
    }
}
