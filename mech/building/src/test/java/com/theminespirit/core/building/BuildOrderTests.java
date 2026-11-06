package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BuildOrderTests {
    private static final BlockCoord ANCHOR = new BlockCoord(10, 0, 10);

    private static final class MemoryWorld implements BuildOrder.World {
        private final Set<BlockCoord> solid = new HashSet<>();
        private final Set<BlockCoord> fluid = new HashSet<>();

        MemoryWorld withSolid(BlockCoord... cells) {
            solid.addAll(List.of(cells));
            return this;
        }

        MemoryWorld withFluid(BlockCoord... cells) {
            fluid.addAll(List.of(cells));
            return this;
        }

        @Override
        public boolean solid(BlockCoord at) {
            return solid.contains(at);
        }

        @Override
        public boolean fluid(BlockCoord at) {
            return fluid.contains(at);
        }
    }

    private static List<BlockCoord> column(int x, int z, int fromY, int toY) {
        List<BlockCoord> cells = new ArrayList<>();
        for (int y = fromY; y <= toY; y++) {
            cells.add(new BlockCoord(x, y, z));
        }
        return cells;
    }

    private static List<BlockCoord> square(int y, int radius) {
        List<BlockCoord> cells = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                cells.add(new BlockCoord(x, y, z));
            }
        }
        return cells;
    }

    private static void assertLayerByLayer(List<BlockCoord> ordered) {
        int previous = Integer.MIN_VALUE;
        for (BlockCoord cell : ordered) {
            assertTrue(
                    cell.y() >= previous,
                    "Клетка " + cell + " идёт после слоя " + previous + ": верхний слой начат раньше нижнего");
            previous = cell.y();
        }
    }

    private static void assertSupported(List<BlockCoord> ordered, BuildOrder.World world) {
        Set<BlockCoord> earlier = new HashSet<>();
        for (BlockCoord cell : ordered) {
            BlockCoord below = new BlockCoord(cell.x(), cell.y() - 1, cell.z());
            assertTrue(
                    earlier.contains(below) || world.solid(below),
                    "Клетка " + cell + " ставится без опоры: под ней ни мира, ни ранее поставленной клетки");
            earlier.add(cell);
        }
    }

    @Test
    void fill_OneByOneFive_PlacesBottomUpWithoutFloatingColumn() {
        BuildOrder.Plan plan = BuildOrder.fill(
                column(0, 0, 0, 4), Set.of(), new MemoryWorld().withSolid(new BlockCoord(0, -1, 0)), ANCHOR);

        assertEquals(
                column(0, 0, 0, 4),
                plan.cells(),
                "Шахта 1×1 глубиной 5 засыпается снизу вверх: каждый блок ложится на предыдущий, а не столбом в воздухе");
        assertSupported(plan.cells(), new MemoryWorld().withSolid(new BlockCoord(0, -1, 0)));
        assertTrue(plan.complete(), "Пустая шахта без препятствий обязана планироваться целиком");
    }

    @Test
    void fill_ThreeByThreeCube_FinishesLayerBeforeNext() {
        List<BlockCoord> cells = new ArrayList<>();
        for (int y = 0; y < 3; y++) {
            cells.addAll(square(y, 1));
        }
        MemoryWorld world = new MemoryWorld();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                world.withSolid(new BlockCoord(x, -1, z));
            }
        }

        BuildOrder.Plan plan = BuildOrder.fill(cells, Set.of(), world, ANCHOR);

        assertEquals(27, plan.cells().size(), "Куб 3×3×3 обязан планироваться целиком, когда ничто не мешает");
        assertLayerByLayer(plan.cells());
        for (int y = 0; y < 3; y++) {
            int layer = y;
            assertEquals(
                    9,
                    plan.cells().stream().filter(cell -> cell.y() == layer).count(),
                    "Слой " + y + " обязан быть заполнен весь: иначе верхний слой ставится на дырявый низ");
        }
        assertSupported(plan.cells(), world);
    }

    @Test
    void fill_UnevenBottom_DeepestColumnGoesFirst() {
        List<BlockCoord> cells = new ArrayList<>();
        cells.addAll(column(0, 0, 0, 2));
        cells.addAll(column(1, 0, 2, 2));
        MemoryWorld world = new MemoryWorld().withSolid(new BlockCoord(0, -1, 0), new BlockCoord(1, 1, 0));

        BuildOrder.Plan plan = BuildOrder.fill(cells, Set.of(), world, ANCHOR);

        assertEquals(
                List.of(
                        new BlockCoord(0, 0, 0),
                        new BlockCoord(0, 1, 0),
                        new BlockCoord(0, 2, 0),
                        new BlockCoord(1, 2, 0)),
                plan.cells(),
                "Ступени разной глубины: сначала самая глубокая часть снизу вверх, затем мелкая на своём слое");
    }

    @Test
    void fill_WaterAtBottom_CoversFluidFirst() {
        List<BlockCoord> cells = new ArrayList<>(square(0, 1));
        cells.addAll(square(1, 1));
        MemoryWorld world = new MemoryWorld();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                world.withSolid(new BlockCoord(x, -1, z));
            }
        }
        BlockCoord water = new BlockCoord(1, 0, 1);
        world.withFluid(water);

        BuildOrder.Plan plan = BuildOrder.fill(cells, Set.of(), world, ANCHOR);

        assertEquals(
                water, plan.cells().get(0), "Воду на дне перекрывают первой клеткой нижнего слоя: иначе дух топит яму");
        assertLayerByLayer(plan.cells());
    }

    @Test
    void fill_SpiritInPit_LeavesItsCellAndStopsBeforeNextLayer() {
        List<BlockCoord> cells = new ArrayList<>(square(0, 1));
        cells.addAll(square(1, 1));
        MemoryWorld world = new MemoryWorld();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                world.withSolid(new BlockCoord(x, -1, z));
            }
        }
        BlockCoord feet = new BlockCoord(0, 0, 0);

        BuildOrder.Plan plan = BuildOrder.fill(cells, Set.of(feet), world, ANCHOR);

        assertFalse(plan.cells().contains(feet), "В клетку, где стоит дух, блок не ставят: иначе он замурует себя");
        assertEquals(
                8,
                plan.cells().stream().filter(cell -> cell.y() == 0).count(),
                "Вокруг духа нижний слой засыпается весь, кроме его клетки");
        assertTrue(
                plan.cells().stream().allMatch(cell -> cell.y() == 0),
                "Пока на нижнем слое осталась клетка духа, верхний слой не планируется: иначе слой перескочит");
    }

    @Test
    void fill_TwoSpirits_DoNotPlaceInEitherAndDoNotJumpLayers() {
        List<BlockCoord> cells = new ArrayList<>(square(0, 1));
        cells.addAll(square(1, 1));
        MemoryWorld world = new MemoryWorld();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                world.withSolid(new BlockCoord(x, -1, z));
            }
        }
        BlockCoord first = new BlockCoord(-1, 0, 0);
        BlockCoord second = new BlockCoord(1, 0, 0);

        BuildOrder.Plan plan = BuildOrder.fill(cells, Set.of(first, second), world, ANCHOR);

        assertFalse(plan.cells().contains(first), "Блок не ставят в клетку первого духа");
        assertFalse(plan.cells().contains(second), "Блок не ставят в клетку второго духа");
        assertEquals(7, plan.cells().size(), "Остальные клетки нижнего слоя планируются, клетки духов — нет");
        assertTrue(
                plan.cells().stream().allMatch(cell -> cell.y() == 0),
                "Два духа в нижнем слое не дают планировать верхний слой: сначала освобождаются их клетки");
    }

    @Test
    void fill_PartiallyFilled_StartsAtLowestEmptyLayer() {
        List<BlockCoord> cells = new ArrayList<>();
        for (int y = 0; y < 3; y++) {
            cells.addAll(square(y, 1));
        }
        MemoryWorld world = new MemoryWorld();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                world.withSolid(new BlockCoord(x, -1, z));
                world.withSolid(new BlockCoord(x, 0, z));
            }
        }

        BuildOrder.Plan plan = BuildOrder.fill(cells, Set.of(), world, ANCHOR);

        assertEquals(
                18,
                plan.cells().size(),
                "Засыпанный нижний слой не планируется снова: продолжают с самого нижнего пустого слоя");
        assertEquals(1, plan.cells().get(0).y(), "Первая клетка плана — нижний пустой слой y=1");
        assertLayerByLayer(plan.cells());
    }

    @Test
    void fill_CellWithoutFloor_NotPlanned() {
        BuildOrder.Plan plan = BuildOrder.fill(List.of(new BlockCoord(0, 5, 0)), Set.of(), new MemoryWorld(), ANCHOR);

        assertTrue(plan.cells().isEmpty(), "Клетка без опоры в плане не появляется: иначе получится висящий блок");
        assertEquals(
                List.of(new BlockCoord(0, 5, 0)),
                plan.blocked(),
                "Клетку без опоры план называет заблокированной: по ней видно, почему слой не закрыт");
    }

    @Test
    void fill_LiveCraterFixture_BottomUpAcrossNineColumns() {
        List<BlockCoord> cells = new ArrayList<>();
        for (int x = -198; x <= -196; x++) {
            for (int z = -526; z <= -524; z++) {
                for (int y = 63; y <= 67; y++) {
                    cells.add(new BlockCoord(x, y, z));
                }
            }
        }
        MemoryWorld world = new MemoryWorld();
        for (int x = -198; x <= -196; x++) {
            for (int z = -526; z <= -524; z++) {
                world.withSolid(new BlockCoord(x, 62, z));
            }
        }

        BuildOrder.Plan plan = BuildOrder.fill(cells, Set.of(), world, new BlockCoord(-197, 63, -525));

        assertEquals(45, plan.cells().size(), "Воронка 3×3×5 из снимка обязана планироваться целиком");
        assertEquals(63, plan.cells().get(0).y(), "Засыпка воронки начинается с самого глубокого слоя y=63");
        assertEquals(
                9,
                plan.cells().stream().filter(cell -> cell.y() == 63).count(),
                "Сначала весь нижний слой из девяти клеток, только потом выше");
        assertLayerByLayer(plan.cells());
        assertSupported(plan.cells(), world);
    }
}
