package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.ClearOrder;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FillScenarioTests {
    private static final BlockCoord ANCHOR = new BlockCoord(10, 0, 10);

    private static final class MemoryWorld implements FillOrder.World {
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

        void place(List<BlockCoord> cells) {
            solid.addAll(cells);
        }
    }

    private static List<BlockCoord> pit(int minX, int minZ, int width, int depth, int fromY) {
        List<BlockCoord> cells = new ArrayList<>();
        for (int y = fromY; y < fromY + depth; y++) {
            for (int x = minX; x < minX + width; x++) {
                for (int z = minZ; z < minZ + width; z++) {
                    cells.add(new BlockCoord(x, y, z));
                }
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

    private static void assertSupported(List<BlockCoord> ordered, FillOrder.World world) {
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
    void fill_TwoPitsTwoSpirits_LayersSupportFluidAndAuditTogether() {
        List<BlockCoord> cells = new ArrayList<>(pit(0, 0, 3, 3, 64));
        cells.addAll(pit(6, 0, 2, 2, 64));
        MemoryWorld world = new MemoryWorld();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                world.withSolid(new BlockCoord(x, 63, z));
            }
        }
        for (int x = 6; x < 8; x++) {
            for (int z = 0; z < 2; z++) {
                world.withSolid(new BlockCoord(x, 63, z));
            }
        }
        BlockCoord water = new BlockCoord(0, 64, 0);
        BlockCoord firstSpirit = new BlockCoord(1, 64, 1);
        BlockCoord secondSpirit = new BlockCoord(6, 64, 0);
        world.withFluid(water);

        FillOrder.Plan plan = FillOrder.fill(cells, Set.of(firstSpirit, secondSpirit), world, ANCHOR);

        assertEquals(water, plan.cells().get(0), "Воду на дне перекрывают первой клеткой нижнего слоя");
        assertFalse(plan.cells().contains(firstSpirit), "В клетку первого духа блок не ставят");
        assertFalse(plan.cells().contains(secondSpirit), "В клетку второго духа блок не ставят");
        assertEquals(
                9 + 4 - 2,
                plan.cells().size(),
                "Пока духи стоят на нижнем слое, верхний слой не начинают: засыпают всё, кроме их клеток");
        assertTrue(
                plan.cells().stream().allMatch(cell -> cell.y() == 64),
                "Верхние слои ждут, пока освободятся клетки духов");
        assertLayerByLayer(plan.cells());
        assertSupported(plan.cells(), world);
        assertFalse(FillOrder.filled(cells, world), "Пока духи стоят в яме, готовность по миру не наступает");

        world.place(plan.cells());
        FillOrder.Plan second = FillOrder.fill(cells, Set.of(), world, ANCHOR);
        assertTrue(
                second.cells().containsAll(List.of(firstSpirit, secondSpirit)),
                "После ухода духов их клетки досыпаются, а на них встают верхние слои");
        assertEquals(
                27 + 8 - (9 + 4 - 2),
                second.cells().size(),
                "Досыпается всё, что осталось после нижнего слоя, кроме уже поставленного");
        assertLayerByLayer(second.cells());
        world.place(second.cells());
        assertTrue(FillOrder.filled(cells, world), "Все клетки ям твёрдые — аудит закрывает засыпку");
    }

    @Test
    void fill_PartialPit_ContinuesFromLowestEmptyLayerWithoutNewOrder() {
        List<BlockCoord> cells = pit(0, 0, 3, 3, 64);
        MemoryWorld world = new MemoryWorld();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                world.withSolid(new BlockCoord(x, 63, z), new BlockCoord(x, 64, z));
            }
        }

        FillOrder.Plan plan = FillOrder.fill(cells, Set.of(), world, ANCHOR);

        assertEquals(65, plan.cells().get(0).y(), "Засыпанный нижний слой не повторяют: начинают с нижнего пустого");
        assertEquals(
                18,
                plan.cells().size(),
                "Сначала засыпаются оба пустых слоя: каждый следующий ложится на закрытый нижний");
        assertEquals(
                9,
                plan.cells().stream().filter(cell -> cell.y() == 65).count(),
                "Сначала весь нижний пустой слой: верхний слой не начинают на дырявом низе");
        assertLayerByLayer(plan.cells());

        world.place(plan.cells().stream().filter(cell -> cell.y() == 65).toList());
        FillOrder.Plan next = FillOrder.fill(cells, Set.of(), world, ANCHOR);
        assertEquals(66, next.cells().get(0).y(), "Частичная засыпка продолжается следующей пустой прослойкой");
        assertEquals(9, next.cells().size(), "Остался ровно один слой на девять клеток");
        world.place(next.cells());
        assertTrue(FillOrder.filled(cells, world), "Частичная засыпка закрывается той же задачей по миру");
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

        FillOrder.Plan plan = FillOrder.fill(cells, Set.of(), world, new BlockCoord(-197, 63, -525));

        assertEquals(45, plan.cells().size(), "Воронка 3×3×5 из снимка обязана планироваться целиком");
        assertEquals(63, plan.cells().get(0).y(), "Засыпка воронки начинается с самого глубокого слоя y=63");
        assertEquals(
                9,
                plan.cells().stream().filter(cell -> cell.y() == 63).count(),
                "Сначала весь нижний слой из девяти клеток, только потом выше");
        assertLayerByLayer(plan.cells());
        assertSupported(plan.cells(), world);
    }

    @Test
    void layers_PitAndLevelingBoxes_GiveSameBottomUpRows() {
        List<FillOrder.Box> boxes = List.of(new FillOrder.Box(0, 64, 0, 2, 66, 2));

        List<FillOrder.Box> layers = FillOrder.layers(boxes);

        assertEquals(
                List.of(
                        new FillOrder.Box(0, 64, 0, 2, 64, 2),
                        new FillOrder.Box(0, 65, 0, 2, 65, 2),
                        new FillOrder.Box(0, 66, 0, 2, 66, 2)),
                layers,
                "Подсыпка участка и засыпка ямы режутся на одни и те же слои снизу вверх");
    }

    @Test
    void batches_LayersOverBatch_SplitWithoutBreakingLayer() {
        List<FillOrder.Box> layers = List.of(
                new FillOrder.Box(0, 60, 0, 9, 60, 9),
                new FillOrder.Box(0, 61, 0, 9, 61, 9),
                new FillOrder.Box(0, 62, 0, 9, 62, 9));

        assertEquals(
                List.of(200, 100),
                FillOrder.batches(layers, 256).stream().map(FillOrder::volume).toList(),
                "Слои по 100 при партии 256 — порции 200 и 100: слой не рвётся пополам");
        assertEquals(
                300, FillOrder.volume(layers), "Объём порций совпадает с объёмом подсыпки: лишнего блока не берут");
    }

    @Test
    void district_BuildFillAndClearAtOnce_ThreeSpiritsFinishWithoutLeftovers() {
        DistrictWorld world = new DistrictWorld();
        world.floor(0, 0, 30, 30, 63);
        BlockCoord building = new BlockCoord(24, 64, 24);
        world.place(List.of(building));

        List<BlockCoord> pit = new ArrayList<>();
        for (int y = 64; y <= 65; y++) {
            for (int x = 10; x <= 12; x++) {
                for (int z = 10; z <= 12; z++) {
                    pit.add(new BlockCoord(x, y, z));
                }
            }
        }
        List<BlockCoord> debris = new ArrayList<>(List.of(
                new BlockCoord(20, 64, 20),
                new BlockCoord(20, 65, 20),
                new BlockCoord(20, 66, 20),
                new BlockCoord(21, 64, 20)));
        world.place(debris);
        BlockCoord pillarBase = new BlockCoord(16, 64, 16);
        List<BlockCoord> pillar = PillarPlan.plan(4).stream()
                .map(cell -> new BlockCoord(pillarBase.x() + cell.x(), pillarBase.y() + cell.y(), pillarBase.z()))
                .toList();
        List<BlockCoord> dismantle = PillarPlan.dismantle(4).stream()
                .map(cell -> new BlockCoord(pillarBase.x() + cell.x(), pillarBase.y() + cell.y(), pillarBase.z()))
                .toList();

        FillOrder.Plan fill = FillOrder.fill(pit, Set.of(new BlockCoord(13, 64, 13)), world, ANCHOR);
        assertTrue(fill.complete(), "Засыпка ямы заблокирована: " + fill.blocked());

        int filled = 0;
        int built = 0;
        while (filled < fill.cells().size() || built < pillar.size() || !debris.isEmpty()) {
            if (filled < fill.cells().size()) {
                BlockCoord cell = fill.cells().get(filled++);
                assertFalse(world.solid(cell), "Засыпка ставит блок в уже занятую клетку " + cell);
                assertTrue(
                        world.solid(new BlockCoord(cell.x(), cell.y() - 1, cell.z())),
                        "Блок засыпки " + cell + " поставлен в воздух без опоры");
                world.place(List.of(cell));
            }
            if (built < pillar.size()) {
                BlockCoord cell = pillar.get(built++);
                assertFalse(world.solid(cell), "Стойка ставится в занятую клетку " + cell);
                assertTrue(
                        world.solid(new BlockCoord(cell.x(), cell.y() - 1, cell.z())),
                        "Блок стойки " + cell + " висит без опоры: дух не поставит его честно");
                world.place(List.of(cell));
            }
            if (!debris.isEmpty()) {
                List<BlockCoord> top = ClearOrder.topmost(debris, world::solid);
                assertFalse(top.isEmpty(), "Расчистка не нашла верхний блок в куче " + debris);
                BlockCoord removed = top.get(0);
                world.remove(removed);
                debris.remove(removed);
            }
            assertTrue(world.solid(building), "Стройка по соседству повреждена расчисткой или засыпкой");
        }

        for (BlockCoord cell : dismantle) {
            assertTrue(world.solid(cell), "Разбор стойки не нашёл блок " + cell + ": временная опора осталась");
            world.remove(cell);
        }

        assertTrue(FillOrder.filled(pit, world), "Яма не засыпана до конца: аудит мира не закроет работу");
        assertTrue(debris.isEmpty(), "Куча расчищена не вся: " + debris);
        for (BlockCoord cell : pillar) {
            assertFalse(world.solid(cell), "Временный блок стойки " + cell + " остался в мире после разбора");
        }
        assertTrue(world.solid(building), "Постройка поселения цела: соседние работы её не задели");
        for (int x = 0; x <= 30; x++) {
            for (int z = 0; z <= 30; z++) {
                assertTrue(world.solid(new BlockCoord(x, 63, z)), "Пол участка " + x + "," + z + " разобран");
            }
        }
    }
}
