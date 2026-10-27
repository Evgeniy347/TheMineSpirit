package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.SettlementFacts;
import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GraphWiringTests {
    @Test
    void stock_SpruceSettlement_OwnLogsCountForeignLogsDoNot() {
        Map<String, Integer> stock = GraphNames.stock(
                Map.of("minecraft:spruce_log", 10, "oak_log", 7, "spruce_planks", 3), "spruce", Set.of());

        assertEquals(10, stock.get("oak_log"), "Брёвна породы поселения — узел oak_log графа: " + stock);
        assertEquals(3, stock.get("oak_planks"), "Доски породы поселения — узел oak_planks графа: " + stock);
        assertFalse(
                stock.containsKey("other:spruce_log") || stock.getOrDefault("oak_log", 0) > 10,
                "Дуб в еловом поселении — чужая порода: не закрывает нехватку брёвен постройки: " + stock);
    }

    @Test
    void stock_CharcoalBedsFood_FoldIntoGraphNodes() {
        Map<String, Integer> stock = GraphNames.stock(
                Map.of("coal", 2, "charcoal", 3, "red_bed", 1, "white_bed", 1, "bread", 4, "cooked_beef", 2),
                "oak",
                Set.of("minecraft:bread", "minecraft:cooked_beef"));

        assertEquals(5, stock.get("coal"), "Уголь и древесный уголь — один узел топлива факелов: " + stock);
        assertEquals(2, stock.get("bed"), "Кровати любого цвета — один узел bed: " + stock);
        assertEquals(6, stock.get(GraphNames.FOOD), "Еда — сумма съедобного по таблице сытности: " + stock);
    }

    @Test
    void item_GraphOakInBirchSettlement_SwapsToBirch() {
        assertEquals(
                "birch_log",
                GraphNames.item("oak_log", "birch"),
                "Задание графа «рубить oak_log» в берёзовом поселении рубит берёзу: граф знает одну породу");
    }

    @Test
    void kits_MixedTools_BestTierPerKind() {
        Map<String, Integer> kit = GraphKits.of(
                List.of("minecraft:wooden_pickaxe", "minecraft:stone_pickaxe", "golden_axe", "minecraft:stick"),
                GraphWorld.GRAPH);

        assertEquals(
                Map.of("pickaxe", 1, "axe", 0),
                kit,
                "Набор духа — лучший уровень каждого вида; золото считается деревом, палка — не инструмент");
    }

    @Test
    void notes_SameKeyWithinMinute_Suppressed() {
        GraphNotes notes = new GraphNotes();

        boolean first = notes.due("notice:warehouse", 100);
        boolean again = notes.due("notice:warehouse", 100 + GraphNotes.EVERY_TICKS - 1);
        boolean later = notes.due("notice:warehouse", 100 + GraphNotes.EVERY_TICKS);

        assertTrue(first && !again && later, "Просьба стратегу — не чаще раза в минуту: " + first + again + later);
    }

    @Test
    void tickets_UnimplementedAndInfeasible_NotPosted() {
        GraphWorld world = GraphWorld.settled().with("coal", 0).with("torch", 0);
        GraphPlan plan = world.plan();

        GraphTickets.Result result = GraphTickets.of(GraphWorld.GRAPH, world.state(), plan);

        assertTrue(
                result.post().stream()
                        .allMatch(ticket ->
                                ticket.task().feasible() && ticket.task().implemented()),
                "На доску идут только выполнимые и реализованные задания: " + result.post());
        assertTrue(
                plan.tasks().stream()
                        .filter(task -> task.implemented() && !task.feasible() && task.priority() > 0)
                        .allMatch(task -> result.live().contains(GraphTickets.id(task))),
                "Невыполнимое задание остаётся живой причиной: поставленный раньше тикет не снимается, пока граф "
                        + "его держит: " + result.live());
        assertTrue(
                result.post().stream().allMatch(ticket -> ticket.id().startsWith(GraphTickets.PREFIX)),
                "id заданий графа стабильны и с префиксом graph: — доска дедуплицирует по id: " + result.post());
    }

    @Test
    void tickets_ChopShortage_CountCoversThresholdGap() {
        GraphWorld world = GraphWorld.settled().with("oak_log", 0);

        GraphTickets.Result result = GraphTickets.of(GraphWorld.GRAPH, world.state(), world.plan());

        int threshold = GraphPlanner.threshold(GraphWorld.GRAPH.items().get("oak_log"), world.state());
        assertTrue(
                result.post().stream()
                        .anyMatch(ticket -> "graph:chop_log".equals(ticket.id())
                                && ticket.count() == Math.min(GraphTickets.COUNT_MAX, threshold)),
                "Рубка добирает запас до порога Xmin·P·S·K (" + threshold + "), не больше стака: " + result.post());
    }

    @Test
    void tickets_SpiritWithoutTools_ToolTaskCarriesSpirit() {
        GraphWorld world = GraphWorld.settled().tools("spirit-1", -1);

        GraphTickets.Result result = GraphTickets.of(GraphWorld.GRAPH, world.state(), world.plan());

        assertTrue(
                result.post().stream()
                        .anyMatch(ticket -> ticket.id().equals("graph:craft_wooden_pickaxe@spirit-1")
                                && ticket.task()
                                        .spirit()
                                        .filter("spirit-1"::equals)
                                        .isPresent()),
                "Кирка ставится духу без кирки адресно: набор у каждого духа свой (владелец 23.10): " + result.post());
    }

    @Test
    void plan_NoPickaxeAnywhere_ClearingWaitsPickaxeRaised() {
        GraphPlan plan = GraphWorld.empty()
                .built("crafting_table", Built.BUILT)
                .with("oak_planks", 16)
                .with("stick", 8)
                .plan();

        assertFalse(
                plan.task("building:clearing").map(GraphPlan.Task::feasible).orElse(true),
                "Без кирки выравнивание не начинается: срезать камень нечем: " + plan.task("building:clearing"));
        assertTrue(
                plan.task("craft_wooden_pickaxe@spirit-1")
                        .map(GraphPlan.Task::feasible)
                        .orElse(false),
                "Выравнивание ждёт кирку — деревянная кирка выполнима и стоит заданием: " + plan.tasks());
    }

    @Test
    void stageBuilt_FactsOfFreshPlot_OnlyTableBuilt() {
        SettlementFacts facts = new SettlementFacts(
                true,
                false,
                false,
                1,
                true,
                Set.of(),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                Set.of(BoardCase.STOCK_CHESTS),
                Set.of());

        Map<String, Built> built = StageBuilt.of(facts);

        assertEquals(Built.BUILT, built.get("crafting_table"), "Верстак стоит у дома — постройка готова: " + built);
        assertEquals(
                Built.PLANNED,
                built.get("chests"),
                "Сундуков двора меньше двух — двор не готов, даже если шаг в журнале сделан: " + built);
        assertEquals(StageBuilt.ids(), built.keySet(), "Каждая постройка этапа 1 получает состояние для графа");
    }
}
