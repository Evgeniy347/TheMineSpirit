package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphState;
import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class More6InputsTests {
    @Test
    void events_AddToBase_WithoutLosingThem() {
        GraphState state = state(GraphFacts.empty(), Map.of(), Map.of());

        GraphState next = More6Inputs.events(state, Set.of("more6_spirits_short", "more6_spirit_idle"));

        assertTrue(next.events().contains("night"), "Вход области не теряет события других сборщиков");
        assertTrue(next.events().contains("more6_spirits_short"), "Свободное жильё добавляется к событиям");
        assertTrue(next.events().contains("more6_spirit_idle"), "Простой добавляется к событиям");
        assertEquals(state.facts(), next.facts(), "Сборщик не подменяет факты графа");
    }

    @Test
    void carried_NamespacedInventory_NormalizedAndSummed() {
        GraphFacts facts = new GraphFacts(
                Map.of(
                        "one", spirit("one", null, Map.of("minecraft:oak_log", 3, "minecraft:coal", 2)),
                        "two", spirit("two", null, Map.of("minecraft:oak_log", 4))),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Map.of(),
                List.of(),
                GraphFacts.Terrain.empty(),
                new GraphFacts.StageReqs(Map.of(), Map.of()));

        assertEquals(
                Map.of("oak_log", 7, "coal", 2),
                More6Inputs.carried(facts),
                "Предметы духов складываются по узлам графа без namespace");
    }

    @Test
    void needed_MaterialsAndOrders_AreUnioned() {
        GraphState state = state(
                GraphFacts.empty(),
                Map.of("hut", Map.of("oak_log", 10), "barn", Map.of("cobblestone", 4)),
                Map.of("coal", 2));

        assertEquals(
                Set.of("oak_log", "cobblestone", "coal"),
                More6Inputs.needed(state),
                "Нужны и материалы построек, и недостача заказов");
    }

    @Test
    void fields_OnlyBuiltFieldsAndFarms_WithPosition() {
        GraphFacts facts = new GraphFacts(
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Map.of(
                        "field", building("field", new GraphFacts.Position(1, 64, 1)),
                        "wheat_field", building("wheat_field", new GraphFacts.Position(2, 64, 2)),
                        "barn", building("barn", new GraphFacts.Position(3, 64, 3)),
                        "field_carrot", building("field_carrot", null)),
                List.of(),
                GraphFacts.Terrain.empty(),
                new GraphFacts.StageReqs(Map.of(), Map.of()));
        GraphState state = state(
                facts,
                Map.of(),
                Map.of(),
                Map.of(
                        "field", Built.BUILT,
                        "wheat_field", Built.PLANNED,
                        "barn", Built.BUILT,
                        "field_carrot", Built.BUILT));

        assertEquals(
                List.of(new GraphFacts.Position(1, 64, 1)),
                More6Inputs.fields(state, facts),
                "Поле считается только построенным и с координатой");
    }

    @Test
    void builtStations_OnlyBuilt_AreTaken() {
        GraphFacts facts = new GraphFacts(
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(
                        "furnace", station("furnace"),
                        "crafting_table", station("crafting_table")),
                Map.of(),
                List.of(),
                GraphFacts.Terrain.empty(),
                new GraphFacts.StageReqs(Map.of(), Map.of()));
        GraphState state =
                state(facts, Map.of(), Map.of(), Map.of("furnace", Built.BUILT, "crafting_table", Built.PLANNED));

        assertEquals(
                Set.of("furnace"),
                More6Inputs.builtStations(state, facts),
                "Сломанной считается только построенная станция");
    }

    @Test
    void idleSpirits_NullAndBlank_AreIdle() {
        GraphFacts facts = new GraphFacts(
                Map.of(
                        "busy", spirit("busy", "mine", Map.of()),
                        "blank", spirit("blank", " ", Map.of()),
                        "free", spirit("free", null, Map.of())),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Map.of(),
                List.of(),
                GraphFacts.Terrain.empty(),
                new GraphFacts.StageReqs(Map.of(), Map.of()));

        assertEquals(
                List.of("blank", "free"),
                More6Inputs.idleSpirits(facts),
                "Дух без занятия или с пустым занятием простаивает");
    }

    private static GraphState state(
            GraphFacts facts, Map<String, Map<String, Integer>> materials, Map<String, Integer> orders) {
        return state(facts, materials, orders, Map.of());
    }

    private static GraphState state(
            GraphFacts facts,
            Map<String, Map<String, Integer>> materials,
            Map<String, Integer> orders,
            Map<String, Built> buildings) {
        return new GraphState(
                Map.of(),
                1,
                Map.of(),
                buildings,
                materials,
                100,
                Set.of("night"),
                Set.of(),
                Map.of(),
                Map.of(),
                orders,
                facts);
    }

    private static GraphFacts.Spirit spirit(String id, String activity, Map<String, Integer> inventory) {
        return new GraphFacts.Spirit(
                id,
                new GraphFacts.Position(0, 64, 0),
                20,
                20,
                300,
                false,
                false,
                false,
                activity,
                inventory,
                List.of(),
                List.of());
    }

    private static GraphFacts.Building building(String id, GraphFacts.Position position) {
        return new GraphFacts.Building(id, 1, 100, 0, position, false);
    }

    private static GraphFacts.Station station(String id) {
        return new GraphFacts.Station(id, id, new GraphFacts.Position(0, 64, 0), false, null, null, null);
    }
}
