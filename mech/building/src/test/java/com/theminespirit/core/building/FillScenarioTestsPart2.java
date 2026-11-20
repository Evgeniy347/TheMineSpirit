package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.ClearOrder;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FillScenarioTestsPart2 {
    private static final BlockCoord ANCHOR = new BlockCoord(10, 0, 10);

    @Test
    void batches_LayersOverBatch_SplitWithoutBreakingLayer() {
        List<FillOrder.Box> layers = List.of(
                new FillOrder.Box(0, 60, 0, 9, 60, 9),
                new FillOrder.Box(0, 61, 0, 9, 61, 9),
                new FillOrder.Box(0, 62, 0, 9, 62, 9));

        assertEquals(
                List.of(200, 100),
                FillOrder.batches(layers, 256).stream().map(FillOrder::volume).toList(),
                "Слои по 100 при партии 256 — порции 200 и 100: слой не рвётся пополам"
                        + " Иначе тест batches_LayersOverBatch_SplitWithoutBreakingLayer перестанет ловить эту поломку. При смене batches_LayersOverBatch_SplitWithoutBreakingLayer проверь это утверждение.");
        assertEquals(
                300,
                FillOrder.volume(layers),
                "Объём порций совпадает с объёмом подсыпки: лишнего блока не берут"
                        + " Иначе тест batches_LayersOverBatch_SplitWithoutBreakingLayer перестанет ловить эту поломку. При смене batches_LayersOverBatch_SplitWithoutBreakingLayer проверь это утверждение.");
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
        assertTrue(
                fill.complete(),
                "Засыпка ямы заблокирована: " + fill.blocked()
                        + " Иначе тест district_BuildFillAndClearAtOnce_ThreeSpiritsFinishWithoutLeftovers перестанет ловить эту поломку. При смене district_BuildFillAndClearAtOnce_ThreeSpiritsFinishWithoutLeftovers проверь это утверждение.");

        int filled = 0;
        int built = 0;
        while (filled < fill.cells().size() || built < pillar.size() || !debris.isEmpty()) {
            if (filled < fill.cells().size()) {
                BlockCoord cell = fill.cells().get(filled++);
                assertFalse(
                        world.solid(cell),
                        "Засыпка ставит блок в уже занятую клетку " + cell
                                + " Иначе тест district_BuildFillAndClearAtOnce_ThreeSpiritsFinishWithoutLeftovers перестанет ловить эту поломку. При смене district_BuildFillAndClearAtOnce_ThreeSpiritsFinishWithoutLeftovers проверь это утверждение.");
                assertTrue(
                        world.solid(new BlockCoord(cell.x(), cell.y() - 1, cell.z())),
                        "Блок засыпки " + cell + " поставлен в воздух без опоры"
                                + " Иначе тест district_BuildFillAndClearAtOnce_ThreeSpiritsFinishWithoutLeftovers перестанет ловить эту поломку. При смене district_BuildFillAndClearAtOnce_ThreeSpiritsFinishWithoutLeftovers проверь это утверждение.");
                world.place(List.of(cell));
            }
            if (built < pillar.size()) {
                BlockCoord cell = pillar.get(built++);
                assertFalse(
                        world.solid(cell),
                        "Стойка ставится в занятую клетку " + cell
                                + " Иначе тест district_BuildFillAndClearAtOnce_ThreeSpiritsFinishWithoutLeftovers перестанет ловить эту поломку. При смене district_BuildFillAndClearAtOnce_ThreeSpiritsFinishWithoutLeftovers проверь это утверждение.");
                assertTrue(
                        world.solid(new BlockCoord(cell.x(), cell.y() - 1, cell.z())),
                        "Блок стойки " + cell + " висит без опоры: дух не поставит его честно"
                                + " Иначе тест district_BuildFillAndClearAtOnce_ThreeSpiritsFinishWithoutLeftovers перестанет ловить эту поломку. При смене district_BuildFillAndClearAtOnce_ThreeSpiritsFinishWithoutLeftovers проверь это утверждение.");
                world.place(List.of(cell));
            }
            if (!debris.isEmpty()) {
                List<BlockCoord> top = ClearOrder.topmost(debris, world::solid);
                assertFalse(
                        top.isEmpty(),
                        "Расчистка не нашла верхний блок в куче " + debris
                                + " Иначе тест district_BuildFillAndClearAtOnce_ThreeSpiritsFinishWithoutLeftovers перестанет ловить эту поломку. При смене district_BuildFillAndClearAtOnce_ThreeSpiritsFinishWithoutLeftovers проверь это утверждение.");
                BlockCoord removed = top.get(0);
                world.remove(removed);
                debris.remove(removed);
            }
            assertTrue(
                    world.solid(building),
                    "Стройка по соседству повреждена расчисткой или засыпкой"
                            + " Иначе тест district_BuildFillAndClearAtOnce_ThreeSpiritsFinishWithoutLeftovers перестанет ловить эту поломку. При смене district_BuildFillAndClearAtOnce_ThreeSpiritsFinishWithoutLeftovers проверь это утверждение.");
        }

        for (BlockCoord cell : dismantle) {
            assertTrue(
                    world.solid(cell),
                    "Разбор стойки не нашёл блок " + cell + ": временная опора осталась"
                            + " Иначе тест district_BuildFillAndClearAtOnce_ThreeSpiritsFinishWithoutLeftovers перестанет ловить эту поломку. При смене district_BuildFillAndClearAtOnce_ThreeSpiritsFinishWithoutLeftovers проверь это утверждение.");
            world.remove(cell);
        }

        assertTrue(
                FillOrder.filled(pit, world),
                "Яма не засыпана до конца: аудит мира не закроет работу"
                        + " Иначе тест district_BuildFillAndClearAtOnce_ThreeSpiritsFinishWithoutLeftovers перестанет ловить эту поломку. При смене district_BuildFillAndClearAtOnce_ThreeSpiritsFinishWithoutLeftovers проверь это утверждение.");
        assertTrue(
                debris.isEmpty(),
                "Куча расчищена не вся: " + debris
                        + " Иначе тест district_BuildFillAndClearAtOnce_ThreeSpiritsFinishWithoutLeftovers перестанет ловить эту поломку. При смене district_BuildFillAndClearAtOnce_ThreeSpiritsFinishWithoutLeftovers проверь это утверждение.");
        for (BlockCoord cell : pillar) {
            assertFalse(
                    world.solid(cell),
                    "Временный блок стойки " + cell + " остался в мире после разбора"
                            + " Иначе тест district_BuildFillAndClearAtOnce_ThreeSpiritsFinishWithoutLeftovers перестанет ловить эту поломку. При смене district_BuildFillAndClearAtOnce_ThreeSpiritsFinishWithoutLeftovers проверь это утверждение.");
        }
        assertTrue(
                world.solid(building),
                "Постройка поселения цела: соседние работы её не задели"
                        + " Иначе тест district_BuildFillAndClearAtOnce_ThreeSpiritsFinishWithoutLeftovers перестанет ловить эту поломку. При смене district_BuildFillAndClearAtOnce_ThreeSpiritsFinishWithoutLeftovers проверь это утверждение.");
        for (int x = 0; x <= 30; x++) {
            for (int z = 0; z <= 30; z++) {
                assertTrue(
                        world.solid(new BlockCoord(x, 63, z)),
                        "Пол участка " + x + "," + z + " разобран"
                                + " Иначе тест district_BuildFillAndClearAtOnce_ThreeSpiritsFinishWithoutLeftovers перестанет ловить эту поломку. При смене district_BuildFillAndClearAtOnce_ThreeSpiritsFinishWithoutLeftovers проверь это утверждение.");
            }
        }
    }
}
