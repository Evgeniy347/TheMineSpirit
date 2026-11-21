package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

class WoolRulesTests {

    @Test
    void defaults_FromClasspath_MatchChecklist() {
        assertEquals(
                4,
                WoolRules.minPenSheep(),
                "Предел в тесте, факт из production: шерстяные задания открываются при 4 овцах в загоне"
                        + " Иначе тест defaults_FromClasspath_MatchChecklist перестанет ловить эту поломку. При смене defaults_FromClasspath_MatchChecklist проверь это утверждение.");
        assertEquals(
                48,
                WoolRules.wildSheepRadius(),
                "Предел в тесте, факт из production: диких овец ищут в радиусе 48 от дома"
                        + " Иначе тест defaults_FromClasspath_MatchChecklist перестанет ловить эту поломку. При смене defaults_FromClasspath_MatchChecklist проверь это утверждение.");
        assertEquals(
                4,
                WoolRules.wildSheepPerDay(),
                "Предел в тесте, факт из production: не больше 4 диких овец за игровые сутки"
                        + " Иначе тест defaults_FromClasspath_MatchChecklist перестанет ловить эту поломку. При смене defaults_FromClasspath_MatchChecklist проверь это утверждение.");
        assertEquals(
                8,
                WoolRules.stringThreshold(),
                "Предел в тесте, факт из production: нити пускают в шерсть от 8 штук"
                        + " Иначе тест defaults_FromClasspath_MatchChecklist перестанет ловить эту поломку. При смене defaults_FromClasspath_MatchChecklist проверь это утверждение.");
        assertEquals(
                4,
                WoolRules.stringReserve(),
                "Предел в тесте, факт из production: склад держит 4 нити неприкосновенными"
                        + " Иначе тест defaults_FromClasspath_MatchChecklist перестанет ловить эту поломку. При смене defaults_FromClasspath_MatchChecklist проверь это утверждение.");
        assertEquals(
                2,
                WoolRules.shearsIron(),
                "Предел в тесте, факт из production: ножницы стоят 2 железных слитка"
                        + " Иначе тест defaults_FromClasspath_MatchChecklist перестанет ловить эту поломку. При смене defaults_FromClasspath_MatchChecklist проверь это утверждение.");
        assertEquals(
                3,
                WoolRules.bedWool(),
                "Предел в тесте, факт из production: кровать — 3 шерсти одного цвета"
                        + " Иначе тест defaults_FromClasspath_MatchChecklist перестанет ловить эту поломку. При смене defaults_FromClasspath_MatchChecklist проверь это утверждение.");
        assertEquals(
                3,
                WoolRules.bedPlanks(),
                "Предел в тесте, факт из production: кровать — 3 доски"
                        + " Иначе тест defaults_FromClasspath_MatchChecklist перестанет ловить эту поломку. При смене defaults_FromClasspath_MatchChecklist проверь это утверждение.");
        assertEquals(
                8,
                WoolRules.shearRadius(),
                "Предел в тесте, факт из production: стригут в радиусе загона 8"
                        + " Иначе тест defaults_FromClasspath_MatchChecklist перестанет ловить эту поломку. При смене defaults_FromClasspath_MatchChecklist проверь это утверждение.");
    }

    @Test
    void woolFromStrings_Threshold_KeepsReserve() {
        assertEquals(
                1,
                WoolRules.woolFromStrings(8),
                "Восемь нитей — ровно одна шерсть: четыре уходят в крафт, четыре остаются запасом"
                        + " Иначе тест woolFromStrings_Threshold_KeepsReserve перестанет ловить эту поломку. При смене woolFromStrings_Threshold_KeepsReserve проверь это утверждение.");
        assertEquals(
                0,
                WoolRules.woolFromStrings(7),
                "Семь нитей ниже порога: шерсть из них не делают, иначе запас ушёл бы под ноль"
                        + " При смене woolFromStrings_Threshold_KeepsReserve проверь это утверждение.");
        assertEquals(
                2,
                WoolRules.woolFromStrings(12),
                "Двенадцать нитей — две шерсти: после крафта остаётся неприкосновенный запас 4"
                        + " Иначе тест woolFromStrings_Threshold_KeepsReserve перестанет ловить эту поломку. При смене woolFromStrings_Threshold_KeepsReserve проверь это утверждение.");
    }

