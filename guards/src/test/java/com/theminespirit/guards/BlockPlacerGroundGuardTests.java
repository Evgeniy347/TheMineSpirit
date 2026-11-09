package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class BlockPlacerGroundGuardTests {
    private static final Pattern DEFAULT_GROUND =
            Pattern.compile("public\\s+BlockPlacer\\s*\\(\\s*SpiritEntity\\s+\\w+\\s*\\)");

    @Test
    void blockPlacer_DefaultGroundConstructor_IsAbsent() {
        String text = read("neoforge/BlockPlacer.java");

        assertFalse(
                DEFAULT_GROUND.matcher(text).find(),
                "У BlockPlacer снова появился конструктор без основания — новый нестроительный код молча "
                        + "получает право ставить блоки в план. Как надо: передавай PlotProtection.Ground явно, "
                        + "NONE для временного и нестроительного.");
        assertTrue(
                text.contains("public BlockPlacer(SpiritEntity spirit, PlotProtection.Ground ground)"),
                "Основание постановки обязано быть аргументом BlockPlacer: по нему защита отличает стройку "
                        + "от засыпки, моста и пахоты");
    }

    @Test
    void defaultGroundPattern_ConstructorWithoutGround_IsReported() {
        String sample = "public BlockPlacer(SpiritEntity spirit) { this(spirit, Ground.PLAN_BUILD); }";

        assertEquals(
                List.of("public BlockPlacer(SpiritEntity spirit)"),
                found(sample),
                "Сторож ослеп: конструктор BlockPlacer без основания обязан попасть в нарушители");
    }

    private static List<String> found(String text) {
        return DEFAULT_GROUND
                .matcher(text)
                .results()
                .map(match -> match.group())
                .toList();
    }

    private static String read(String logical) {
        try {
            return Files.readString(SourceFiles.path(logical), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Файл BlockPlacer не прочитан: " + error.getMessage(), error);
        }
    }
}
