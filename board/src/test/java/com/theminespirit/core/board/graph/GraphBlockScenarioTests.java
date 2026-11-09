package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.GraphBlock;
import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphBlockScenarioTests {
    @Test
    void item_Blocked_NodeNotPostedAndProducerRaised() {
        GraphWorld calm = mining();
        assertTrue(
                task(calm.plan(), "mine_coal").feasible(),
                "Без блокировки узел выполним: " + calm.plan().tasks());
        int torchBefore = calm.plan().priority("craft_torch");

        GraphWorld blocked = mining().blocks(new GraphBlock("mine_coal", "", new GraphBlock.Item("torch", 1)));
        GraphPlan plan = blocked.plan();

        GraphPlan.Task coal = task(plan, "mine_coal");
        assertFalse(coal.feasible(), "Узел с нехваткой предмета не ставится повторно: " + coal);
        assertTrue(coal.blocked().contains("torch"), "Причина блокировки называет предмет: " + coal.blocked());
        assertTrue(
                plan.priority("craft_torch") > torchBefore,
                "Ключ (факел) поднят выше прежнего приоритета: " + plan.priority("craft_torch") + " против "
                        + torchBefore);
    }

    @Test
    void item_Satisfied_BlockClearedAndTaskReturns() {
        GraphWorld world = mining().blocks(new GraphBlock("mine_coal", "", new GraphBlock.Item("torch", 1)))
                .with("torch", 1);

        GraphPlan.Task coal = task(world.plan(), "mine_coal");

        assertTrue(coal.feasible(), "Предмет появился — условие снято, задача вернулась: " + coal);
        assertTrue(coal.blocked().isEmpty(), "Снятая блокировка не оставляет причину: " + coal.blocked());
    }

    @Test
    void tool_Blocked_NodeNotPostedAndSpiritToolRaised() {
        GraphWorld world = mining();
        world.kits.put("spirit-2", new HashMap<>());
        GraphPlan before = world.plan();
        int toolBefore = toolPriority(before, "spirit-2");

        GraphWorld blocked = mining();
        blocked.kits.put("spirit-2", new HashMap<>());
        blocked.blocks(new GraphBlock("mine_coal", "", new GraphBlock.Tool("pickaxe", 0, "spirit-2")));
        GraphPlan plan = blocked.plan();

        assertFalse(task(plan, "mine_coal").feasible(), "Без инструмента узел не ставится");
        assertTrue(
                toolPriority(plan, "spirit-2") > toolBefore,
                "Ключ — кирка конкретного духа: " + toolPriority(plan, "spirit-2") + " против " + toolBefore);
    }

    @Test
    void building_Blocked_NodeNotPostedAndBuildingRaised() {
        GraphWorld blocked = mining().blocks(new GraphBlock("mine_coal", "", new GraphBlock.Building("clearing")));
        GraphPlan plan = blocked.plan();

        assertFalse(task(plan, "mine_coal").feasible(), "Без постройки узел не ставится");
        assertTrue(plan.task("building:clearing").isPresent(), "Ключ — нужная постройка в плане: " + plan.tasks());
    }

    @Test
    void impossible_NoSource_NoticeAndNoAttempts() {
        GraphWorld blocked = mining().blocks(new GraphBlock("mine_coal", "", new GraphBlock.Item("unobtainium", 1)));
        GraphPlan plan = blocked.plan();

        assertFalse(task(plan, "mine_coal").feasible(), "Нет источника — попыток нет");
        assertTrue(
                plan.notices().stream()
                        .anyMatch(notice -> notice.reason().contains("невозможно")
                                && notice.reason().contains("unobtainium")),
                "План получает notice «невозможно»: " + plan.notices());
    }

    @Test
    void chain_FurnaceBlocked_FlowReachesCoalAndPickaxe() {
        GraphWorld world = GraphWorld.empty()
                .blocks(new GraphBlock("building:furnace", "", new GraphBlock.Item("cobblestone", 8)));
        GraphPlan plan = world.plan();

        assertFalse(task(plan, "building:furnace").feasible(), "Печь не ставится, пока нет камня");
        assertTrue(
                task(plan, "building:furnace").blocked().contains("cobblestone"),
                "Блокировка печи называет камень: "
                        + task(plan, "building:furnace").blocked());
        assertTrue(plan.task("mine_cobble").isPresent(), "Ключ первого шага — добыча камня");
        assertTrue(plan.task("building:mine").isPresent(), "Шахта нужна камню");
        assertTrue(
                plan.tasks().stream().anyMatch(task -> task.id().contains("pickaxe@spirit-1")),
                "Кирка нужна шахте: " + plan.tasks());
        assertTrue(
                plan.priority("building:mine") > 0 && plan.priority("mine_cobble") > 0,
                "Поток идёт до источников, а не обрывается на печи: " + plan.tasks());
    }

    @Test
    void otherNode_Blocked_RestOfWorkStaysFeasible() {
        GraphWorld world = mining().blocks(new GraphBlock("mine_coal", "", new GraphBlock.Item("torch", 1)));
        GraphPlan plan = world.plan();

        assertTrue(
                plan.tasks().stream().anyMatch(GraphPlan.Task::feasible),
                "Заблокирован один узел, остальная работа не простаивает: " + plan.tasks());
        assertTrue(
                plan.tasks().stream()
                        .filter(GraphPlan.Task::feasible)
                        .anyMatch(task -> !task.id().startsWith("explore:")),
                "Прочая работа остаётся выполнимой: " + plan.tasks());
    }

    @Test
    void holder_Stuck_BlocksNodeAndRaisesRescue() {
        GraphWorld world = mining();
        world.facts = GraphFacts.empty()
                .withSpirits(Map.of(
                        "spirit-1",
                        new GraphFacts.Spirit(
                                "spirit-1",
                                new GraphFacts.Position(0, 64, 0),
                                20.0f,
                                20.0f,
                                null,
                                20,
                                300,
                                false,
                                false,
                                true,
                                null,
                                Map.of(),
                                List.of(),
                                List.of(),
                                null)));
        world.blocks(new GraphBlock("mine_coal", "", new GraphBlock.Holder("spirit-1", true)));
        GraphPlan plan = world.plan();

        assertFalse(task(plan, "mine_coal").feasible(), "Задание застрявшего держателя не ставится");
        assertTrue(plan.priority("rescue_spirit") > 0, "Спасение держателя остаётся главным: " + plan.tasks());
    }

    @Test
    void severalSpirits_BlockedTools_BothKeysRaised() {
        GraphWorld world = mining();
        world.kits.put("spirit-2", new HashMap<>());
        world.kits.put("spirit-3", new HashMap<>());
        world.blocks(
                new GraphBlock("mine_coal", "", new GraphBlock.Tool("pickaxe", 0, "spirit-2")),
                new GraphBlock("mine_cobble", "", new GraphBlock.Tool("pickaxe", 0, "spirit-3")));
        GraphPlan plan = world.plan();

        assertFalse(task(plan, "mine_coal").feasible(), "Первая цель заблокирована");
        assertFalse(task(plan, "mine_cobble").feasible(), "Вторая цель заблокирована");
        assertTrue(toolPriority(plan, "spirit-2") > 0, "Ключ первого духа поднят");
        assertTrue(toolPriority(plan, "spirit-3") > 0, "Ключ второго духа поднят");
        assertEquals(
                List.of("spirit-1", "spirit-2", "spirit-3"),
                world.kits.keySet().stream().sorted().toList(),
                "Сцена держит несколько духов");
    }

    private static GraphWorld mining() {
        GraphWorld world = GraphWorld.empty();
        world.buildings.put("mine", Built.BUILT);
        world.tools("spirit-1", 1);
        world.stock.putAll(Map.of("oak_log", 8, "oak_planks", 4, "stick", 4));
        return world;
    }

    private static int toolPriority(GraphPlan plan, String spirit) {
        return plan.tasks().stream()
                .filter(task -> task.id().contains("pickaxe@") && task.id().endsWith("@" + spirit))
                .mapToInt(GraphPlan.Task::priority)
                .max()
                .orElse(-1);
    }

    private static GraphPlan.Task task(GraphPlan plan, String id) {
        return plan.task(id).orElseThrow(() -> new AssertionError("Нет задачи " + id + " в " + plan.tasks()));
    }
}
