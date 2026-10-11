package com.theminespirit.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.guards.SourceFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class CoreIsolationTests {
    private static final Path CORE_SOURCES = Path.of("src/main/java/com/theminespirit/core");
    private static final Pattern GAME_IMPORT =
            Pattern.compile("^import\\s+(static\\s+)?(net\\.minecraft|net\\.neoforged|com\\.mojang)\\.", Pattern.MULTILINE);

    @Test
    void coreSources_Scanned_ImportNothingFromMinecraftOrNeoForge() {
        Map<Path, String> all = SourceFiles.javaUnder(SourceFiles.MAIN);
        List<Path> coreFiles = all.keySet().stream()
                .filter(path -> path.startsWith(CORE_SOURCES))
                .toList();
        assertTrue(Files.isDirectory(CORE_SOURCES),
                "Сторож не нашёл каталог core " + CORE_SOURCES + ": переименование пакета молча делает "
                        + "сторож зелёным на нуле файлов");
        assertTrue(!coreFiles.isEmpty(),
                "Сторож не нашёл ни одного файла в " + CORE_SOURCES + ": переименование пакета молча делает "
                        + "сторож зелёным на нуле файлов");

        List<String> offenders = coreFiles.stream()
                .filter(path -> GAME_IMPORT.matcher(all.get(path)).find())
                .map(Path::toString)
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
