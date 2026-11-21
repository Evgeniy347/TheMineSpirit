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
                "9 еды при расходе 5/день — меньше двух дней Иначе тест food_LowStock_AddsForecastEvent перестанет ловить эту поломку. При смене food_LowStock_AddsForecastEvent проверь это утверждение.");
        assertEquals(
                1.8,
                signals.food().daysLeft(),
                1e-9,
                "Дней хватит = запас / (расход − производство) Иначе тест food_LowStock_AddsForecastEvent перестанет ловить эту поломку. При смене food_LowStock_AddsForecastEvent проверь это утверждение.");
    }

    @Test
    void food_ProductionCoversNeed_IsNotLow() {
        More2Rules.Food covered = new More2Rules.Food(0, 5, 5);
        More2Rules.Food plenty = new More2Rules.Food(40, 5, 0);

        assertFalse(
                covered.low(),
                "Производство закрывает расход — поселение не голодает Иначе тест food_ProductionCoversNeed_IsNotLow перестанет ловить эту поломку. При смене food_ProductionCoversNeed_IsNotLow проверь это утверждение.");
        assertTrue(
                Double.isInfinite(covered.daysLeft()),
                "При покрытом расходе запас не кончается Иначе тест food_ProductionCoversNeed_IsNotLow перестанет ловить эту поломку. При смене food_ProductionCoversNeed_IsNotLow проверь это утверждение.");
        assertFalse(
                plenty.low(),
                "Восемь дней запаса — не повод сеять вне плана Иначе тест food_ProductionCoversNeed_IsNotLow перестанет ловить эту поломку. При смене food_ProductionCoversNeed_IsNotLow проверь это утверждение.");
    }

    @Test
    void food_NoConsumers_IsNotLow() {
        assertFalse(
                new More2Rules.Food(0, 0, 0).low(),
                "Некому есть — прогноз не требует еды Иначе тест food_NoConsumers_IsNotLow перестанет ловить эту поломку. При смене food_NoConsumers_IsNotLow проверь это утверждение.");
    }

    @Test
    void inventory_FullAndRoom_AreSeparateEvents() {
        More2Rules.Signals full = signals(new More2Rules.Inventory(2, 5, true, true));
        More2Rules.Signals room = signals(new More2Rules.Inventory(20, 5, false, true));
        More2Rules.Signals idle = signals(new More2Rules.Inventory(20, 5, false, false));

        assertTrue(
                More2Rules.events(full).contains(More2Rules.INVENTORY_FULL),
                "Мало места и есть груз — сдать Иначе тест inventory_FullAndRoom_AreSeparateEvents перестанет ловить эту поломку. При смене inventory_FullAndRoom_AreSeparateEvents проверь это утверждение.");
        assertTrue(
                More2Rules.events(room).contains(More2Rules.INVENTORY_SPACE),
                "Много места и есть работа — нести ещё Иначе тест inventory_FullAndRoom_AreSeparateEvents перестанет ловить эту поломку. При смене inventory_FullAndRoom_AreSeparateEvents проверь это утверждение.");
        assertFalse(
                More2Rules.events(idle).contains(More2Rules.INVENTORY_SPACE),
                "Без работы нести нечего Иначе тест inventory_FullAndRoom_AreSeparateEvents перестанет ловить эту поломку. При смене inventory_FullAndRoom_AreSeparateEvents проверь это утверждение.");
    }

    @Test
    void inventory_NoChestRoom_DoesNotAskToCarry() {
        More2Rules.Signals signals = signals(new More2Rules.Inventory(20, 0, false, true));

        assertFalse(
                More2Rules.events(signals).contains(More2Rules.INVENTORY_SPACE),
                "Склад полон — нести некуда Иначе тест inventory_NoChestRoom_DoesNotAskToCarry перестанет ловить эту поломку. При смене inventory_NoChestRoom_DoesNotAskToCarry проверь это утверждение.");
    }

    @Test
    void hazards_FireLavaAndFlow_AreReportedSeparately() {
        Set<String> fire = More2Rules.events(signals(new More2Rules.Hazards(1, 0, 0, 0)));
        Set<String> lava = More2Rules.events(signals(new More2Rules.Hazards(0, 1, 0, 0)));
        Set<String> flow = More2Rules.events(signals(new More2Rules.Hazards(0, 0, 2, 1)));

        assertTrue(
                fire.contains(More2Rules.FIRE_NEAR_BUILDING),
                "Огонь у постройки — тушить Иначе тест hazards_FireLavaAndFlow_AreReportedSeparately перестанет ловить эту поломку. При смене hazards_FireLavaAndFlow_AreReportedSeparately проверь это утверждение.");
        assertTrue(
                lava.contains(More2Rules.LAVA_NEAR_BUILDING),
                "Лава у постройки — убрать Иначе тест hazards_FireLavaAndFlow_AreReportedSeparately перестанет ловить эту поломку. При смене hazards_FireLavaAndFlow_AreReportedSeparately проверь это утверждение.");
        assertTrue(
                flow.contains(More2Rules.HAZARD_FLOW),
                "Текущая вода/лава — перекрыть Иначе тест hazards_FireLavaAndFlow_AreReportedSeparately перестанет ловить эту поломку. При смене hazards_FireLavaAndFlow_AreReportedSeparately проверь это утверждение.");
    }

    @Test
    void hazards_Clean_IsEmpty() {
        assertEquals(
                Set.of(),
                More2Rules.events(More2Rules.empty()),
                "Чистый вход не ставит ни одной задачи Иначе тест hazards_Clean_IsEmpty перестанет ловить эту поломку. При смене hazards_Clean_IsEmpty проверь это утверждение.");
    }

    @Test
    void animals_OutsidePenAndWild_AreReported() {
        Set<String> outside = More2Rules.events(signals(new More2Rules.Animals(5, 3, 0, true)));
        Set<String> wild = More2Rules.events(signals(new More2Rules.Animals(4, 0, 4, false)));

        assertTrue(
                outside.contains(More2Rules.ANIMAL_OUTSIDE),
                "Животные вне загона — загнать Иначе тест animals_OutsidePenAndWild_AreReported перестанет ловить эту поломку. При смене animals_OutsidePenAndWild_AreReported проверь это утверждение.");
        assertFalse(
                outside.contains(More2Rules.WILD_HERD),
                "Своё стадо — не дикое Иначе тест animals_OutsidePenAndWild_AreReported перестанет ловить эту поломку. При смене animals_OutsidePenAndWild_AreReported проверь это утверждение.");
        assertTrue(
                wild.contains(More2Rules.WILD_HERD),
                "Дикие рядом — привести на разведение Иначе тест animals_OutsidePenAndWild_AreReported перестанет ловить эту поломку. При смене animals_OutsidePenAndWild_AreReported проверь это утверждение.");
    }

    @Test
    void animals_MissingPen_DoesNotAskToHerd() {
        Set<String> events = More2Rules.events(signals(new More2Rules.Animals(4, 0, 0, false)));

        assertFalse(
                events.contains(More2Rules.ANIMAL_OUTSIDE),
                "Без загона загонять некуда Иначе тест animals_MissingPen_DoesNotAskToHerd перестанет ловить эту поломку. При смене animals_MissingPen_DoesNotAskToHerd проверь это утверждение.");
    }

    @Test
    void villagers_Nearby_GuardOnly() {
        Set<String> events = More2Rules.events(signals(new More2Rules.Villagers(2)));

        assertTrue(
                events.contains(More2Rules.VILLAGER_NEAR),
                "Жители рядом — защищать Иначе тест villagers_Nearby_GuardOnly перестанет ловить эту поломку. При смене villagers_Nearby_GuardOnly проверь это утверждение.");
        assertFalse(
                events.contains("more2_villager_trade"),
                "Торговли у духов нет: событие не создаётся Иначе тест villagers_Nearby_GuardOnly перестанет ловить эту поломку. При смене villagers_Nearby_GuardOnly проверь это утверждение.");
    }

    @Test
    void stage_IdleWithNextStage_Preparation() {
        Set<String> prep = More2Rules.events(signals(new More2Rules.Stage(1, 3, 1)));
        Set<String> busy = More2Rules.events(signals(new More2Rules.Stage(1, 3, 0)));
        Set<String> last = More2Rules.events(signals(new More2Rules.Stage(3, 3, 2)));

        assertTrue(
                prep.contains(More2Rules.STAGE_PREP),
                "Свободный дух и есть следующий этап — заготовка заранее Иначе тест stage_IdleWithNextStage_Preparation перестанет ловить эту поломку. При смене stage_IdleWithNextStage_Preparation проверь это утверждение.");
        assertFalse(
                busy.contains(More2Rules.STAGE_PREP),
                "Занятым духам заготовку не навязывают Иначе тест stage_IdleWithNextStage_Preparation перестанет ловить эту поломку. При смене stage_IdleWithNextStage_Preparation проверь это утверждение.");
        assertFalse(
                last.contains(More2Rules.STAGE_PREP),
                "Последнему этапу готовиться не к чему Иначе тест stage_IdleWithNextStage_Preparation перестанет ловить эту поломку. При смене stage_IdleWithNextStage_Preparation проверь это утверждение.");
    }

    @Test
    void sleep_FewerBedsThanSpirits_AsksForBed() {
        assertTrue(
                More2Rules.events(signals(new More2Rules.Sleep(1, 3))).contains(More2Rules.BED_NEEDED),
                "Кроватей меньше духов — строить Иначе тест sleep_FewerBedsThanSpirits_AsksForBed перестанет ловить эту поломку. При смене sleep_FewerBedsThanSpirits_AsksForBed проверь это утверждение.");
        assertFalse(
                More2Rules.events(signals(new More2Rules.Sleep(3, 3))).contains(More2Rules.BED_NEEDED),
                "Мест хватает Иначе тест sleep_FewerBedsThanSpirits_AsksForBed перестанет ловить эту поломку. При смене sleep_FewerBedsThanSpirits_AsksForBed проверь это утверждение.");
    }

    @Test
    void paths_ManySpiritsOutsideWithoutTrail_AsksForTrail() {
        assertTrue(
                More2Rules.events(signals(new More2Rules.Paths(2, 0))).contains(More2Rules.PATH_NEEDED),
                "Двое ходят снаружи без тропы — проложить Иначе тест paths_ManySpiritsOutsideWithoutTrail_AsksForTrail перестанет ловить эту поломку. При смене paths_ManySpiritsOutsideWithoutTrail_AsksForTrail проверь это утверждение.");
        assertFalse(
                More2Rules.events(signals(new More2Rules.Paths(2, 1))).contains(More2Rules.PATH_NEEDED),
                "Тропа уже есть Иначе тест paths_ManySpiritsOutsideWithoutTrail_AsksForTrail перестанет ловить эту поломку. При смене paths_ManySpiritsOutsideWithoutTrail_AsksForTrail проверь это утверждение.");
        assertFalse(
                More2Rules.events(signals(new More2Rules.Paths(1, 0))).contains(More2Rules.PATH_NEEDED),
                "Один выход — не частый путь Иначе тест paths_ManySpiritsOutsideWithoutTrail_AsksForTrail перестанет ловить эту поломку. При смене paths_ManySpiritsOutsideWithoutTrail_AsksForTrail проверь это утверждение.");
    }

    @Test
    void signals_NegativeCounters_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new More2Rules.Food(-1, 0, 0),
                "Отрицательный запас еды невозможен Иначе тест signals_NegativeCounters_Rejected перестанет ловить эту поломку. При смене signals_NegativeCounters_Rejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> new More2Rules.Animals(2, 3, 0, true),
                "В загоне больше животных, чем есть Иначе тест signals_NegativeCounters_Rejected перестанет ловить эту поломку. При смене signals_NegativeCounters_Rejected проверь это утверждение.");
    }

    private static More2Rules.Signals signals(More2Rules.Food food) {
        return new More2Rules.Signals(
                food,
                new More2Rules.Inventory(20, 5, false, false),
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
                new More2Rules.Hazards(0, 0, 0, 0),
                new More2Rules.Animals(0, 0, 0, false),
                new More2Rules.Villagers(0),
                new More2Rules.Stage(1, 0, 0),
                new More2Rules.Sleep(0, 0),
                paths);
    }
}
