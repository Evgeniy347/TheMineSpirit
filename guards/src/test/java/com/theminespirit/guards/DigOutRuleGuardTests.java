package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DigOutRuleGuardTests {
    private static final List<String> TARGET_FILES = List.of(
            "neoforge/board/DigOutAction.java", "neoforge/board/DigOutPost.java", "neoforge/board/RescueTicket.java");
    private static final List<String> HEIGHT_LIFTS = List.of("SurfacePoint", "getHeight(", "Heightmap.");
    private static final String SUPPORT = "PlaceSupport.hasSupport(";
    private static final String PLACE = "placer.begin(";

    @Test
    void digOutTarget_HomePointChosen_NoHeightmapLift() {
        Map<String, String> sources = new LinkedHashMap<>();
        for (String name : TARGET_FILES) {
            sources.put(name, read(SourceFiles.path(name)));
        }

        assertEquals(
                List.of(),
                heightLifts(sources),
                "Цель dig_out и спасения — стоянка дома (HomeStand/WalkableStand). SurfacePoint/Heightmap "
                        + "поднимает её на верх колонки: над домом оказывается крыша склада (-171,80,-489), и дух "
                        + "лезет наверх вместо дома. Нарушители");
    }

    @Test
    void digOutFloor_Placement_CheckedForSupportFirst() {
        String text = read(SourceFiles.path("neoforge/DigOutFloor.java"));

        assertTrue(
                supportBeforePlace(text),
                "Ступень без опоры нельзя даже пытаться поставить: иначе в block_changes появляются пустые "
                        + "постановки air → air вокруг духа после каждого dig_out NONE. Сначала PlaceSupport, потом "
                        + "placer.begin");
    }

    @Test
    void heightLiftScan_HeightmapCall_IsReported() {
        Map<String, String> sources = Map.of(
                "neoforge/board/DigOutAction.java",
                "int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);");

        assertEquals(
                List.of("neoforge/board/DigOutAction.java: getHeight(", "neoforge/board/DigOutAction.java: Heightmap."),
                heightLifts(sources),
                "Сторож ослеп: высота колонки в цели dig_out обязана попадать в нарушители");
    }

    @Test
    void heightLiftScan_HomePoint_IsAccepted() {
        Map<String, String> sources = Map.of("neoforge/board/DigOutAction.java", "start(service, spirit, settlement);");

        assertEquals(
                List.of(),
                heightLifts(sources),
                "Сторож ложно срабатывает: стоянка дома как цель — это правило, а не нарушение");
    }

    @Test
    void floorScan_PlaceBeforeSupport_IsReported() {
        assertFalse(
                supportBeforePlace(
                        "placer.begin(cell, state, id); if (PlaceSupport.hasSupport(level, cell, state)) {}"),
                "Сторож ослеп: постановка до проверки опоры обязана попадать в нарушители");
    }

    @Test
    void floorScan_SupportBeforePlace_IsAccepted() {
        assertTrue(
                supportBeforePlace(
                        "if (!PlaceSupport.hasSupport(level, cell, state)) { return Optional.of(\"нет опоры\"); } "
                                + "return switch (placer.begin(cell, state, id)) {};"),
                "Сторож ложно срабатывает: проверка опоры перед постановкой — это правило");
    }

    private static List<String> heightLifts(Map<String, String> sources) {
        List<String> offenders = new ArrayList<>();
        sources.forEach((file, text) -> {
            for (String lift : HEIGHT_LIFTS) {
                if (text.contains(lift)) {
                    offenders.add(file + ": " + lift);
                }
            }
        });
        return offenders;
    }

    private static boolean supportBeforePlace(String text) {
        int support = text.indexOf(SUPPORT);
        int place = text.indexOf(PLACE);
        return support >= 0 && place > support;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new UncheckedIOException("Сторож не прочитал " + file + ": " + error.getMessage(), error);
        }
    }
}
