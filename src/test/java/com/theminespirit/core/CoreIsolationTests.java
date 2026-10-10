package com.theminespirit.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class CoreIsolationTests {
    private static final Path CORE_SOURCES = Path.of("src/main/java/com/theminespirit/core");
    private static final Pattern GAME_IMPORT =
            Pattern.compile("^import\\s+(static\\s+)?(net\\.minecraft|net\\.neoforged|com\\.mojang)\\.", Pattern.MULTILINE);

    @Test
    void coreSources_Scanned_ImportNothingFromMinecraftOrNeoForge() throws IOException {
        List<String> offenders = javaFilesUnder(CORE_SOURCES).stream()
                .filter(CoreIsolationTests::importsGameClasses)
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

    private static List<Path> javaFilesUnder(Path root) throws IOException {
        assertTrue(Files.isDirectory(root), "Сторож не нашёл каталог " + root + ": тест запущен не из корня проекта");
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    private static boolean importsGameClasses(Path file) {
        try {
            return GAME_IMPORT.matcher(Files.readString(file)).find();
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать " + file, e);
        }
    }
}
