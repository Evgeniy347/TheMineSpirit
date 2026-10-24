package com.theminespirit.core.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RespawnCellTests {
    private static final BlockCoord BASE = new BlockCoord(10, 64, 10);

    @Test
    void base_PlotAnchorAndHome_UsesPlotAnchor() {
        Optional<BlockCoord> plot = Optional.of(BASE);
        Optional<BlockCoord> home = Optional.of(new BlockCoord(1, 70, 1));

        assertEquals(
                plot,
                RespawnCell.base(plot, home),
                "Якорь участка — главная точка базы: дом духа не имеет права увести возрождение с участка поселения");
    }

    @Test
    void base_NoPlotAndHome_UsesHome() {
        Optional<BlockCoord> home = Optional.of(new BlockCoord(1, 70, 1));

        assertEquals(
                home,
                RespawnCell.base(Optional.empty(), home),
                "Без плана участка дух возрождается у своего дома: иначе возрождение некуда привязать");
    }

    @Test
    void base_NoPlotAndNoHome_ReturnsEmpty() {
        assertEquals(
                Optional.empty(),
                RespawnCell.base(Optional.empty(), Optional.empty()),
                "Без базы и дома возрождать некуда: запись не должна создаваться, дух не появится в случайной точке");
    }

    @Test
    void nearest_FreeBaseCell_ReturnsBase() {
        RespawnCell.Terrain terrain = terrain(Set.of(floor(BASE)), Set.of());

        assertEquals(
                Optional.of(BASE),
                RespawnCell.nearest(BASE, terrain),
                "Свободная клетка базы обязана побеждать: дух возрождается в точке базы, а не ищет другую без причины");
    }

    @Test
    void nearest_BaseOccupied_ReturnsNeighbourWithSolidFloorAndTwoAir() {
        BlockCoord neighbour = new BlockCoord(BASE.x() + 1, BASE.y(), BASE.z());
        RespawnCell.Terrain terrain = terrain(Set.of(floor(BASE), floor(neighbour)), Set.of(BASE));

        assertEquals(
                Optional.of(neighbour),
                RespawnCell.nearest(BASE, terrain),
                "Занятая клетка базы заменяется ближайшей свободной с твёрдым полом: иначе дух возродится в блоке");
    }

    @Test
    void nearest_OnlyOneAirAboveCell_IsRejected() {
        BlockCoord blockedAbove = new BlockCoord(BASE.x(), BASE.y() + 1, BASE.z());
        RespawnCell.Terrain terrain = terrain(Set.of(floor(BASE)), Set.of(blockedAbove));

        assertEquals(
                Optional.empty(),
                RespawnCell.nearest(BASE, terrain),
                "Клетка с одним воздухом над ногами не годится: дух ростом выше одного блока застрянет и задохнётся");
    }

    @Test
    void nearest_NoSolidFloor_ReturnsEmpty() {
        RespawnCell.Terrain terrain = terrain(Set.of(), Set.of());

        assertEquals(
                Optional.empty(),
                RespawnCell.nearest(BASE, terrain),
                "Без твёрдого пола свободной клетки нет: дух не может возродиться в воздухе и упасть");
    }

    @Test
    void nearest_TwoEqualDistanceCells_ReturnsLexicographicallyFirst() {
        BlockCoord positive = new BlockCoord(BASE.x() + 1, BASE.y(), BASE.z());
        BlockCoord negative = new BlockCoord(BASE.x() - 1, BASE.y(), BASE.z());
        RespawnCell.Terrain terrain = terrain(Set.of(floor(positive), floor(negative)), Set.of());

        assertEquals(
                Optional.of(negative),
                RespawnCell.nearest(BASE, terrain),
                "При равном расстоянии клетка выбирается по координатам: случайный порядок уводил бы духа в "
                        + "разные места после перезапуска");
    }

    @Test
    void nearest_NegativeRadius_Throws() {
        RespawnCell.Terrain terrain = terrain(Set.of(floor(BASE)), Set.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> RespawnCell.nearest(BASE, -1, terrain),
                "Отрицательный радиус — ошибка вызова: пустая окрестность не может считаться поиском");
    }

    private static BlockCoord floor(BlockCoord feet) {
        return new BlockCoord(feet.x(), feet.y() - 1, feet.z());
    }

    private static RespawnCell.Terrain terrain(Set<BlockCoord> floors, Set<BlockCoord> solids) {
        Set<BlockCoord> floorBlocks = new HashSet<>(floors);
        Set<BlockCoord> solidBlocks = new HashSet<>(solids);
        return new RespawnCell.Terrain(floorBlocks::contains, cell -> !solidBlocks.contains(cell));
    }
}
