package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphStateFactsTests {
    @Test
    void oldConstructors_EmptyInput_LeaveFactsEmpty() {
        GraphState state = GraphWorld.empty().state();

        assertEquals(GraphFacts.empty(), state.facts(), "Старый конструктор оставляет факты пустыми");
        assertEquals(1, state.spirits(), "Число духов по-прежнему считается по наборам");
    }

    @Test
    void withOrders_AndWithFacts_KeepOtherData() {
        GraphFacts facts = facts();
        GraphState state = GraphWorld.empty().state().withFacts(facts).withOrders(Map.of("coal", 4));

        assertSame(facts, state.facts(), "Заказ не теряет факты графа");
        assertEquals(Map.of("coal", 4), state.orders(), "Факты не теряют заказ игрока");
    }

    @Test
    void facts_MutableInput_AreCopied() {
        Map<String, Integer> inventory = new HashMap<>(Map.of("minecraft:stick", 3));
        GraphFacts.Spirit spirit = new GraphFacts.Spirit(
                "spirit-1",
                new GraphFacts.Position(1, 2, 3),
                20,
                18,
                300,
                false,
                false,
                false,
                null,
                inventory,
                List.of(),
                List.of());
        inventory.put("minecraft:coal", 9);

        assertEquals(
                Map.of("minecraft:stick", 3),
                spirit.inventory(),
                "Инвентарь духа копируется: правка исходной карты не меняет вход графа");
    }

    @Test
    void chest_FreeSlotsAboveTotal_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new GraphFacts.Chest(new GraphFacts.Position(0, 0, 0), Map.of(), 4, 2),
                "Свободных слотов не может быть больше общего числа");
    }

    @Test
    void building_AuditPercentAboveHundred_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new GraphFacts.Building("hut", 1, 101, 0, null, false),
                "Процент аудита выше 100 невозможен");
    }

    @Test
    void resource_NegativeDistance_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new GraphFacts.Resource(
                        "tree:oak", new GraphFacts.Position(0, 0, 0), -1, GraphFacts.Reach.UNKNOWN, 0),
                "Отрицательное расстояние не имеет смысла: неизвестное задают UNKNOWN");
    }

    @Test
    void threat_HealthAboveMax_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new GraphFacts.Threat(
                        "threat-1",
                        "minecraft:zombie",
                        25,
                        20,
                        new GraphFacts.Position(0, 64, 0),
                        "target-1",
                        GraphFacts.TargetKind.SPIRIT,
                        true,
                        true,
                        null,
                        3,
                        null,
                        0.2),
                "Здоровье угрозы не может быть больше максимума");
    }

    @Test
    void threat_NegativeDistance_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new GraphFacts.Threat(
                        "threat-1",
                        "minecraft:zombie",
                        20,
                        20,
                        new GraphFacts.Position(0, 64, 0),
                        null,
                        GraphFacts.TargetKind.NONE,
                        false,
                        false,
                        null,
                        -1,
                        null,
                        0),
                "Расстояние до угрозы не отрицательно: неизвестное не задают минусом");
    }

    @Test
    void threat_ShortForm_KeepsTargetKind() {
        GraphFacts.Threat threat = new GraphFacts.Threat(
                "threat-1", "minecraft:zombie", 20, new GraphFacts.Position(0, 64, 0), "target-1");

        assertEquals(20, threat.maxHealth(), "Короткая форма считает максимум по текущему здоровью");
        assertEquals(GraphFacts.TargetKind.OTHER, threat.targetKind(), "Цель без вида считается другой до уточнения");
    }

    @Test
    void stageReqs_MutableInput_AreCopied() {
        Map<String, Integer> minimum = new HashMap<>(Map.of("hut", 1));
        GraphFacts.StageReqs reqs = new GraphFacts.StageReqs(minimum, Map.of("hut", 0));
        minimum.put("hut", 2);

        assertEquals(Map.of("hut", 1), reqs.minimum(), "Минимум этапа копируется");
    }

    @Test
    void terrain_Empty_IsFlat() {
        List<GraphFacts.Stray> strays = new ArrayList<>();
        GraphFacts facts = new GraphFacts(
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Map.of(),
                strays,
                GraphFacts.Terrain.empty(),
                new GraphFacts.StageReqs(Map.of(), Map.of()));
        strays.add(new GraphFacts.Stray("s", "wall", new GraphFacts.Position(0, 0, 0)));

        assertEquals(List.of(), facts.strays(), "Список лишних построек копируется");
    }

    private static GraphFacts facts() {
        return new GraphFacts(
                Map.of(
                        "spirit-1",
                        new GraphFacts.Spirit(
                                "spirit-1",
                                new GraphFacts.Position(0, 64, 0),
                                20,
                                20,
                                300,
                                false,
                                false,
                                false,
                                null,
                                Map.of("minecraft:stick", 1),
                                List.of(),
                                List.of())),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Map.of(),
                List.of(),
                GraphFacts.Terrain.empty(),
                new GraphFacts.StageReqs(Map.of(), Map.of()));
    }
}
