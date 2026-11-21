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
                "Без блокировки узел выполним: " + calm.plan().tasks()
                        + " Иначе тест item_Blocked_NodeNotPostedAndProducerRaised перестанет ловить эту поломку. При смене item_Blocked_NodeNotPostedAndProducerRaised проверь это утверждение.");
        int torchBefore = calm.plan().priority("craft_torch");

        GraphWorld blocked = mining().blocks(new GraphBlock("mine_coal", "", new GraphBlock.Item("torch", 1)));
        GraphPlan plan = blocked.plan();

        GraphPlan.Task coal = task(plan, "mine_coal");
        assertFalse(
                coal.feasible(),
                "Узел с нехваткой предмета не ставится повторно: " + coal
                        + " Иначе тест item_Blocked_NodeNotPostedAndProducerRaised перестанет ловить эту поломку. При смене item_Blocked_NodeNotPostedAndProducerRaised проверь это утверждение.");
        assertTrue(
                coal.blocked().contains("torch"),
                "Причина блокировки называет предмет: " + coal.blocked()
                        + " Иначе тест item_Blocked_NodeNotPostedAndProducerRaised перестанет ловить эту поломку. При смене item_Blocked_NodeNotPostedAndProducerRaised проверь это утверждение.");
        assertTrue(
                plan.priority("craft_torch") > torchBefore,
                "Ключ (факел) поднят выше прежнего приоритета: " + plan.priority("craft_torch") + " против "
                        + torchBefore
                        + " Иначе тест item_Blocked_NodeNotPostedAndProducerRaised перестанет ловить эту поломку. При смене item_Blocked_NodeNotPostedAndProducerRaised проверь это утверждение.");
    }

    @Test
    void item_Blocked_KeyTakesBlockedPlace() {
        GraphWorld calm = GraphWorld.settled().with("stick", 0).with("torch", 0);
        int torch = calm.plan().priority("craft_torch");

        GraphPlan plan = GraphWorld.settled()
                .with("stick", 0)
                .with("torch", 0)
                .blocks(new GraphBlock("craft_torch", "", new GraphBlock.Item("stick", 1)))
                .plan();

        assertTrue(
                plan.priority("craft_stick") > torch,
                "Ключ блокировки (палки) занимает место заблокированного факела: " + plan.priority("craft_stick")
                        + " против " + torch
                        + " Иначе тест item_Blocked_KeyTakesBlockedPlace перестанет ловить эту поломку. При смене item_Blocked_KeyTakesBlockedPlace проверь это утверждение.");
    }

    @Test
    void item_Satisfied_BlockClearedAndTaskReturns() {
        GraphWorld world = mining().blocks(new GraphBlock("mine_coal", "", new GraphBlock.Item("torch", 1)))
                .with("torch", 1);

        GraphPlan.Task coal = task(world.plan(), "mine_coal");

        assertTrue(
                coal.feasible(),
                "Предмет появился — условие снято, задача вернулась: " + coal
                        + " Иначе тест item_Satisfied_BlockClearedAndTaskReturns перестанет ловить эту поломку. При смене item_Satisfied_BlockClearedAndTaskReturns проверь это утверждение.");
        assertTrue(
                coal.blocked().isEmpty(),
                "Снятая блокировка не оставляет причину: " + coal.blocked()
                        + " Иначе тест item_Satisfied_BlockClearedAndTaskReturns перестанет ловить эту поломку. При смене item_Satisfied_BlockClearedAndTaskReturns проверь это утверждение.");
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

        assertFalse(
                task(plan, "mine_coal").feasible(),
                "Без инструмента узел не ставится"
                        + " Иначе тест tool_Blocked_NodeNotPostedAndSpiritToolRaised перестанет ловить эту поломку. При смене tool_Blocked_NodeNotPostedAndSpiritToolRaised проверь это утверждение.");
        assertTrue(
                toolPriority(plan, "spirit-2") > toolBefore,
                "Ключ — кирка конкретного духа: " + toolPriority(plan, "spirit-2") + " против " + toolBefore
                        + " Иначе тест tool_Blocked_NodeNotPostedAndSpiritToolRaised перестанет ловить эту поломку. При смене tool_Blocked_NodeNotPostedAndSpiritToolRaised проверь это утверждение.");
    }

    @Test
    void building_Blocked_NodeNotPostedAndBuildingRaised() {
        GraphWorld blocked = mining().blocks(new GraphBlock("mine_coal", "", new GraphBlock.Building("clearing")));
        GraphPlan plan = blocked.plan();

        assertFalse(
                task(plan, "mine_coal").feasible(),
                "Без постройки узел не ставится"
                        + " Иначе тест building_Blocked_NodeNotPostedAndBuildingRaised перестанет ловить эту поломку. При смене building_Blocked_NodeNotPostedAndBuildingRaised проверь это утверждение.");
        assertTrue(
                plan.task("building:clearing").isPresent(),
                "Ключ — нужная постройка в плане: " + plan.tasks()
                        + " Иначе тест building_Blocked_NodeNotPostedAndBuildingRaised перестанет ловить эту поломку. При смене building_Blocked_NodeNotPostedAndBuildingRaised проверь это утверждение.");
    }

    @Test
    void impossible_NoSource_NoticeAndNoAttempts() {
        GraphWorld blocked = mining().blocks(new GraphBlock("mine_coal", "", new GraphBlock.Item("unobtainium", 1)));
        GraphPlan plan = blocked.plan();

        assertFalse(
                task(plan, "mine_coal").feasible(),
                "Нет источника — попыток нет"
                        + " Иначе тест impossible_NoSource_NoticeAndNoAttempts перестанет ловить эту поломку. При смене impossible_NoSource_NoticeAndNoAttempts проверь это утверждение.");
        assertTrue(
                plan.notices().stream()
                        .anyMatch(notice -> notice.reason().contains("невозможно")
                                && notice.reason().contains("unobtainium")),
                "План получает notice «невозможно»: " + plan.notices()
                        + " Иначе тест impossible_NoSource_NoticeAndNoAttempts перестанет ловить эту поломку. При смене impossible_NoSource_NoticeAndNoAttempts проверь это утверждение.");
    }

    @Test
    void chain_FurnaceBlocked_FlowReachesCoalAndPickaxe() {
        GraphWorld world = GraphWorld.empty()
                .blocks(new GraphBlock("building:furnace", "", new GraphBlock.Item("cobblestone", 8)));
        GraphPlan plan = world.plan();

        assertFalse(
                task(plan, "building:furnace").feasible(),
                "Печь не ставится, пока нет камня"
                        + " Иначе тест chain_FurnaceBlocked_FlowReachesCoalAndPickaxe перестанет ловить эту поломку. При смене chain_FurnaceBlocked_FlowReachesCoalAndPickaxe проверь это утверждение.");
        assertTrue(
                task(plan, "building:furnace").blocked().contains("cobblestone"),
                "Блокировка печи называет камень: "
                        + task(plan, "building:furnace").blocked()
                        + " Иначе тест chain_FurnaceBlocked_FlowReachesCoalAndPickaxe перестанет ловить эту поломку. При смене chain_FurnaceBlocked_FlowReachesCoalAndPickaxe проверь это утверждение.");
        assertTrue(
                plan.task("mine_cobble").isPresent(),
                "Ключ первого шага — добыча камня"
                        + " Иначе тест chain_FurnaceBlocked_FlowReachesCoalAndPickaxe перестанет ловить эту поломку. При смене chain_FurnaceBlocked_FlowReachesCoalAndPickaxe проверь это утверждение.");
        assertTrue(
                plan.task("building:mine").isPresent(),
                "Шахта нужна камню"
                        + " Иначе тест chain_FurnaceBlocked_FlowReachesCoalAndPickaxe перестанет ловить эту поломку. При смене chain_FurnaceBlocked_FlowReachesCoalAndPickaxe проверь это утверждение.");
        assertTrue(
                plan.tasks().stream().anyMatch(task -> task.id().contains("pickaxe@spirit-1")),
                "Кирка нужна шахте: " + plan.tasks()
                        + " Иначе тест chain_FurnaceBlocked_FlowReachesCoalAndPickaxe перестанет ловить эту поломку. При смене chain_FurnaceBlocked_FlowReachesCoalAndPickaxe проверь это утверждение.");
        assertTrue(
                plan.raised("building:mine") && plan.raised("mine_cobble"),
                "Поток идёт до источников, а не обрывается на печи: " + plan.tasks()
                        + " Иначе тест chain_FurnaceBlocked_FlowReachesCoalAndPickaxe перестанет ловить эту поломку. При смене chain_FurnaceBlocked_FlowReachesCoalAndPickaxe проверь это утверждение.");
    }

    @Test
    void otherNode_Blocked_RestOfWorkStaysFeasible() {
        GraphWorld world = mining().blocks(new GraphBlock("mine_coal", "", new GraphBlock.Item("torch", 1)));
        GraphPlan plan = world.plan();

        assertTrue(
                plan.tasks().stream().anyMatch(GraphPlan.Task::feasible),
                "Заблокирован один узел, остальная работа не простаивает: " + plan.tasks()
                        + " Иначе тест otherNode_Blocked_RestOfWorkStaysFeasible перестанет ловить эту поломку. При смене otherNode_Blocked_RestOfWorkStaysFeasible проверь это утверждение.");
        assertTrue(
                plan.tasks().stream()
                        .filter(GraphPlan.Task::feasible)
                        .anyMatch(task -> !task.id().startsWith("explore:")),
                "Прочая работа остаётся выполнимой: " + plan.tasks()
                        + " Иначе тест otherNode_Blocked_RestOfWorkStaysFeasible перестанет ловить эту поломку. При смене otherNode_Blocked_RestOfWorkStaysFeasible проверь это утверждение.");
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

        assertFalse(
                task(plan, "mine_coal").feasible(),
                "Задание застрявшего держателя не ставится"
                        + " Иначе тест holder_Stuck_BlocksNodeAndRaisesRescue перестанет ловить эту поломку. При смене holder_Stuck_BlocksNodeAndRaisesRescue проверь это утверждение.");
        assertTrue(
                plan.raised("rescue_spirit"),
                "Спасение держателя остаётся главным: " + plan.tasks()
                        + " Иначе тест holder_Stuck_BlocksNodeAndRaisesRescue перестанет ловить эту поломку. При смене holder_Stuck_BlocksNodeAndRaisesRescue проверь это утверждение.");
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

        assertFalse(
                task(plan, "mine_coal").feasible(),
                "Первая цель заблокирована"
                        + " Иначе тест severalSpirits_BlockedTools_BothKeysRaised перестанет ловить эту поломку. При смене severalSpirits_BlockedTools_BothKeysRaised проверь это утверждение.");
        assertFalse(
                task(plan, "mine_cobble").feasible(),
                "Вторая цель заблокирована"
                        + " Иначе тест severalSpirits_BlockedTools_BothKeysRaised перестанет ловить эту поломку. При смене severalSpirits_BlockedTools_BothKeysRaised проверь это утверждение.");
        assertTrue(
                toolRaised(plan, "spirit-2"),
                "Ключ первого духа поднят"
                        + " Иначе тест severalSpirits_BlockedTools_BothKeysRaised перестанет ловить эту поломку. При смене severalSpirits_BlockedTools_BothKeysRaised проверь это утверждение.");
        assertTrue(
                toolRaised(plan, "spirit-3"),
                "Ключ второго духа поднят"
                        + " Иначе тест severalSpirits_BlockedTools_BothKeysRaised перестанет ловить эту поломку. При смене severalSpirits_BlockedTools_BothKeysRaised проверь это утверждение.");
        assertEquals(
                List.of("spirit-1", "spirit-2", "spirit-3"),
                world.kits.keySet().stream().sorted().toList(),
                "Сцена держит несколько духов"
                        + " При смене severalSpirits_BlockedTools_BothKeysRaised проверь это утверждение. Иначе поломка пройдёт незамеченной в прогоне.");
    }

    @Test
    void world_Blocked_LiftsOnlyWhenFactAppears() {
        GraphWorld world = mining().blocks(new GraphBlock("mine_coal", "", new GraphBlock.World("tree:oak")));
        world.world.remove("tree:oak");

        GraphPlan.Task blocked = task(world.plan(), "mine_coal");

        assertFalse(
                blocked.feasible(),
                "Породы в округе нет — рубка не ставится: " + blocked
                        + " Иначе тест world_Blocked_LiftsOnlyWhenFactAppears перестанет ловить эту поломку. При смене world_Blocked_LiftsOnlyWhenFactAppears проверь это утверждение.");
        assertTrue(
                blocked.blocked().contains("tree:oak"),
                "Блокировка называет факт мира, по которому снимется: " + blocked.blocked()
                        + " Иначе тест world_Blocked_LiftsOnlyWhenFactAppears перестанет ловить эту поломку. При смене world_Blocked_LiftsOnlyWhenFactAppears проверь это утверждение.");

        world.world.add("tree:oak");
        GraphPlan.Task back = task(world.plan(), "mine_coal");

        assertTrue(
                back.feasible(),
                "Дерево появилось в округе — условие снято, задача вернулась: " + back.blocked()
                        + " Иначе тест world_Blocked_LiftsOnlyWhenFactAppears перестанет ловить эту поломку. При смене world_Blocked_LiftsOnlyWhenFactAppears проверь это утверждение.");
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

    private static boolean toolRaised(GraphPlan plan, String spirit) {
        return plan.tasks().stream()
                .filter(task -> task.id().contains("pickaxe@") && task.id().endsWith("@" + spirit))
                .anyMatch(GraphPlan.Task::raised);
    }

    private static GraphPlan.Task task(GraphPlan plan, String id) {
        return plan.task(id).orElseThrow(() -> new AssertionError("Нет задачи " + id + " в " + plan.tasks()));
    }
}
