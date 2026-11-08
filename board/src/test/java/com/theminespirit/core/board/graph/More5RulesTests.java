package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class More5RulesTests {
    private static final SupplyGraph GRAPH = SupplyGraph.loadDefault();

    @Test
    void events_AllSignals_AddExpected() {
        Set<String> events = More5Rules.events(new More5Rules.Signals(2, 3, 4, 5, 6, 12, true, 7, 8, 9, 10, 11, 12));

        assertEquals(
                Set.of(
                        "more5_crops_dark",
                        "more5_saplings_dark",
                        "more5_crops_dry",
                        "more5_growth_unfed",
                        "more5_fuel_low",
                        "more5_build_shortage",
                        "more5_mob_broken",
                        "more5_route_blocked",
                        "more5_chest_far",
                        "more5_mine_dark",
                        "more5_wood_drops"),
                events,
                "Каждый вход области даёт своё событие");
    }

    @Test
    void events_EmptySignals_AddNothing() {
        Set<String> events = More5Rules.events(new More5Rules.Signals(0, 0, 0, 0, 0, 16, false, 0, 0, 0, 0, 0, 0));

        assertEquals(Set.of(), events, "Пустой вход не ставит работ");
    }

    @Test
    void events_FuelEnough_NoFuelEvent() {
        Set<String> events = More5Rules.events(new More5Rules.Signals(0, 0, 0, 0, 16, 16, true, 0, 0, 0, 0, 0, 0));

        assertFalse(events.contains("more5_fuel_low"), "Полного запаса топлива хватает");
        events = More5Rules.events(new More5Rules.Signals(0, 0, 0, 0, 15, 16, true, 0, 0, 0, 0, 0, 0));
        assertTrue(events.contains("more5_fuel_low"), "Недостача топлива при печи — событие");
        events = More5Rules.events(new More5Rules.Signals(0, 0, 0, 0, 0, 16, false, 0, 0, 0, 0, 0, 0));
        assertFalse(events.contains("more5_fuel_low"), "Без печи топливо не тратят");
    }

    @Test
    void signals_Negative_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new More5Rules.Signals(-1, 0, 0, 0, 0, 16, false, 0, 0, 0, 0, 0, 0),
                "Отрицательное число тёмных грядок не имеет смысла");
        assertThrows(
                IllegalArgumentException.class,
                () -> new More5Rules.Signals(0, 0, 0, 0, 0, -1, false, 0, 0, 0, 0, 0, 0),
                "Отрицательная цель по топливу не имеет смысла");
    }

    @Test
    void fuelTarget_GrowsWithFurnaces_ButHasFloor() {
        assertEquals(16, More5Rules.fuelTarget(0), "Без печей запас всё равно нужен на печь");
        assertEquals(16, More5Rules.fuelTarget(1), "Одна печь — базовый запас");
        assertEquals(24, More5Rules.fuelTarget(3), "Каждая печь добавляет восемь единиц топлива");
        assertThrows(IllegalArgumentException.class, () -> More5Rules.fuelTarget(-1), "Печей не бывает минус");
    }

    @Test
    void fuel_GroupMembers_Counted() {
        int fuel = More5Rules.fuel(GRAPH, Map.of("coal", 3, "charcoal", 2, "oak_planks", 4, "oak_log", 1, "stick", 9));

        assertEquals(10, fuel, "Топливо считается по своей группе, палки не в счёт");
        assertEquals(0, More5Rules.fuel(GRAPH, Map.of()), "Пустой склад — пустой запас");
    }

    @Test
    void buildShortage_OnlyUnbuiltNearBuildings_Counted() {
        Map<String, Map<String, Integer>> materials = Map.of(
                "hut", Map.of("oak_planks", 16, "cobblestone", 8),
                "done", Map.of("oak_planks", 64),
                "empty", Map.of());
        Map<String, Integer> stock = Map.of("oak_planks", 10, "cobblestone", 8);

        assertEquals(
                6,
                More5Rules.buildShortage(materials, stock, Set.of("done")),
                "Нехватка считается только по будущим постройкам: 16-10 и 8-8");
        assertEquals(
                0, More5Rules.buildShortage(materials, stock, Set.of("hut", "done")), "Построенное больше не копят");
        assertEquals(0, More5Rules.buildShortage(Map.of(), stock, Set.of()), "Нет плана — нет нехватки");
    }

    @Test
    void cutByFluid_WidthAtLeastTwo_Cuts() {
        assertTrue(
                More5Rules.cutByFluid(List.of(false, true, true, false), 2), "Две клетки воды подряд перерезают путь");
        assertFalse(More5Rules.cutByFluid(List.of(false, true, false, true), 2), "Одиночные клетки воды обходятся");
        assertFalse(
                More5Rules.cutByFluid(List.of(true, true, true), 2),
                "Вся линия в воде — ресурс в воде, а не путь перерезан");
        assertFalse(More5Rules.cutByFluid(List.of(), 2), "Пустая линия не перерезана");
        assertThrows(
                IllegalArgumentException.class,
                () -> More5Rules.cutByFluid(List.of(true), 0),
                "Нулевая ширина препятствия не имеет смысла");
    }

    @Test
    void farSites_NoChestNear_Counted() {
        GraphFacts.Position mine = new GraphFacts.Position(0, 64, 0);
        GraphFacts.Position build = new GraphFacts.Position(40, 64, 0);
        GraphFacts.Position near = new GraphFacts.Position(0, 64, 8);
        GraphFacts.Position beside = new GraphFacts.Position(34, 64, 0);

        assertEquals(0, More5Rules.farSites(List.of(), List.of(near), 24), "Нет работ — нет временного сундука");
        assertEquals(
                1,
                More5Rules.farSites(List.of(mine, build), List.of(near), 24),
                "Шахта у сундука, стройка далеко — один сундук нужен");
        assertEquals(
                2, More5Rules.farSites(List.of(mine, build), List.of(), 24), "Без сундуков все места работы далеко");
        assertEquals(
                0,
                More5Rules.farSites(List.of(mine, build), List.of(near, beside), 24),
                "Сундук у каждого места работы закрывает потребность");
    }

    @Test
    void woodDrops_SaplingsAndApples_Counted() {
        Set<String> saplings = Set.of("oak_sapling", "birch_sapling");

        assertEquals(
                2,
                More5Rules.woodDrops(Set.of("oak_sapling", "apple"), saplings),
                "Саженец и яблоко с рубки подбирают");
        assertEquals(
                1,
                More5Rules.woodDrops(Set.of("birch_sapling", "oak_log"), saplings),
                "Бревно не считается выпавшим саженцем");
        assertEquals(0, More5Rules.woodDrops(Set.of(), saplings), "Нет дропов — нет работы");
    }
}
