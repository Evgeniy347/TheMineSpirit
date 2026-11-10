package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import org.junit.jupiter.api.Test;

class HomeStandLiveTests {
    private static final BlockCoord BASE = new BlockCoord(-171, 71, -489);
    private static final BlockCoord STAND = new BlockCoord(-169, 70, -490);

    @Test
    void home_MedianOfHomesInsideWarehouseWall_UsesWalkableStand() throws IOException {
        Map<BlockCoord, String> cells = load("/live/home-warehouse-1111.live");
        List<BlockCoord> homes =
                List.of(new BlockCoord(-173, 71, -489), new BlockCoord(-171, 71, -489), new BlockCoord(-169, 71, -489));

        BlockCoord home = HomeStand.of(homes, BASE, cell -> standable(cells, cell));

        assertEquals(
                STAND,
                home,
                "Середина домов попала в стену склада (-171,71,-489): цель задания обязана стать ближайшей "
                        + "стоянкой, иначе каждое «иди домой» падает «ни одной стоянки рядом, а цель занята блоком»");
        assertTrue(standable(cells, home), "Выбранная домашняя точка обязана быть стоянкой в живом мире фикстуры");
    }

    @Test
    void home_WithoutHomes_UsesWalkableStand() throws IOException {
        Map<BlockCoord, String> cells = load("/live/home-warehouse-1111.live");

        assertEquals(
                STAND,
                HomeStand.of(List.of(), BASE, cell -> standable(cells, cell)),
                "Без домов дом считают от якоря этапа: якорь под стеной склада тоже обязан уступить стоянке");
    }

    @Test
    void home_CentreAlreadyWalkable_KeepsCentre() {
        assertEquals(
                BASE,
                HomeStand.of(List.of(BASE), BASE, BASE::equals),
                "Свободная домашняя точка не двигается: ближайшая стоянка — она сама");
    }

    @Test
    void home_HomeInsideWarehouseWall_IsNotReturnedAsIs() throws IOException {
        Map<BlockCoord, String> cells = load("/live/home-warehouse-1111.live");

        BlockCoord home = HomeStand.at(new BlockCoord(-171, 71, -489), cell -> standable(cells, cell));

        assertNotEquals(
                BASE,
                home,
                "HomePoint духа, чей дом оказался в стене склада, обязан вернуть стоянку, а не занятую клетку");
    }

    @Test
    void home_AnchorFree_KeepsAnchor() {
        assertEquals(
                BASE,
                HomeStand.of(List.of(), BASE, cell -> true),
                "Свободный якорь остаётся домом: соседняя стоянка ищется только когда сам дом занят");
    }

    private static boolean standable(Map<BlockCoord, String> cells, BlockCoord feet) {
        return "AIR".equals(kind(cells, feet))
                && "AIR".equals(kind(cells, above(feet)))
                && !passable(kind(cells, below(feet)));
    }

    private static boolean passable(String kind) {
        return "AIR".equals(kind) || "WATER".equals(kind) || "LAVA".equals(kind);
    }

    private static String kind(Map<BlockCoord, String> cells, BlockCoord cell) {
        return cells.getOrDefault(cell, "AIR");
    }

    private static BlockCoord above(BlockCoord cell) {
        return new BlockCoord(cell.x(), cell.y() + 1, cell.z());
    }

    private static BlockCoord below(BlockCoord cell) {
        return new BlockCoord(cell.x(), cell.y() - 1, cell.z());
    }

    private static Map<BlockCoord, String> load(String resource) throws IOException {
        try (InputStream stream = HomeStandLiveTests.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Нет живой фикстуры: " + resource);
            }
            return parse(new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)), resource);
        }
    }

    private static Map<BlockCoord, String> parse(BufferedReader reader, String source) throws IOException {
        Map<BlockCoord, String> cells = new HashMap<>();
        String line;
        int number = 0;
        while ((line = reader.readLine()) != null) {
            number++;
            if (line.isBlank() || line.startsWith("#") || line.startsWith("feet")) {
                continue;
            }
            StringTokenizer parts = new StringTokenizer(line);
            if (parts.countTokens() != 4) {
                throw new IllegalArgumentException(source + ":" + number + ": строка не из четырёх полей: " + line);
            }
            BlockCoord offset = new BlockCoord(
                    Integer.parseInt(parts.nextToken()),
                    Integer.parseInt(parts.nextToken()),
                    Integer.parseInt(parts.nextToken()));
            cells.put(BASE.plus(offset), parts.nextToken());
        }
        return cells;
    }
}