    @Property
    void woolFromStrings_AnyCount_NeverEatsReserve(@ForAll @IntRange(min = 0, max = 400) int strings) {
        int wool = WoolRules.woolFromStrings(strings);

        assertTrue(
                wool >= 0,
                "Шерсти не бывает меньше нуля: " + wool
                        + " Иначе тест woolFromStrings_AnyCount_NeverEatsReserve перестанет ловить эту поломку. При смене woolFromStrings_AnyCount_NeverEatsReserve проверь это утверждение.");
        long left = strings - (long) wool * WoolRules.STRINGS_PER_WOOL;
        assertTrue(
                left >= Math.min(strings, WoolRules.stringReserve()),
                "Крафт не трогает запас " + WoolRules.stringReserve() + ": нитей " + strings + ", шерсти " + wool
                        + ", осталось " + left
                        + " Иначе тест woolFromStrings_AnyCount_NeverEatsReserve перестанет ловить эту поломку. При смене woolFromStrings_AnyCount_NeverEatsReserve проверь это утверждение.");
        if (strings >= WoolRules.stringThreshold()) {
            assertTrue(
                    wool >= 1,
                    "От порога шерсть обязана делаться: нитей " + strings
                            + " Иначе тест woolFromStrings_AnyCount_NeverEatsReserve перестанет ловить эту поломку. При смене woolFromStrings_AnyCount_NeverEatsReserve проверь это утверждение.");
        }
    }

    @Property
    void penReady_AnyCount_MatchesMinimum(@ForAll @IntRange(min = 0, max = 40) int sheep) {
        assertEquals(
                sheep >= WoolRules.minPenSheep(),
                WoolRules.penReady(sheep),
                "Гейт отары открывается ровно на " + WoolRules.minPenSheep() + " овцах, фактически " + sheep
                        + " Иначе тест penReady_AnyCount_MatchesMinimum перестанет ловить эту поломку. При смене penReady_AnyCount_MatchesMinimum проверь это утверждение.");
    }

    @Property
    void shearsCraftable_AnyStock_NeedsIronAndNoShears(
            @ForAll @IntRange(min = 0, max = 20) int iron, @ForAll boolean hasShears) {
        assertEquals(
                !hasShears && iron >= WoolRules.shearsIron(),
                WoolRules.shearsCraftable(iron, hasShears),
                "Ножницы куют только когда их нет и есть " + WoolRules.shearsIron() + " железа: железо " + iron
                        + ", ножницы " + hasShears
                        + " Иначе тест shearsCraftable_AnyStock_NeedsIronAndNoShears перестанет ловить эту поломку. При смене shearsCraftable_AnyStock_NeedsIronAndNoShears проверь это утверждение.");
    }

    @Property
    void bedsNeeded_AnyCounts_NeverAsksAboveSpirits(
            @ForAll @IntRange(min = 0, max = 40) int beds, @ForAll @IntRange(min = 0, max = 40) int spirits) {
        int needed = WoolRules.bedsNeeded(beds, spirits);

        assertTrue(
                needed >= 0,
                "Спрос на кровати не бывает отрицательным: " + needed
                        + " Иначе тест bedsNeeded_AnyCounts_NeverAsksAboveSpirits перестанет ловить эту поломку. При смене bedsNeeded_AnyCounts_NeverAsksAboveSpirits проверь это утверждение.");
        assertTrue(
                beds + needed <= Math.max(beds, spirits) || needed == 0,
                "Кроватей не больше духов: кроватей " + beds + ", духов " + spirits + ", спрос " + needed
                        + " Иначе тест bedsNeeded_AnyCounts_NeverAsksAboveSpirits перестанет ловить эту поломку. При смене bedsNeeded_AnyCounts_NeverAsksAboveSpirits проверь это утверждение.");
        assertEquals(
                Math.max(0, spirits - beds),
                needed,
                "Спрос — духи без кровати: кроватей " + beds + ", духов " + spirits
                        + " Иначе тест bedsNeeded_AnyCounts_NeverAsksAboveSpirits перестанет ловить эту поломку. При смене bedsNeeded_AnyCounts_NeverAsksAboveSpirits проверь это утверждение.");
    }

    @Property
    void wildTarget_InHomePen_IsNotTarget(
            @ForAll @IntRange(min = 0, max = 100) int distance, @ForAll @IntRange(min = 1, max = 64) int radius) {
        assertFalse(
                WoolRules.wildTarget(true, distance, radius),
                "Овца домашнего круга не цель охоты: расстояние " + distance + ", радиус " + radius
                        + " Иначе тест wildTarget_InHomePen_IsNotTarget перестанет ловить эту поломку. При смене wildTarget_InHomePen_IsNotTarget проверь это утверждение.");
    }

