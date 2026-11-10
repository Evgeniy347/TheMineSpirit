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
import java.util.Optional;
import java.util.Set;
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

    @Test
    void largest_SealedRoomNearer_ChoosesSettlementComponent() {
        Set<BlockCoord> sealed = Set.of(new BlockCoord(-169, 70, -490));
        Set<BlockCoord> settlement =
                Set.of(new BlockCoord(-175, 70, -493), new BlockCoord(-174, 70, -493), new BlockCoord(-173, 70, -493));

        assertEquals(
                Optional.of(new BlockCoord(-173, 70, -493)),
                HomeStand.largest(BASE, List.of(sealed, settlement)),
                "Дом не может оказаться в ближней запертой комнате: из двух компонент проходимости берут ту, где "
                        + "ходят духи поселения, иначе цель снова за преградой");
    }

    @Test
    void largest_SameSize_PicksNearestComponent() {
        Set<BlockCoord> far = Set.of(new BlockCoord(-171, 65, -489), new BlockCoord(-171, 65, -488));
        Set<BlockCoord> near = Set.of(new BlockCoord(-169, 70, -490), new BlockCoord(-168, 70, -490));

        assertEquals(
                Optional.of(new BlockCoord(-169, 70, -490)),
                HomeStand.largest(BASE, List.of(far, near)),
                "При равном числе клеток дом — ближайшая компонента: выбор не зависит от порядка духов");
    }

    @Test
    void largest_NoComponents_IsEmpty() {
        assertTrue(
                HomeStand.largest(BASE, List.of()).isEmpty(),
                "Нет проходимых компонент — нет домашней стоянки: выдуманную точку не подставляем");
    }

    @Test
    void largest_RememberedCellInComponent_KeepsHome() {
        Set<BlockCoord> component =
                Set.of(new BlockCoord(-175, 70, -493), new BlockCoord(-174, 70, -493), new BlockCoord(-173, 70, -493));
        BlockCoord remembered = new BlockCoord(-175, 70, -493);

        assertEquals(
                Optional.of(remembered),
                HomeStand.largest(BASE, List.of(component), Optional.of(remembered)),
                "Запомненная домашняя стоянка внутри проходимой компоненты остаётся домом, даже если ближе к центру "
                        + "есть другая клетка: иначе цель уже выданного задания дрейфует между пересборами");
    }

    @Test
    void largest_RememberedCellOutsideComponents_Recomputed() {
        Set<BlockCoord> component = Set.of(new BlockCoord(-173, 70, -493), new BlockCoord(-174, 70, -493));
        BlockCoord sealed = new BlockCoord(-168, 70, -492);

        assertEquals(
                Optional.of(new BlockCoord(-173, 70, -493)),
                HomeStand.largest(BASE, List.of(component), Optional.of(sealed)),
                "Запомненная стоянка, к которой духи больше не ходят (заперта), заменяется ближайшей клеткой "
                        + "проходимой компоненты: дом не остаётся за преградой");
    }

    @Test
    void largest_RememberedCellInSmallerComponent_KeepsReachableHome() {
        Set<BlockCoord> big =
                Set.of(new BlockCoord(-175, 70, -493), new BlockCoord(-174, 70, -493), new BlockCoord(-173, 70, -493));
        Set<BlockCoord> small = Set.of(new BlockCoord(-160, 70, -480), new BlockCoord(-159, 70, -480));
        BlockCoord remembered = new BlockCoord(-160, 70, -480);

        assertEquals(
                Optional.of(remembered),
                HomeStand.largest(BASE, List.of(big, small), Optional.of(remembered)),
                "Проходимость важнее размера компоненты: раз духи уже ходят к запомненной клетке, дом не "
                        + "переезжает в более крупную компоненту и не тянет за собой выданное задание");
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
