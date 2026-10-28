package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.perception.Terrain;
import com.theminespirit.core.perception.TerrainView;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.adapter.Visibility;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class BlockSightTests {
    private static final Point EYE = new Point(0.5, 2.5, 0.5);
    private static final BlockCoord TARGET = new BlockCoord(5, 2, 0);

    @Test
    void visible_DirectLine_Clear() {
        assertEquals(
                Visibility.CLEAR,
                BlockSight.visible(EYE, TARGET, new Air(), cell -> false),
                "Блок в прямой видимости виден: иначе дух откажется ломать то, что перед глазами");
    }

    @Test
    void visible_BlockBehindWall_Blocked() {
        Air world = new Air();
        world.solid(new BlockCoord(2, 2, 0));

        assertEquals(
                Visibility.BLOCKED,
                BlockSight.visible(EYE, TARGET, world, cell -> false),
                "Блок за стеной не виден: дух не ломает сквозь стену, как игрок");
    }

    @Test
    void visible_LogBehindLeaves_Clear() {
        BlockCoord leaves = new BlockCoord(2, 2, 0);
        Air world = new Air();
        world.solid(leaves);
        Predicate<BlockCoord> passable = leaves::equals;

        assertEquals(
                Visibility.CLEAR,
                BlockSight.visible(EYE, TARGET, world, passable),
                "Бревно за листвой при рубке видно: листва не считается стеной, иначе дерево не срубить");
    }

    @Test
    void visible_LogBehindStone_Blocked() {
        Air world = new Air();
        world.solid(new BlockCoord(2, 2, 0));

        assertEquals(
                Visibility.BLOCKED,
                BlockSight.visible(EYE, TARGET, world, cell -> false),
                "Бревно за камнем не видно даже при рубке: сквозь твёрдый блок рубить нельзя");
    }

    @Test
    void visible_UnloadedCell_Unknown() {
        Air world = new Air();
        world.unloaded(new BlockCoord(2, 2, 0));

        assertEquals(
                Visibility.UNKNOWN,
                BlockSight.visible(EYE, TARGET, world, cell -> false),
                "Незагруженная клетка на луче — не догадка: видимость неизвестна, ломать нельзя");
    }

    private static final class Air implements TerrainView {
        private final Map<BlockCoord, Terrain> cells = new HashMap<>();

        void solid(BlockCoord cell) {
            cells.put(cell, Terrain.SOLID);
        }

        void unloaded(BlockCoord cell) {
            cells.put(cell, Terrain.UNLOADED);
        }

        @Override
        public Terrain at(BlockCoord coord) {
            return cells.getOrDefault(coord, Terrain.AIR);
        }
    }
}
