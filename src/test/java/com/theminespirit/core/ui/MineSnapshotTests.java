package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.building.MineLayout;
import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MineSnapshotTests {
    private static final String STONE = "minecraft:stone";
    private static final String AIR = "minecraft:air";
    private static final MineLayout LAYOUT = new MineLayout(
            List.of(new BlockCoord(0, 4, 0), new BlockCoord(1, 3, 0)),
            List.of(new BlockCoord(0, 5, 0), new BlockCoord(1, 4, 0)),
            List.of(),
            new BlockCoord(0, 5, 0));
    private static final MineGeometry GEOMETRY = MineGeometry.of(LAYOUT, 2);
    private static final Map<BlockCoord, MineBlocks.Observed> WORLD = world();

    @Test
    void of_Slice_ShowsDescentFloorOreWaterAndLava() {
        MineSnapshot snapshot = MineSnapshot.of(GEOMETRY, 4, MineSnapshotTests::at);
        assertEquals(
                List.of(
                        new MineSnapshot.Cell(0, 0, MineSnapshot.Kind.FLOOR),
                        new MineSnapshot.Cell(0, 1, MineSnapshot.Kind.LUCK_ORE),
                        new MineSnapshot.Cell(1, 0, MineSnapshot.Kind.DESCENT),
                        new MineSnapshot.Cell(1, 1, MineSnapshot.Kind.FLOOR),
                        new MineSnapshot.Cell(2, 0, MineSnapshot.Kind.WATER),
                        new MineSnapshot.Cell(2, 1, MineSnapshot.Kind.ORE)),
                snapshot.cells(),
                "Срез на y=4: пол, спуск, вода, руда на удачу и обычная руда; глухая порода не рисуется");
    }

    @Test
    void of_LavaAtHeight_LavaCell() {
        MineSnapshot snapshot = MineSnapshot.of(GEOMETRY, 5, MineSnapshotTests::at);
        assertEquals(
                List.of(
                        new MineSnapshot.Cell(0, 0, MineSnapshot.Kind.DESCENT),
                        new MineSnapshot.Cell(1, 0, MineSnapshot.Kind.PASSAGE),
                        new MineSnapshot.Cell(1, 1, MineSnapshot.Kind.LAVA),
                        new MineSnapshot.Cell(2, 0, MineSnapshot.Kind.PASSAGE),
                        new MineSnapshot.Cell(2, 1, MineSnapshot.Kind.PASSAGE)),
                snapshot.cells(),
                "На y=5 в срезе видны продолжение спуска, проходы и лава");
    }

    @Test
    void of_PlanRects_PercentFromWorld() {
        MineSnapshot snapshot = MineSnapshot.of(GEOMETRY, MineSnapshot.ANY_Y, MineSnapshotTests::at);
        assertEquals(4, snapshot.y(), "Срез без высоты — нижняя ступень спуска");
        assertEquals(
                List.of(
                        new MineSnapshot.Rect(0, 0, 1, 0, MineSnapshot.DESCENT, 100),
                        new MineSnapshot.Rect(1, 0, 2, 1, MineSnapshot.TUNNEL, 50)),
                snapshot.rects(),
                "Спуск готов на 100% (4 из 4), ход — на 50% (4 пустых из 8)");
        assertEquals(
                5,
                MineSnapshot.of(GEOMETRY, 99, MineSnapshotTests::at).y(),
                "Высота за верхней границей шахты прижата к максимуму");
    }

    @Test
    void withMarkers_NewMarkers_KeepSliceAndRects() {
        MapMarker marker = new MapMarker(MapMarker.Kind.SPIRIT, "spirit-1", "", 1, 1);
        MineSnapshot snapshot = MineSnapshot.of(GEOMETRY, 4, MineSnapshotTests::at);
        MineSnapshot marked = snapshot.withMarkers(List.of(marker));
        assertEquals(snapshot.cells(), marked.cells(), "Метки не меняют срез шахты");
        assertEquals(snapshot.rects(), marked.rects(), "Метки не меняют постройки шахты");
        assertEquals(List.of(marker), marked.markers(), "Снимок несёт присланные метки");
    }

    private static MineBlocks.Observed at(BlockCoord cell) {
        return WORLD.getOrDefault(cell, new MineBlocks.Observed(STONE, false));
    }

    private static Map<BlockCoord, MineBlocks.Observed> world() {
        Map<BlockCoord, MineBlocks.Observed> world = new HashMap<>();
        world.put(new BlockCoord(0, 5, 0), new MineBlocks.Observed(AIR, true));
        world.put(new BlockCoord(1, 4, 0), new MineBlocks.Observed(AIR, true));
        world.put(new BlockCoord(1, 5, 0), new MineBlocks.Observed(AIR, true));
        world.put(new BlockCoord(2, 5, 0), new MineBlocks.Observed(AIR, true));
        world.put(new BlockCoord(2, 5, 1), new MineBlocks.Observed(AIR, true));
        world.put(new BlockCoord(0, 4, 0), new MineBlocks.Observed(STONE, false));
        world.put(new BlockCoord(1, 3, 0), new MineBlocks.Observed(STONE, false));
        world.put(new BlockCoord(0, 4, 1), new MineBlocks.Observed("minecraft:diamond_ore", false));
        world.put(new BlockCoord(2, 4, 0), new MineBlocks.Observed("minecraft:water", false));
        world.put(new BlockCoord(2, 4, 1), new MineBlocks.Observed("minecraft:coal_ore", false));
        world.put(new BlockCoord(1, 5, 1), new MineBlocks.Observed("minecraft:lava", false));
        return world;
    }
}
