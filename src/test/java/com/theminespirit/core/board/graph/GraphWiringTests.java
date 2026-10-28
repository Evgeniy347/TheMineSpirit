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
    void stock_MixedSpecies_EachSpeciesOwnNode() {
        Map<String, Integer> stock =
                GraphNames.stock(Map.of("minecraft:spruce_log", 10, "oak_log", 7, "spruce_planks", 3), Set.of());

        assertEquals(
                Map.of("spruce_log", 10, "oak_log", 7, "spruce_planks", 3),
                stock,
                "Каждая порода — свой узел графа: ель не закрывает нехватку дуба и наоборот, «любая порода» — группа "
                        + "#logs/#planks графа");
    }

    @Test
    void stock_CoalsBedsFood_GraphNodes() {
        Map<String, Integer> stock = GraphNames.stock(
                Map.of("coal", 2, "charcoal", 3, "red_bed", 1, "white_bed", 1, "bread", 4, "cooked_beef", 2),
                Set.of("minecraft:bread", "minecraft:cooked_beef"));

        assertEquals(
                List.of(2, 3),
                List.of(stock.get("coal"), stock.get("charcoal")),
                "Каменный и древесный уголь — разные узлы, «любое топливо» — группа #coals графа: " + stock);
        assertEquals(2, stock.get("bed"), "Кровати любого цвета — один узел bed: " + stock);
        assertEquals(6, stock.get(GraphNames.FOOD), "Еда — сумма съедобного по таблице сытности: " + stock);
    }

    @Test
    void stock_MangrovePropagule_CountsAsGraphSapling() {
        Map<String, Integer> stock = GraphNames.stock(Map.of("minecraft:mangrove_propagule", 3), Set.of());

        assertEquals(
                3,
                stock.get("mangrove_sapling"),
                "Саженец мангрового дерева в игре — mangrove_propagule: без перевода в узел графа "
                        + "gather_mangrove_sapling никогда не закроет нехватку, а склад не увидит саженцы: " + stock);
    }

    @Test
    void item_GroupInMangroveSettlement_ResolvesToPropagule() {
        assertEquals(
                "mangrove_propagule",
                GraphNames.item(GraphWorld.GRAPH, "#saplings", "mangrove"),
                "Узел графа mangrove_sapling обязан превращаться в настоящий предмет mangrove_propagule: "
                        + "иначе задание доски просит несуществующий предмет");
    }

    @Test
    void item_GroupInBirchSettlement_ResolvesToBirchMember() {
        assertEquals(
                List.of("birch_log", "jungle_planks", "spruce_log"),
                List.of(
                        GraphNames.item(GraphWorld.GRAPH, "#logs", "birch"),
                        GraphNames.item(GraphWorld.GRAPH, "#planks", "jungle"),
                        GraphNames.item(GraphWorld.GRAPH, "spruce_log", "birch")),
                "Группа «любая порода» в задании доски — порода поселения; предмет определённой породы — как есть");
    }

    @Test
    void world_BlocksAndCreatures_TagsOfGraphWays() {
        Set<String> world = GraphNames.world(
                Set.of(
                        "jungle_log",
                        "minecraft:birch_log",
                        "stripped_oak_log",
                        "red_sand",
                        "clay",
                        "poppy",
                        "deepslate_diamond_ore",
                        "stone"),
                Set.of("animals", "sheep:black"));

        assertEquals(
                Set.of(
                        "tree:jungle",
                        "tree:birch",
                        "red_sand",
                        "clay",
                        "flower:poppy",
                        "ore:diamond_found",
                        "animals",
                        "sheep:black"),
                world,
                "Округа для путей графа: породы по брёвнам (ободранные — не дерево), красный песок отдельно, глина, "
                        + "цветы, найденная алмазная руда, живность; иначе путь «world» не активируется: " + world);
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
                        .filter(task -> !laterStage(world, task))
                        .allMatch(task -> result.live().contains(GraphTickets.id(task))),
                "Невыполнимое задание своего этапа остаётся живой причиной: поставленный раньше тикет не снимается, "
                        + "пока граф его держит: " + result.live());
        assertTrue(
                result.post().stream().allMatch(ticket -> ticket.id().startsWith(GraphTickets.PREFIX)),
                "id заданий графа стабильны и с префиксом graph: — доска дедуплицирует по id: " + result.post());
    }

    @Test
    void tickets_ChopShortage_CountCoversThresholdGap() {
        GraphWorld world = GraphWorld.settled().with("oak_log", 0);
        GraphWorld.GRAPH.buildings().values().stream()
                .filter(building -> !building.optional() && building.stage() <= world.stage)
                .forEach(building -> world.built(building.id(), Built.BUILT));

        GraphTickets.Result result = GraphTickets.of(GraphWorld.GRAPH, world.state(), world.plan());

        int threshold = GraphLack.threshold(GraphWorld.GRAPH.items().get("oak_log"), world.state());
        assertTrue(
                result.post().stream()
                        .anyMatch(ticket -> "graph:chop_oak_log".equals(ticket.id())
                                && ticket.count() == Math.min(GraphTickets.COUNT_MAX, threshold)),
                "Рубка добирает запас до порога Xmin·P·S·K (" + threshold + "), не больше стака: " + result.post());
    }

    @Test
    void tickets_BuildingBillBiggerThanThreshold_CountCoversRemainingBill() {
        GraphWorld world =
                GraphWorld.settled().with("oak_log", 40).with("oak_planks", 60).with("cobblestone", 64);
        world.materials.put("hut", Map.of("oak_log", 12, "oak_planks", 120, "cobblestone", 16));

        GraphTickets.Result result = GraphTickets.of(GraphWorld.GRAPH, world.state(), world.plan());

        assertTrue(
                result.post().stream()
                        .anyMatch(ticket -> "graph:craft_oak_planks".equals(ticket.id()) && ticket.count() >= 60),
                "Заготовка закрывает счёт постройки, а не только порог предмета: хижине нужно 120 досок, "
                        + "на складе 60 — крафт досок обязан идти на остаток 60, иначе стройка ждёт вечно: "
                        + result.post());
    }

    @Test
    void tickets_SpiritWithoutTools_ToolTaskCarriesSpirit() {
        GraphWorld world = GraphWorld.settled().tools("spirit-1", -1);

        GraphTickets.Result result = GraphTickets.of(GraphWorld.GRAPH, world.state(), world.plan());

        assertTrue(
                result.post().stream()
                        .anyMatch(ticket -> "graph:craft_wooden_pickaxe@spirit-1".equals(ticket.id())
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

    @Test
    void tickets_StageOneNoFood_NoForagingOfStageTwoCrops() {
        GraphWorld world = GraphWorld.settled().with("food", 0);

        GraphTickets.Result result = GraphTickets.of(GraphWorld.GRAPH, world.state(), world.plan());

        assertTrue(
                result.post().stream()
                        .noneMatch(ticket -> "carrot".equals(ticket.task().makes())),
                "Морковь — предмет этапа 2: на этапе 1 голод её не собирает, духи не тратят время на поиск грядок: "
                        + result.post());
    }

    private static boolean laterStage(GraphWorld world, GraphPlan.Task task) {
        SupplyGraph.Item item = GraphWorld.GRAPH.items().get(task.makes());
        return !task.id().contains(":")
                && item != null
                && item.stage() > world.state().stage();
    }

    @Test
    void stageBuilt_Ids_AllAreGraphBuildings() {
        assertTrue(
                GraphWorld.GRAPH.buildings().keySet().containsAll(StageBuilt.ids()),
                "Состояние постройки этапа 1 из фактов участка пишется под id графа; id без постройки в графе — "
                        + "постройка навсегда «не построена» (сад теперь orchard_<порода>): " + StageBuilt.ids());
    }
}