    @Test
    void bedColor_WhiteAndRedReady_ChoosesWhiteFirst() {
        Map<String, Integer> stock = new LinkedHashMap<>();
        stock.put("white_wool", 3);
        stock.put("red_wool", 3);

        assertEquals(
                Optional.of("white"),
                WoolRules.bedColor(new StockView(stock)),
                "Белая кровать идёт первой: пока белой шерсти хватает, цветные не тратят"
                        + " Иначе тест bedColor_WhiteAndRedReady_ChoosesWhiteFirst перестанет ловить эту поломку. При смене bedColor_WhiteAndRedReady_ChoosesWhiteFirst проверь это утверждение.");
    }

    @Test
    void bedColor_OnlyRedReady_ChoosesRed() {
        Map<String, Integer> stock = new LinkedHashMap<>();
        stock.put("white_wool", 1);
        stock.put("red_wool", 3);

        assertEquals(
                Optional.of("red"),
                WoolRules.bedColor(new StockView(stock)),
                "Цветная кровать делается, когда белой не хватает, а одного цвета набралось три"
                        + " Иначе тест bedColor_OnlyRedReady_ChoosesRed перестанет ловить эту поломку. При смене bedColor_OnlyRedReady_ChoosesRed проверь это утверждение.");
    }

    @Test
    void bedColor_NoColorReady_IsEmpty() {
        assertEquals(
                Optional.empty(),
                WoolRules.bedColor(new StockView(Map.of("white_wool", 2))),
                "Двух шерсти на кровать не хватает: задание не ставится, склад копит цвет"
                        + " Иначе тест bedColor_NoColorReady_IsEmpty перестанет ловить эту поломку. При смене bedColor_NoColorReady_IsEmpty проверь это утверждение.");
    }

    @Test
    void planksType_AnyPlanksWithThree_Chosen() {
        Map<String, Integer> stock = new LinkedHashMap<>();
        stock.put("oak_planks", 2);
        stock.put("spruce_planks", 3);

        assertEquals(
                Optional.of("spruce_planks"),
                WoolRules.planksType(new StockView(stock)),
                "Доски берутся того вида, которого хватает на кровать: две дубовых не годятся"
                        + " Иначе тест planksType_AnyPlanksWithThree_Chosen перестанет ловить эту поломку. При смене planksType_AnyPlanksWithThree_Chosen проверь это утверждение.");
    }

    @Test
    void bedCount_AllColors_Sums() {
        Map<String, Integer> stock = new LinkedHashMap<>();
        stock.put("white_bed", 2);
        stock.put("red_bed", 1);

        assertEquals(
                3,
                WoolRules.bedCount(new StockView(stock)),
                "Кровати всех цветов считаются вместе: иначе цветная кровать обходила бы спрос"
                        + " При смене bedCount_AllColors_Sums проверь это утверждение.");
    }

    @Test
    void negativeInputs_AnyEntry_AreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> WoolRules.penReady(-1),
                "Отрицательное поголовье обязано падать: минус овец прятал бы пустой загон"
                        + " Иначе тест negativeInputs_AnyEntry_AreRejected перестанет ловить эту поломку. При смене negativeInputs_AnyEntry_AreRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> WoolRules.woolFromStrings(-1),
                "Отрицательные нити обязаны падать: минус прятал бы перерасход"
                        + " Иначе тест negativeInputs_AnyEntry_AreRejected перестанет ловить эту поломку. При смене negativeInputs_AnyEntry_AreRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> WoolRules.bedsNeeded(-1, 2),
                "Отрицательные кровати обязаны падать: минус ломал бы спрос"
                        + " Иначе тест negativeInputs_AnyEntry_AreRejected перестанет ловить эту поломку. При смене negativeInputs_AnyEntry_AreRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> WoolRules.wildTarget(false, -1, 48),
                "Отрицательный квадрат расстояния обязан падать: такого расстояния не бывает"
                        + " Иначе тест negativeInputs_AnyEntry_AreRejected перестанет ловить эту поломку. При смене negativeInputs_AnyEntry_AreRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> WoolRules.woolId("rainbow"),
                "Неизвестный цвет обязан падать: иначе задание искало бы несуществующую шерсть"
                        + " При смене negativeInputs_AnyEntry_AreRejected проверь это утверждение.");
    }
}
