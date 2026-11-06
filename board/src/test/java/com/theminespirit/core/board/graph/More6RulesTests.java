package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class More6RulesTests {
    @Test
    void population_FewerSpiritsThanHousing_Shortage() {
        assertTrue(More6Rules.population(2, 3).shortage(), "Свободное жильё зовёт нового духа");
        assertFalse(More6Rules.population(3, 3).shortage(), "Полное жильё новых не зовёт");
        assertFalse(More6Rules.population(4, 3).shortage(), "Лишних духов жильё не отменяет");
    }

    @Test
    void risk_WeakFarAndThreatened_Counted() {
        GraphFacts.Position home = new GraphFacts.Position(0, 64, 0);
        List<GraphFacts.Threat> threats =
                List.of(new GraphFacts.Threat("zombie", "zombie", 20, new GraphFacts.Position(70, 64, 0), null));

        assertEquals(
                1,
                More6Rules.risk(List.of(spirit("weak-far", 60, 64, 0, 8, List.of())), home, threats)
                        .weakFar(),
                "Слабый дух далеко от дома — риск дальней работы");
        assertEquals(
                1,
                More6Rules.risk(List.of(spirit("weak-threat", 70, 64, 4, 20, brokenArmor())), home, threats)
                        .weakThreat(),
                "Слабый по броне дух рядом с угрозой — риск опасной работы");
        assertEquals(
                0,
                More6Rules.risk(List.of(spirit("healthy-far", 60, 64, 0, 20, List.of())), home, threats)
                        .weakFar(),
                "Здорового духа далеко от дома риск не поднимает");
        assertEquals(
                0,
                More6Rules.risk(List.of(spirit("weak-near", 4, 64, 0, 8, List.of())), home, threats)
                        .weakFar(),
                "Слабый, но рядом с домом — не дальняя работа");
    }

    @Test
    void unfinished_PartialAudit_Counted() {
        Map<String, GraphFacts.Building> buildings = Map.of(
                "hut", building("hut", 45, false),
                "barn", building("barn", 100, false),
                "well", building("well", 30, true));

        assertEquals(1, More6Rules.unfinished(buildings).partial(), "Начатая постройка важнее новой");
    }

    @Test
    void carried_NeededItems_Counted() {
        Map<String, Integer> carried = Map.of("oak_log", 5, "coal", 2, "stick", 3);

        assertEquals(
                5,
                More6Rules.carried(carried, Set.of("oak_log", "cobblestone")).materials(),
                "Считать надо только материалы построек, а не всё подряд");
    }

    @Test
    void demand_ItemInManyBuildings_Counted() {
        Map<String, Map<String, Integer>> materials = Map.of(
                "hut", Map.of("oak_log", 10),
                "barn", Map.of("oak_log", 8),
                "well", Map.of("oak_log", 4),
                "fence", Map.of("stick", 2));

        assertEquals(1, More6Rules.demand(materials).repeated(), "Один ресурс в трёх постройках — постоянный запас");
        assertEquals(
                0,
                More6Rules.demand(Map.of("hut", Map.of("oak_log", 1), "barn", Map.of("oak_log", 1)))
                        .repeated(),
                "Два запроса — ещё не повтор");
    }

    @Test
    void loose_Threshold_IsPile() {
        assertFalse(More6Rules.loose(3).present(), "Три предмета — не скопление");
        assertTrue(More6Rules.loose(4).present(), "Четыре предмета — уже скопление");
    }

    @Test
    void fluids_DryFieldAndLava_Flagged() {
        assertTrue(More6Rules.fluids(1, 0).water(), "Сухое поле требует источника воды");
        assertFalse(More6Rules.fluids(0, 1).water(), "Политое поле воды не просит");
        assertTrue(More6Rules.fluids(0, 1).lava(), "Известная лава — ресурс для топлива");
    }

    @Test
    void idle_LongestIdle_Flagged() {
        assertFalse(More6Rules.idle(199, 1).present(), "Короткий простой — ещё не повод");
        assertTrue(More6Rules.idle(200, 1).present(), "Долгий простой поднимает выполнимую работу");
        assertFalse(More6Rules.idle(200, 0).present(), "Без простаивающих духов события нет");
    }

    @Test
    void events_AllSignals_AddExpected() {
        Set<String> events = More6Rules.events(new More6Rules.Signals(
                new More6Rules.Population(1, 2),
                new More6Rules.Risk(1, 0),
                new More6Rules.Unfinished(1),
                new More6Rules.Carried(4),
                new More6Rules.Stations(1, 1),
                new More6Rules.Loose(4),
                new More6Rules.Fluids(1, 1),
                new More6Rules.Demand(1),
                new More6Rules.Idle(200, 1)));

        assertTrue(
                events.containsAll(Set.of(
                        More6Rules.SPIRITS_SHORT,
                        More6Rules.WEAK_FAR,
                        More6Rules.BUILD_UNFINISHED,
                        More6Rules.CARRY_MATERIALS,
                        More6Rules.STATION_BROKEN,
                        More6Rules.LOOSE_ITEMS_FOUND,
                        More6Rules.WATER_NEEDED,
                        More6Rules.LAVA_NEEDED,
                        More6Rules.REPEAT_DEMAND,
                        More6Rules.SPIRIT_IDLE)),
                "Каждый вход области даёт своё событие: " + events);
    }

    @Test
    void events_EmptySignals_AddNothing() {
        assertEquals(Set.of(), More6Rules.events(More6Rules.empty()), "Пустой вход не ставит работ");
    }

    @Test
    void events_LavaNeedsBrokenStation_NotJustSource() {
        More6Rules.Signals quiet = new More6Rules.Signals(
                new More6Rules.Population(0, 0),
                new More6Rules.Risk(0, 0),
                new More6Rules.Unfinished(0),
                new More6Rules.Carried(0),
                new More6Rules.Stations(0, 0),
                new More6Rules.Loose(0),
                new More6Rules.Fluids(0, 1),
                new More6Rules.Demand(0),
                new More6Rules.Idle(0, 0));

        assertEquals(Set.of(), More6Rules.events(quiet), "Лава сама по себе не повод, если станции исправны");
    }

    @Test
    void signals_Negative_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new More6Rules.Population(-1, 0),
                "Отрицательное число духов не имеет смысла");
        assertThrows(
                IllegalArgumentException.class, () -> new More6Rules.Risk(-1, 0), "Отрицательный риск не имеет смысла");
        assertThrows(
                IllegalArgumentException.class,
                () -> new More6Rules.Stations(-1, 0),
                "Отрицательное число сломанных станций не имеет смысла");
        assertThrows(
                IllegalArgumentException.class,
                () -> new More6Rules.Idle(-1, 0),
                "Отрицательный простой не имеет смысла");
    }

    private static GraphFacts.Spirit spirit(
            String id, int x, int y, int z, float health, List<GraphFacts.Armor> armor) {
        return new GraphFacts.Spirit(
                id,
                new GraphFacts.Position(x, y, z),
                health,
                20,
                300,
                false,
                false,
                false,
                null,
                Map.of(),
                armor,
                List.of());
    }

    private static List<GraphFacts.Armor> brokenArmor() {
        return List.of(new GraphFacts.Armor("CHEST", "minecraft:leather_chestplate", 4, 80));
    }

    private static GraphFacts.Building building(String id, int percent, boolean unknown) {
        return new GraphFacts.Building(
                id, 1, percent, 0, Map.of(), Map.of(), 0, new GraphFacts.Position(0, 64, 0), unknown, true);
    }
}
