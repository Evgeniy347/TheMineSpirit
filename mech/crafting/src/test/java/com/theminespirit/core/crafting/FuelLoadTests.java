package com.theminespirit.core.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.crafting.FuelLoad.Portion;
import com.theminespirit.core.planner.api.ResourceKnowledge;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FuelLoadTests {
    private static final ResourceKnowledge KNOWLEDGE = new FuelKnowledge();
    private static final int COOK = 200;

    @Test
    void choose_ThreeItemsTwoCoal_BurnsOneCoalOnly() {
        assertEquals(
                List.of(new Portion("coal", 1)),
                FuelLoad.choose(3, COOK, Map.of("coal", 2), KNOWLEDGE),
                "На три предмета хватает одного угля (600 из 1600 тиков): лишний уголь обязан остаться у духа, "
                        + "иначе печь жжёт запас сверх работы и топливо тратится зря. При смене FuelLoad.choose "
                        + "проверь строку досок ниже в тесте");
    }

    @Test
    void choose_PlanksOnly_BurnsExactlyNeededPlanks() {
        assertEquals(
                List.of(new Portion("oak_planks", 6)),
                FuelLoad.choose(8, COOK, Map.of("oak_planks", 9), KNOWLEDGE),
                "8 предметов досками (300 тиков) требуют ceil(1600/300) = 6 досок, остальные 3 остаются: без этого "
                        + "досок сгорает весь запас и дух остаётся без стройматериала. При смене расчёта в "
                        + "FuelLoad.choose проверь строку угля выше в тесте");
    }

    @Test
    void choose_CoalAndPlanksBothCover_BurnsFirstInPreferenceOrder() {
        assertEquals(
                List.of(new Portion("coal", 1)),
                FuelLoad.choose(3, COOK, Map.of("coal", 1, "oak_planks", 9), KNOWLEDGE),
                "При покрытии двумя видами топлива горит тот, что раньше в порядке предпочтения (уголь): так "
                        + "выбирает резолвер, иначе дух жжёт не то топливо, а добытое под план лежит зря. При смене "
                        + "порядка предпочтения проверь строку неполного покрытия ниже в тесте");
    }

    @Test
    void choose_BelowCoverage_SkipsInsufficientFuelForSufficientOne() {
        assertEquals(
                List.of(new Portion("oak_log", 6)),
                FuelLoad.choose(8, COOK, Map.of("oak_planks", 3, "oak_log", 6), KNOWLEDGE),
                "Если доски покрывают не всё, а брёвна покрывают, горят брёвна целиком: резолвер выбирает полное "
                        + "покрытие раньше частичного, иначе доски кончаются на середине и плавка встаёт. При смене "
                        + "выбора топлива в FuelLoad.choose проверь строку предпочтения выше в тесте");
    }

    @Test
    void choose_NoSingleFuelCovers_CombinesFuelsInPreferenceOrder() {
        assertEquals(
                List.of(new Portion("coal", 1), new Portion("oak_planks", 5)),
                FuelLoad.choose(16, COOK, Map.of("coal", 1, "oak_planks", 5), KNOWLEDGE),
                "Когда ни одно топливо не покрывает работу в одиночку, горит всё имеющееся по порядку "
                        + "предпочтения, и недостача видна как «топливо кончилось», иначе дух отказывается от "
                        + "плавки ещё до расходов. При смене комбинирования в FuelLoad.choose проверь строку "
                        + "короткого топлива ниже в тесте");
    }

    @Test
    void choose_ShortFuel_TakesWhatInventoryHas() {
        assertEquals(
                List.of(new Portion("stick", 1)),
                FuelLoad.choose(3, COOK, Map.of("stick", 1), KNOWLEDGE),
                "Одна палка на 3 предмета: топлива мало, но дух кладёт то, что есть, иначе он отказывается от "
                        + "работы, хотя печь могла переплавить часть, а печь сама покажет, сколько успела. При смене "
                        + "FuelLoad.choose проверь строку без топлива ниже в тесте");
    }

    @Test
    void choose_NoFuel_ReturnsEmpty() {
        assertEquals(
                List.of(),
                FuelLoad.choose(3, COOK, Map.of("iron_ore", 5), KNOWLEDGE),
                "Без топлива в инвентаре порций нет: скрипт отказывает «нет топлива» до любых расходов, иначе руда "
                        + "уходит в печь впустую. При смене проверки топлива в FuelLoad.choose проверь строку одной "
                        + "палки выше в тесте");
    }

    @Test
    void choose_ShortCookTime_NeedsLessFuel() {
        assertEquals(
                List.of(new Portion("oak_planks", 1)),
                FuelLoad.choose(3, 100, Map.of("oak_planks", 4), KNOWLEDGE),
                "Рецепт готовится 100 тиков: 3 предмета — 300 тиков, это одна доска; расход считается по времени "
                        + "рецепта, а не по 200 обычной печи, иначе досок уходит вдвое больше нужного. При смене "
                        + "источника времени в FuelLoad.choose проверь строку 200 выше в тесте");
    }
}
