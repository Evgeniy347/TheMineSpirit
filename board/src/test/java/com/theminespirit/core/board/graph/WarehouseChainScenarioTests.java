package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.ChestClearance;
import com.theminespirit.core.board.SettlementStage;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.UpgradePlan;
import com.theminespirit.core.board.WarehouseApproval;
import com.theminespirit.core.board.WarehouseOrder;
import com.theminespirit.core.board.WarehouseRules;
import com.theminespirit.core.inventory.ContainerSnapshot;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WarehouseChainScenarioTests {
    private static final BlockCoord SITE = new BlockCoord(20, 64, 20);
    private static final BlockCoord WAREHOUSE_CELL = new BlockCoord(30, 64, 30);
    private static final int SLOTS = 27;

    @Test
    void stageTwoStage_StrategistOrder_MakesWarehouseTask() {
        Board board = stageOneDone();
        assertEquals(2, SettlementStage.of(board.ledger()), "Этап 1 закрыт — поселение переходит на этап 2");

        GraphWorld world = GraphWorld.settled();
        world.stage = 2;
        world.tools("spirit-1", 1);
        world.tools("spirit-2", 1);
        world.tools("spirit-3", 1);
        WarehouseApproval.apply(world.buildings, 0, order(2));
        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("building:warehouse").isPresent(),
                "После решения стратега граф ставит задание на склад: " + plan.tasks());
        assertFalse(
                plan.task("building:warehouse_2").isPresent(),
                "Второй уровень не ставится одновременно с первым: " + plan.tasks());
    }

    @Test
    void noStrategistOrder_WarehouseMissing_NoWarehouseTask() {
        GraphWorld world = GraphWorld.settled();
        world.stage = 2;
        WarehouseApproval.apply(world.buildings, 0, null);

        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("building:warehouse").isEmpty(),
                "Без решения стратега граф не строит склад сам: " + plan.tasks());
        assertTrue(
                plan.notices().stream().anyMatch(notice -> "warehouse".equals(notice.building())),
                "Склад без решения остаётся заявкой стратегу: " + plan.notices());
    }

    @Test
    void warehouseTask_StrategistOrder_HasMaterialsAndSite() {
        Map<String, Integer> materials = UpgradePlan.required(null, WarehouseRules.scheme(1));
        assertFalse(materials.isEmpty(), "Склад уровня 1 называет материалы по схеме");

        TaskNode plan = TicketPlans.plan(
                BoardCase.BUILD_WAREHOUSE, 1, TicketTarget.at(SITE).withScheme(WarehouseRules.scheme(1)));
        ScriptCall build = calls(plan).stream()
                .filter(call -> "build_schematic".equals(call.script()))
                .findFirst()
                .orElseThrow();

        assertEquals(WarehouseRules.scheme(1), build.params().get("file"), "Строится схема склада первого уровня");
        assertEquals(SITE.x(), build.params().get("x"), "Место строительства приходит из решения стратега");
    }

    @Test
    void stageTwoUpgrade_FirstLevelBuilt_SecondTaskAppears() {
        GraphWorld world = GraphWorld.settled();
        world.stage = 2;
        WarehouseApproval.apply(world.buildings, 1, order(2));

        GraphPlan plan = world.plan();

        assertTrue(
                plan.task("building:warehouse_2").isPresent(),
                "Первый уровень стоит — граф берёт второй: " + plan.tasks());
        assertTrue(plan.task("building:warehouse").isEmpty(), "Первый уровень заново не строится");
    }

    @Test
    void extraChests_ItemsMovedToWarehouse_CountsPreserved() {
        List<ContainerSnapshot> extra = new ArrayList<>();
        extra.add(chest(new Stack("dirt", 0, 5, 64), new Stack("oak_log", 0, 3, 64)));
        extra.add(chest(
                new Stack("cobblestone", 0, 64, 64), new Stack("cobblestone", 1, 6, 64), new Stack("torch", 0, 2, 64)));
        Map<String, Integer> before = contents(extra);
        List<Stack> warehouse = slots();

        for (ContainerSnapshot source : extra) {
            ContainerSnapshot chest = source;
            while (true) {
                ChestClearance.Decision decision = ChestClearance.decide(
                        chest, false, List.of(new ContainerSnapshot(WAREHOUSE_CELL, warehouse)), List.of(), 0);
                if (decision.action() == ChestClearance.Action.DRAIN) {
                    assertFalse(ChestClearance.empty(chest), "Вынос бывает только из непустого сундука");
                    for (ChestClearance.Move move : decision.moves()) {
                        store(warehouse, move.item(), move.count());
                    }
                    chest = emptyChest(chest);
                    continue;
                }
                assertEquals(
                        ChestClearance.Action.REMOVE,
                        decision.action(),
                        "После выноса сундук пуст и только тогда ломается");
                assertTrue(ChestClearance.empty(chest), "Ломать непустой сундук запрещено");
                break;
            }
        }

        assertEquals(
                before,
                contents(List.of(new ContainerSnapshot(WAREHOUSE_CELL, warehouse))),
                "Число предметов по типам в складе совпадает с исходным: вещи не потеряны");
    }

    @Test
    void fullWarehouse_Waits_ThenAddsChestAndDrains() {
        ContainerSnapshot source = chest(new Stack("dirt", 0, 5, 64));
        ContainerSnapshot full = full();
        ChestClearance.Decision wait = ChestClearance.decide(source, false, List.of(full), List.of(), 0);
        assertEquals(ChestClearance.Action.WAIT, wait.action(), "Полный склад без досок — снос ждёт");

        ChestClearance.Decision add = ChestClearance.decide(source, false, List.of(full), List.of(WAREHOUSE_CELL), 8);
        assertEquals(ChestClearance.Action.ADD_CHEST, add.action(), "Есть доски и клетка — сначала ставим сундук");

        List<Stack> warehouse = slots();
        warehouse.add(new Stack("cobblestone", 0, 64, 64));
        ContainerSnapshot empty = new ContainerSnapshot(WAREHOUSE_CELL, slots());
        ChestClearance.Decision drain = ChestClearance.decide(source, false, List.of(empty), List.of(), 0);
        assertEquals(ChestClearance.Action.DRAIN, drain.action(), "После нового сундука место есть — выносим");
    }

    private static Board stageOneDone() {
        Board board = new Board(() -> 0L);
        for (BoardCase step : List.of(
                BoardCase.CLEAR_GROUNDS,
                BoardCase.BUILD_MINE,
                BoardCase.BUILD_FENCE,
                BoardCase.BUILD_ORCHARD,
                BoardCase.BUILD_HOUSE,
                BoardCase.STOCK_CHESTS,
                BoardCase.CRAFT_FURNACE,
                BoardCase.LIGHTING,
                BoardCase.BUILD_STREETS)) {
            board.ledger().note(step, 1);
        }
        return board;
    }

    private static WarehouseOrder order(int level) {
        return new WarehouseOrder(level, SITE, "strategist", "пора ставить склад", 100);
    }

    private static ContainerSnapshot chest(Stack... stacks) {
        List<Stack> slots = slots();
        for (int index = 0; index < stacks.length; index++) {
            slots.set(index, stacks[index]);
        }
        return new ContainerSnapshot(new BlockCoord(1, 64, 1), slots);
    }

    private static ContainerSnapshot emptyChest(ContainerSnapshot chest) {
        return new ContainerSnapshot(chest.pos(), slots());
    }

    private static ContainerSnapshot full() {
        List<Stack> slots = slots();
        for (int index = 0; index < SLOTS; index++) {
            slots.set(index, new Stack("cobblestone", 0, 64, 64));
        }
        return new ContainerSnapshot(WAREHOUSE_CELL, slots);
    }

    private static List<Stack> slots() {
        List<Stack> slots = new ArrayList<>(SLOTS);
        for (int index = 0; index < SLOTS; index++) {
            slots.add(null);
        }
        return slots;
    }

    private static void store(List<Stack> slots, String item, int count) {
        int left = count;
        for (int index = 0; index < slots.size() && left > 0; index++) {
            Stack stack = slots.get(index);
            if (stack != null && stack.item().equals(item) && stack.freeSpace() > 0) {
                int part = Math.min(stack.freeSpace(), left);
                slots.set(index, stack.withCount(stack.count() + part));
                left -= part;
            }
        }
        for (int index = 0; index < slots.size() && left > 0; index++) {
            if (slots.get(index) == null) {
                int part = Math.min(64, left);
                slots.set(index, new Stack(item, 0, part, 64));
                left -= part;
            }
        }
        assertEquals(0, left, "Склад обязан вместить все вынесенные вещи");
    }

    private static Map<String, Integer> contents(List<ContainerSnapshot> chests) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ContainerSnapshot chest : chests) {
            for (Stack stack : chest.slots()) {
                if (stack != null) {
                    counts.merge(stack.item(), stack.count(), Integer::sum);
                }
            }
        }
        return Map.copyOf(counts);
    }

    private static List<ScriptCall> calls(TaskNode node) {
        List<ScriptCall> calls = new ArrayList<>();
        collect(node, calls);
        return List.copyOf(calls);
    }

    private static void collect(TaskNode node, List<ScriptCall> calls) {
        switch (node) {
            case ScriptCall call -> calls.add(call);
            case SequenceNode sequence -> sequence.steps().forEach(step -> collect(step, calls));
            default ->
                throw new IllegalArgumentException("Узел " + node.getClass().getSimpleName() + " не разбирается");
        }
    }
}
