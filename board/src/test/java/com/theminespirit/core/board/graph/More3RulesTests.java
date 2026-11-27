package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class More3RulesTests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();

    @Test
    void trash_KnownJunk_Counted() {
        int trash = More3Rules.trash(GRAPH, Map.of("rotten_flesh", 2, "poisonous_potato", 1, "stick", 5));

        assertEquals(
                3,
                trash,
                "Мусор считается по группе #trash, полезные предметы не в счёт"
                        + " Иначе тест trash_KnownJunk_Counted перестанет ловить эту поломку. При смене trash_KnownJunk_Counted проверь это утверждение.");
    }

    @Test
    void unlabeledChests_MixedAndOther_Counted() {
        List<GraphFacts.Chest> chests = List.of(
                chest(Map.of("oak_log", 4, "coal", 2)),
                chest(Map.of("oak_log", 4)),
                chest(Map.of("rotten_flesh", 3)),
                chest(Map.of()));

        assertEquals(
                2,
                More3Rules.unlabeledChests(chests),
                "Смешанный и «прочий» сундук требуют назначения"
                        + " Иначе тест unlabeledChests_MixedAndOther_Counted перестанет ловить эту поломку. При смене unlabeledChests_MixedAndOther_Counted проверь это утверждение.");
    }

    @Test
    void workConflicts_DifferentActivitiesNearby_Counted() {
        GraphFacts.Spirit one = spirit("one", 0, 64, 0, "mine");
        GraphFacts.Spirit two = spirit("two", 1, 64, 0, "build");
        GraphFacts.Spirit same = spirit("three", 2, 64, 0, "mine");
        GraphFacts.Spirit far = spirit("four", 20, 64, 0, "build");

        assertEquals(
                1,
                More3Rules.workConflicts(List.of(one, two)),
                "Разные работы рядом мешают друг другу"
                        + " Иначе тест workConflicts_DifferentActivitiesNearby_Counted перестанет ловить эту поломку. При смене workConflicts_DifferentActivitiesNearby_Counted проверь это утверждение.");
        assertEquals(
                0,
                More3Rules.workConflicts(List.of(one, same)),
                "Одна работа рядом — не конфликт"
                        + " Иначе тест workConflicts_DifferentActivitiesNearby_Counted перестанет ловить эту поломку. При смене workConflicts_DifferentActivitiesNearby_Counted проверь это утверждение.");
        assertEquals(
                0,
                More3Rules.workConflicts(List.of(one, far)),
                "Далеко — не конфликт"
                        + " Иначе тест workConflicts_DifferentActivitiesNearby_Counted перестанет ловить эту поломку. При смене workConflicts_DifferentActivitiesNearby_Counted проверь это утверждение.");
    }

    @Test
    void idleStations_BuiltNotBusy_Counted() {
        Map<String, GraphFacts.Station> stations = Map.of(
                "furnace", station("furnace", false),
                "mine", station("mine", true));

        assertEquals(
                1,
                More3Rules.idleStations(stations),
                "Свободная станция требует загрузки, занятая — нет"
                        + " Иначе тест idleStations_BuiltNotBusy_Counted перестанет ловить эту поломку. При смене idleStations_BuiltNotBusy_Counted проверь это утверждение.");
    }

    @Test
    void workSlow_TwiceExpected_Flagged() {
        assertTrue(
                More3Rules.workSlow(401, 100),
                "Работа дольше двух ожиданий — повод уточнить стоимость"
                        + " Иначе тест workSlow_TwiceExpected_Flagged перестанет ловить эту поломку. При смене workSlow_TwiceExpected_Flagged проверь это утверждение.");
        assertFalse(
                More3Rules.workSlow(400, 100),
                "Двукратное ожидание ещё не перебор"
                        + " Иначе тест workSlow_TwiceExpected_Flagged перестанет ловить эту поломку. При смене workSlow_TwiceExpected_Flagged проверь это утверждение.");
        assertFalse(
                More3Rules.workSlow(1000, 0),
                "Без ожидания нечего сравнивать"
                        + " Иначе тест workSlow_TwiceExpected_Flagged перестанет ловить эту поломку. При смене workSlow_TwiceExpected_Flagged проверь это утверждение.");
    }

    @Test
    void stageSlow_OverBudget_Flagged() {
        assertTrue(
                More3Rules.stageSlow(24_001, 1),
                "Этап дольше бюджета — отставание"
                        + " Иначе тест stageSlow_OverBudget_Flagged перестанет ловить эту поломку. При смене stageSlow_OverBudget_Flagged проверь это утверждение.");
        assertFalse(
                More3Rules.stageSlow(24_000, 1),
                "Ровно бюджет — ещё не отставание"
                        + " Иначе тест stageSlow_OverBudget_Flagged перестанет ловить эту поломку. При смене stageSlow_OverBudget_Flagged проверь это утверждение.");
        assertEquals(
                48_000,
                More3Rules.stageBudget(2),
                "Бюджет растёт с номером этапа"
                        + " Иначе тест stageSlow_OverBudget_Flagged перестанет ловить эту поломку. При смене stageSlow_OverBudget_Flagged проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> More3Rules.stageBudget(0),
                "Нулевого этапа нет"
                        + " Иначе тест stageSlow_OverBudget_Flagged перестанет ловить эту поломку. При смене stageSlow_OverBudget_Flagged проверь это утверждение.");
    }

    @Test
    void events_AllSignals_AddExpected() {
        Set<String> events = More3Rules.events(new More3Rules.Signals(1, 1, 1, 1, 1, 1, 1, true, 1, 1, 1, true, true));

        assertTrue(
                events.containsAll(Set.of(
                        "danger_spot",
                        "spirit_death_drop",
                        "work_yield_low",
                        "trash_found",
                        "chest_purpose_missing",
                        "work_conflict",
                        "station_idle",
                        "player_sleep",
                        "sand_collapse",
                        "gravel_collapse",
                        "farmland_trampled",
                        "world_decay",
                        "stage_slow")),
                "Все входы области дают свои события: " + events
                        + " Иначе тест events_AllSignals_AddExpected перестанет ловить эту поломку. При смене events_AllSignals_AddExpected проверь это утверждение.");
    }

    @Test
    void events_EmptySignals_AddNothing() {
        Set<String> events =
                More3Rules.events(new More3Rules.Signals(0, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, false, false));

        assertEquals(
                Set.of(),
                events,
                "Пустой вход не ставит работ"
                        + " Иначе тест events_EmptySignals_AddNothing перестанет ловить эту поломку. При смене events_EmptySignals_AddNothing проверь это утверждение.");
    }

    @Test
    void signals_Negative_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new More3Rules.Signals(-1, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, false, false),
                "Отрицательное число опасных мест не имеет смысла"
                        + " Иначе тест signals_Negative_Rejected перестанет ловить эту поломку. При смене signals_Negative_Rejected проверь это утверждение.");
    }

    private static GraphFacts.Chest chest(Map<String, Integer> contents) {
        return new GraphFacts.Chest(new GraphFacts.Position(0, 64, 0), contents, 0, 27, GraphFacts.Reach.UNKNOWN);
    }

    private static GraphFacts.Station station(String id, boolean busy) {
        return new GraphFacts.Station(id, id, new GraphFacts.Position(0, 64, 0), busy, null, null, null);
    }

    private static GraphFacts.Spirit spirit(String id, int x, int y, int z, String activity) {
        return new GraphFacts.Spirit(
                id,
                new GraphFacts.Position(x, y, z),
                20,
                Math.max(20, 1f),
                null,
                20,
                300,
                false,
                false,
                false,
                activity,
                Map.of(),
                List.of(),
                List.of(),
                null);
    }
}
