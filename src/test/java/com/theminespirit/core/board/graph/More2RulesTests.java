package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class More2RulesTests {
    @Test
    void food_LowStock_AddsForecastEvent() {
        More2Rules.Signals signals = signals(new More2Rules.Food(9, 5, 0));

        assertTrue(
                More2Rules.events(signals).contains(More2Rules.FOOD_LOW),
                "9 еды при расходе 5/день — меньше двух дней");
        assertEquals(1.8, signals.food().daysLeft(), 1e-9, "Дней хватит = запас / (расход − производство)");
    }

    @Test
    void food_ProductionCoversNeed_IsNotLow() {
        More2Rules.Food covered = new More2Rules.Food(0, 5, 5);
        More2Rules.Food plenty = new More2Rules.Food(40, 5, 0);

        assertFalse(covered.low(), "Производство закрывает расход — поселение не голодает");
        assertTrue(Double.isInfinite(covered.daysLeft()), "При покрытом расходе запас не кончается");
        assertFalse(plenty.low(), "Восемь дней запаса — не повод сеять вне плана");
    }

    @Test
    void food_NoConsumers_IsNotLow() {
        assertFalse(new More2Rules.Food(0, 0, 0).low(), "Некому есть — прогноз не требует еды");
    }

    @Test
    void inventory_FullAndRoom_AreSeparateEvents() {
        More2Rules.Signals full = signals(new More2Rules.Inventory(2, 5, true, true));
        More2Rules.Signals room = signals(new More2Rules.Inventory(20, 5, false, true));
        More2Rules.Signals idle = signals(new More2Rules.Inventory(20, 5, false, false));

        assertTrue(More2Rules.events(full).contains(More2Rules.INVENTORY_FULL), "Мало места и есть груз — сдать");
        assertTrue(
                More2Rules.events(room).contains(More2Rules.INVENTORY_SPACE), "Много места и есть работа — нести ещё");
        assertFalse(More2Rules.events(idle).contains(More2Rules.INVENTORY_SPACE), "Без работы нести нечего");
    }

    @Test
    void inventory_NoChestRoom_DoesNotAskToCarry() {
        More2Rules.Signals signals = signals(new More2Rules.Inventory(20, 0, false, true));

        assertFalse(More2Rules.events(signals).contains(More2Rules.INVENTORY_SPACE), "Склад полон — нести некуда");
    }

    @Test
    void carry_FarWork_AsksForBatchOrChest() {
        assertTrue(
                More2Rules.events(signals(new More2Rules.Carry(49))).contains(More2Rules.LONG_CARRY),
                "Дальше 48 — возить пачками");
        assertFalse(
                More2Rules.events(signals(new More2Rules.Carry(48))).contains(More2Rules.LONG_CARRY),
                "48 блоков — ещё не дальний путь");
    }

    @Test
    void hazards_FireLavaAndFlow_AreReportedSeparately() {
        Set<String> fire = More2Rules.events(signals(new More2Rules.Hazards(1, 0, 0, 0)));
        Set<String> lava = More2Rules.events(signals(new More2Rules.Hazards(0, 1, 0, 0)));
        Set<String> flow = More2Rules.events(signals(new More2Rules.Hazards(0, 0, 2, 1)));

        assertTrue(fire.contains(More2Rules.FIRE_NEAR_BUILDING), "Огонь у постройки — тушить");
        assertTrue(lava.contains(More2Rules.LAVA_NEAR_BUILDING), "Лава у постройки — убрать");
        assertTrue(flow.contains(More2Rules.HAZARD_FLOW), "Текущая вода/лава — перекрыть");
    }

    @Test
    void hazards_Clean_IsEmpty() {
        assertEquals(Set.of(), More2Rules.events(More2Rules.empty()), "Чистый вход не ставит ни одной задачи");
    }

    @Test
    void animals_OutsidePenAndWild_AreReported() {
        Set<String> outside = More2Rules.events(signals(new More2Rules.Animals(5, 3, 0, true)));
        Set<String> wild = More2Rules.events(signals(new More2Rules.Animals(4, 0, 4, false)));

        assertTrue(outside.contains(More2Rules.ANIMAL_OUTSIDE), "Животные вне загона — загнать");
        assertFalse(outside.contains(More2Rules.WILD_HERD), "Своё стадо — не дикое");
        assertTrue(wild.contains(More2Rules.WILD_HERD), "Дикие рядом — привести на разведение");
    }

    @Test
    void animals_MissingPen_DoesNotAskToHerd() {
        Set<String> events = More2Rules.events(signals(new More2Rules.Animals(4, 0, 0, false)));

        assertFalse(events.contains(More2Rules.ANIMAL_OUTSIDE), "Без загона загонять некуда");
    }

    @Test
    void villagers_Nearby_GuardAndTrade() {
        Set<String> events = More2Rules.events(signals(new More2Rules.Villagers(2)));

        assertTrue(events.contains(More2Rules.VILLAGER_NEAR), "Жители рядом — защищать");
        assertTrue(events.contains(More2Rules.VILLAGER_TRADE), "Жители рядом — торговать");
    }

    @Test
    void stage_IdleWithNextStage_Preparation() {
        Set<String> prep = More2Rules.events(signals(new More2Rules.Stage(1, 3, 1)));
        Set<String> busy = More2Rules.events(signals(new More2Rules.Stage(1, 3, 0)));
        Set<String> last = More2Rules.events(signals(new More2Rules.Stage(3, 3, 2)));

        assertTrue(prep.contains(More2Rules.STAGE_PREP), "Свободный дух и есть следующий этап — заготовка заранее");
        assertFalse(busy.contains(More2Rules.STAGE_PREP), "Занятым духам заготовку не навязывают");
        assertFalse(last.contains(More2Rules.STAGE_PREP), "Последнему этапу готовиться не к чему");
    }

    @Test
    void sleep_FewerBedsThanSpirits_AsksForBed() {
        assertTrue(
                More2Rules.events(signals(new More2Rules.Sleep(1, 3))).contains(More2Rules.BED_NEEDED),
                "Кроватей меньше духов — строить");
        assertFalse(
                More2Rules.events(signals(new More2Rules.Sleep(3, 3))).contains(More2Rules.BED_NEEDED), "Мест хватает");
    }

    @Test
    void paths_ManySpiritsOutsideWithoutTrail_AsksForTrail() {
        assertTrue(
                More2Rules.events(signals(new More2Rules.Paths(2, 0))).contains(More2Rules.PATH_NEEDED),
                "Двое ходят снаружи без тропы — проложить");
        assertFalse(
                More2Rules.events(signals(new More2Rules.Paths(2, 1))).contains(More2Rules.PATH_NEEDED),
                "Тропа уже есть");
        assertFalse(
                More2Rules.events(signals(new More2Rules.Paths(1, 0))).contains(More2Rules.PATH_NEEDED),
                "Один выход — не частый путь");
    }

    @Test
    void signals_NegativeCounters_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new More2Rules.Food(-1, 0, 0),
                "Отрицательный запас еды невозможен");
        assertThrows(IllegalArgumentException.class, () -> new More2Rules.Carry(-1), "Отрицательная дорога невозможна");
        assertThrows(
                IllegalArgumentException.class,
                () -> new More2Rules.Animals(2, 3, 0, true),
                "В загоне больше животных, чем есть");
    }

    private static More2Rules.Signals signals(More2Rules.Food food) {
        return new More2Rules.Signals(
                food,
                new More2Rules.Inventory(20, 5, false, false),
                new More2Rules.Carry(0),
                new More2Rules.Hazards(0, 0, 0, 0),
                new More2Rules.Animals(0, 0, 0, false),
                new More2Rules.Villagers(0),
                new More2Rules.Stage(1, 0, 0),
                new More2Rules.Sleep(0, 0),
                new More2Rules.Paths(0, 0));
    }

    private static More2Rules.Signals signals(More2Rules.Inventory inventory) {
        return new More2Rules.Signals(
                new More2Rules.Food(0, 0, 0),
                inventory,
                new More2Rules.Carry(0),
                new More2Rules.Hazards(0, 0, 0, 0),
                new More2Rules.Animals(0, 0, 0, false),
                new More2Rules.Villagers(0),
                new More2Rules.Stage(1, 0, 0),
                new More2Rules.Sleep(0, 0),
                new More2Rules.Paths(0, 0));
    }

    private static More2Rules.Signals signals(More2Rules.Carry carry) {
        return new More2Rules.Signals(
                new More2Rules.Food(0, 0, 0),
                new More2Rules.Inventory(20, 5, false, false),
                carry,
                new More2Rules.Hazards(0, 0, 0, 0),
                new More2Rules.Animals(0, 0, 0, false),
                new More2Rules.Villagers(0),
                new More2Rules.Stage(1, 0, 0),
                new More2Rules.Sleep(0, 0),
                new More2Rules.Paths(0, 0));
    }

    private static More2Rules.Signals signals(More2Rules.Hazards hazards) {
        return new More2Rules.Signals(
                new More2Rules.Food(0, 0, 0),
                new More2Rules.Inventory(20, 5, false, false),
                new More2Rules.Carry(0),
                hazards,
                new More2Rules.Animals(0, 0, 0, false),
                new More2Rules.Villagers(0),
                new More2Rules.Stage(1, 0, 0),
                new More2Rules.Sleep(0, 0),
                new More2Rules.Paths(0, 0));
    }

    private static More2Rules.Signals signals(More2Rules.Animals animals) {
        return new More2Rules.Signals(
                new More2Rules.Food(0, 0, 0),
                new More2Rules.Inventory(20, 5, false, false),
                new More2Rules.Carry(0),
                new More2Rules.Hazards(0, 0, 0, 0),
                animals,
                new More2Rules.Villagers(0),
                new More2Rules.Stage(1, 0, 0),
                new More2Rules.Sleep(0, 0),
                new More2Rules.Paths(0, 0));
    }

    private static More2Rules.Signals signals(More2Rules.Villagers villagers) {
        return new More2Rules.Signals(
                new More2Rules.Food(0, 0, 0),
                new More2Rules.Inventory(20, 5, false, false),
                new More2Rules.Carry(0),
                new More2Rules.Hazards(0, 0, 0, 0),
                new More2Rules.Animals(0, 0, 0, false),
                villagers,
                new More2Rules.Stage(1, 0, 0),
                new More2Rules.Sleep(0, 0),
                new More2Rules.Paths(0, 0));
    }

    private static More2Rules.Signals signals(More2Rules.Stage stage) {
        return new More2Rules.Signals(
                new More2Rules.Food(0, 0, 0),
                new More2Rules.Inventory(20, 5, false, false),
                new More2Rules.Carry(0),
                new More2Rules.Hazards(0, 0, 0, 0),
                new More2Rules.Animals(0, 0, 0, false),
                new More2Rules.Villagers(0),
                stage,
                new More2Rules.Sleep(0, 0),
                new More2Rules.Paths(0, 0));
    }

    private static More2Rules.Signals signals(More2Rules.Sleep sleep) {
        return new More2Rules.Signals(
                new More2Rules.Food(0, 0, 0),
                new More2Rules.Inventory(20, 5, false, false),
                new More2Rules.Carry(0),
                new More2Rules.Hazards(0, 0, 0, 0),
                new More2Rules.Animals(0, 0, 0, false),
                new More2Rules.Villagers(0),
                new More2Rules.Stage(1, 0, 0),
                sleep,
                new More2Rules.Paths(0, 0));
    }

    private static More2Rules.Signals signals(More2Rules.Paths paths) {
        return new More2Rules.Signals(
                new More2Rules.Food(0, 0, 0),
                new More2Rules.Inventory(20, 5, false, false),
                new More2Rules.Carry(0),
                new More2Rules.Hazards(0, 0, 0, 0),
                new More2Rules.Animals(0, 0, 0, false),
                new More2Rules.Villagers(0),
                new More2Rules.Stage(1, 0, 0),
                new More2Rules.Sleep(0, 0),
                paths);
    }
}
