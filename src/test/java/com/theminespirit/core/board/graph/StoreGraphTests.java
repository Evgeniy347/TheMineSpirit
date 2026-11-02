package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StoreGraphTests {
    @Test
    void productionFragment_DefaultLoad_DeclaresStoreNodesAndStubs() {
        SupplyGraph graph = SupplyGraph.loadDefault();

        assertTrue(graph.items().containsKey("store:minimum"), "Фрагмент склада объявляет узел минимума");
        assertTrue(graph.items().containsKey("store:transfer"), "Фрагмент склада объявляет узел переноса");
        assertTrue(graph.items().containsKey("store:smelt"), "Фрагмент склада объявляет узел печи");
        assertTrue(graph.items().containsKey("store:space"), "Фрагмент склада объявляет узел места");
        assertTrue(graph.items().containsKey("store:sort"), "Фрагмент склада объявляет узел сортировки");
        assertTrue(graph.way("store_carry_deliver").isPresent(), "Путь сдать несомое объявлен");
        assertTrue(graph.way("store_carry_transfer").isPresent(), "Путь переноса из сундука A в B объявлен");
        assertTrue(graph.way("store_smelt_load").isPresent(), "Путь загрузки печи объявлен");
        assertTrue(graph.way("store_smelt_take").isPresent(), "Путь выемки из печи объявлен");
        assertTrue(graph.way("store_space").isPresent(), "Путь новой пары сундуков объявлен");
        assertTrue(graph.way("store_sort").isPresent(), "Путь сортировки сундуков объявлен");
        assertFalse(graph.implemented("store_carry_deliver"), "Перенос пока заглушка с TODO(store:transfer)");
        assertFalse(graph.implemented("store_smelt_load"), "Загрузка печи пока заглушка с TODO(store:smelt)");
        assertFalse(graph.implemented("store_sort"), "Сортировка пока заглушка с TODO(store:sort)");
    }

    @Test
    void plan_MinimumShortageAboveGraphThreshold_PostsGather() {
        GraphWorld world = GraphWorld.settled().with("cobblestone", 100);
        GraphPlan plan = plan(world, facts(Map.of("cobblestone", 160), Map.of("cobblestone", 100)));

        assertTrue(
                plan.task("mine_cobble").isPresent(),
                "Складу нужно 160 булыжника, в сундуках 100 (выше порога графа) — добыча обязана появиться: "
                        + plan.tasks());
    }

    @Test
    void plan_MinimumMet_NoStoreGather() {
        GraphWorld world = GraphWorld.settled().with("cobblestone", 160);
        GraphPlan plan = plan(world, facts(Map.of("cobblestone", 160), Map.of("cobblestone", 160)));

        assertTrue(plan.task("mine_cobble").isEmpty(), "Минимум склада закрыт — лишней добычи нет: " + plan.tasks());
    }

    @Test
    void plan_GroupMinimumShortage_PostsChop() {
        GraphWorld world = GraphWorld.settled().with("oak_log", 30);
        GraphPlan plan = plan(world, facts(Map.of("#logs", 80), Map.of("#logs", 30)));

        assertTrue(
                plan.task("chop_oak_log").isPresent(),
                "Минимум брёвен задан группой #logs — рубка обязана появиться: " + plan.tasks());
    }

    @Test
    void plan_ToolMinimumShortage_PostsCraft() {
        GraphWorld world = GraphWorld.settled();
        world.stage = 2;
        GraphPlan plan = plan(world, facts(Map.of("iron_axe", 1), Map.of("iron_axe", 0)));

        assertTrue(
                plan.task("craft_iron_axe").isPresent(),
                "Железного топора в запасе нет — крафт обязан появиться: " + plan.tasks());
    }

    @Test
    void plan_CarriedItemsWithFreeChest_PostsDeliver() {
        GraphFacts facts = new Facts()
                .spirit(new GraphFacts.Spirit(
                        "spirit-1",
                        new GraphFacts.Position(0, 64, 0),
                        20,
                        20,
                        300,
                        false,
                        false,
                        false,
                        null,
                        Map.of("minecraft:oak_log", 5),
                        List.of(),
                        List.of()))
                .chest(new GraphFacts.Chest(new GraphFacts.Position(1, 64, 1), Map.of(), 5, 27))
                .build();
        GraphPlan plan = plan(GraphWorld.settled(), facts);

        assertTrue(
                plan.task("store_carry_deliver").isPresent(),
                "Дух несёт вещи, в сундуке есть место — путь сдать несомое обязан появиться: " + plan.tasks());
    }

    @Test
    void plan_FullChestAndFreeChest_PostsTransfer() {
        GraphFacts facts = new Facts()
                .chest(new GraphFacts.Chest(new GraphFacts.Position(1, 64, 1), Map.of("minecraft:oak_log", 64), 0, 27))
                .chest(new GraphFacts.Chest(new GraphFacts.Position(2, 64, 1), Map.of(), 27, 27))
                .build();
        GraphPlan plan = plan(GraphWorld.settled(), facts);

        assertTrue(
                plan.task("store_carry_transfer").isPresent(),
                "Сундук A полон, у сундука B есть место — перенос обязан появиться: " + plan.tasks());
    }

    @Test
    void plan_FurnaceOutput_PostsTake() {
        GraphFacts facts = new Facts()
                .station(new GraphFacts.Station(
                        "furnace",
                        "furnace",
                        new GraphFacts.Position(3, 64, 3),
                        true,
                        "coal",
                        "raw_iron",
                        "iron_ingot"))
                .build();
        GraphPlan plan = plan(GraphWorld.settled(), facts);

        assertTrue(
                plan.task("store_smelt_take").isPresent(),
                "В печи есть готовое — путь забрать готовое обязан появиться: " + plan.tasks());
    }

    @Test
    void plan_FurnaceFree_PostsLoad() {
        GraphFacts facts = new Facts()
                .station(new GraphFacts.Station(
                        "furnace", "furnace", new GraphFacts.Position(3, 64, 3), false, "coal", null, null))
                .build();
        GraphPlan plan = plan(GraphWorld.settled(), facts);

        assertTrue(
                plan.task("store_smelt_load").isPresent(),
                "Печь свободна — путь загрузить печь обязан появиться: " + plan.tasks());
        assertTrue(plan.task("store_smelt_take").isEmpty(), "Забирать из свободной печи нечего: " + plan.tasks());
    }

    @Test
    void plan_StorageLow_PostsSpace() {
        GraphWorld world = GraphWorld.settled();
        world.storageFree = 5;
        GraphPlan plan = plan(world, new Facts().build());

        assertTrue(
                plan.task("store_space").isPresent(),
                "Свободно 5 % при пороге 20 % — новая пара сундуков обязана появиться: " + plan.tasks());
    }

    @Test
    void plan_MixedChest_PostsSort() {
        GraphFacts facts = new Facts()
                .chest(new GraphFacts.Chest(
                        new GraphFacts.Position(1, 64, 1),
                        Map.of("minecraft:oak_log", 10, "minecraft:cobblestone", 10),
                        5,
                        27))
                .build();
        GraphPlan plan = plan(GraphWorld.settled(), facts);

        assertTrue(
                plan.task("store_sort").isPresent(),
                "В сундуке дерево и камень вместе — сортировка обязана появиться: " + plan.tasks());
    }

    private static GraphPlan plan(GraphWorld world, GraphFacts facts) {
        return GraphPlanner.plan(world.graph, world.state().withFacts(facts));
    }

    private static GraphFacts facts(Map<String, Integer> minimum, Map<String, Integer> actual) {
        return new Facts().stageReqs(new GraphFacts.StageReqs(minimum, actual)).build();
    }

    private static final class Facts {
        private Map<String, GraphFacts.Spirit> spirits = Map.of();
        private List<GraphFacts.Chest> chests = List.of();
        private Map<String, GraphFacts.Station> stations = Map.of();
        private GraphFacts.StageReqs stageReqs = new GraphFacts.StageReqs(Map.of(), Map.of());

        Facts spirit(GraphFacts.Spirit spirit) {
            spirits = Map.of(spirit.id(), spirit);
            return this;
        }

        Facts chest(GraphFacts.Chest chest) {
            chests = new ArrayList<>(chests);
            chests.add(chest);
            return this;
        }

        Facts station(GraphFacts.Station station) {
            stations = Map.of(station.id(), station);
            return this;
        }

        Facts stageReqs(GraphFacts.StageReqs reqs) {
            stageReqs = reqs;
            return this;
        }

        GraphFacts build() {
            return new GraphFacts(
                    spirits,
                    List.of(),
                    chests,
                    List.of(),
                    stations,
                    Map.of("furnace", new GraphFacts.Building("furnace", 1, 100, 0, null, false)),
                    List.of(),
                    GraphFacts.Terrain.empty(),
                    stageReqs);
        }
    }
}
