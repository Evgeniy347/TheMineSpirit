package com.theminespirit.core.hunger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpiritHungerTests {
    private static final HungerRules RULES = HungerRules.defaults();

    private static HungerTick run(SpiritHunger hunger, int ticks, float health, float maxHealth) {
        HungerTick tick = new HungerTick(hunger, HungerEffect.NONE);
        for (int i = 0; i < ticks; i++) {
            tick = tick.hunger().tick(RULES, health, maxHealth);
        }
        return tick;
    }

    @Test
    void full_NewState_HasMaxFoodAndSaturation() {
        SpiritHunger hunger = SpiritHunger.full();

        assertEquals(SpiritHunger.MAX_FOOD, hunger.foodLevel(), "Полный дух сыт по максимуму: иначе он ищет еду сытым");
        assertEquals(
                1.0,
                hunger.satietyFraction(),
                "Доля сытости полного духа — единица: по ней StrengthAssessor видит голод");
    }

    @Test
    void satietyFraction_HalfFood_Half() {
        assertEquals(
                0.5,
                SpiritHunger.of(10, 10, 0).satietyFraction(),
                "Доля сытости — сытость, делённая на максимум: фактор голода тактики считается от неё");
    }

    @Test
    void addExhaustion_BelowDrain_KeepsFood() {
        SpiritHunger hunger = SpiritHunger.full().addExhaustion(1.0);

        assertEquals(
                SpiritHunger.MAX_FOOD,
                hunger.foodLevel(),
                "Истощение меньше порога не тратит сытость: иначе каждый шаг лишает духа обеда");
        assertEquals(SpiritHunger.MAX_FOOD, hunger.saturation(), "Истощение меньше порога не тратит насыщение");
        assertEquals(1.0, hunger.exhaustion(), "Накопленное истощение видно до траты");
    }

    @Test
    void addExhaustion_ReachesDrain_SpendsSaturationFirst() {
        SpiritHunger hunger = SpiritHunger.full().addExhaustion(SpiritHunger.EXHAUSTION_PER_DRAIN);

        assertEquals(
                SpiritHunger.MAX_FOOD,
                hunger.foodLevel(),
                "Пока есть насыщение, сытость не тратится: насыщение — буфер быстрого голода");
        assertEquals(
                SpiritHunger.MAX_FOOD - 1.0,
                hunger.saturation(),
                "Порог истощения тратит единицу насыщения: без этого буфер не работает");
        assertEquals(0.0, hunger.exhaustion(), "Трата обнуляет накопленное до порога истощения");
    }

    @Test
    void addExhaustion_NoSaturation_SpendsFood() {
        SpiritHunger hunger = SpiritHunger.of(20, 0, 0).addExhaustion(SpiritHunger.EXHAUSTION_PER_DRAIN);

        assertEquals(
                19,
                hunger.foodLevel(),
                "Без насыщения порог истощения тратит сытость: иначе голод никогда не наступит");
    }

    @Test
    void addExhaustion_BigAmount_DrainsRepeatedly() {
        SpiritHunger hunger = SpiritHunger.of(20, 0, 0).addExhaustion(SpiritHunger.EXHAUSTION_PER_DRAIN * 3);

        assertEquals(17, hunger.foodLevel(), "Истощение сверх порога тратит несколько единиц сытости подряд");
        assertEquals(0.0, hunger.exhaustion(), "Остаток меньше порога остаётся неистраченным");
    }

    @Test
    void addExhaustion_Empty_StaysEmpty() {
        SpiritHunger hunger = SpiritHunger.empty().addExhaustion(10.0);

        assertEquals(0, hunger.foodLevel(), "Голодный дух не может уйти в минус: сытость неотрицательна");
    }

    @Test
    void eat_MoreNutrition_AddsFoodAndSaturation() {
        SpiritHunger hunger = SpiritHunger.empty().eat(5, 0.6);

        assertEquals(5, hunger.foodLevel(), "Съеденное добавляет питательность: хлеб 5 даёт 5 сытости");
        assertEquals(
                5.0,
                hunger.saturation(),
                "Насыщение — питательность на модификатор: хлеб 5 при 0.6 даёт буфер 5 (обрезан сытостью)");
    }

    @Test
    void eat_WhenFull_StaysFull() {
        SpiritHunger hunger = SpiritHunger.full().eat(5, 0.6);

        assertEquals(SpiritHunger.MAX_FOOD, hunger.foodLevel(), "Сытость не растёт выше шкалы 20");
        assertEquals(SpiritHunger.MAX_FOOD, hunger.saturation(), "Насыщение не выше сытости");
    }

    @Test
    void tick_WellFedAndHurt_HealsAtInterval() {
        HungerTick early = run(SpiritHunger.of(20, 0, 0), RULES.regenIntervalTicks() - 1, 10, 20);

        assertEquals(
                HungerEffect.NONE, early.effect(), "Восстановление не мгновенно: до интервала тик ничего не делает");

        HungerTick healed = run(SpiritHunger.of(20, 0, 0), RULES.regenIntervalTicks(), 10, 20);

        assertEquals(
                HungerEffect.HEAL,
                healed.effect(),
                "Сытный раненый дух лечится по интервалу: на этом стоит «поесть до максимума»");
        assertTrue(
                healed.hunger().foodLevel() < SpiritHunger.MAX_FOOD,
                "Лечение тратит сытость: восстановление не бесплатно");
    }

    @Test
    void tick_WellFedAndHealthy_DoesNotHeal() {
        HungerTick tick = run(SpiritHunger.of(20, 0, 0), RULES.regenIntervalTicks(), 20, 20);

        assertEquals(
                HungerEffect.NONE,
                tick.effect(),
                "Здоровый сытый дух не лечится: восстановление тратило бы еду впустую");
    }

    @Test
    void tick_BelowRegenThreshold_DoesNotHeal() {
        HungerTick tick =
                run(SpiritHunger.of(RULES.regenFoodThreshold() - 1, 0, 0), RULES.regenIntervalTicks(), 10, 20);

        assertEquals(
                HungerEffect.NONE,
                tick.effect(),
                "Ниже порога сытости лечение запрещено: иначе голодный дух восстановится без обеда");
    }

    @Test
    void tick_StarvingAtInterval_Damages() {
        HungerTick starving = run(SpiritHunger.empty(), RULES.starveIntervalTicks(), 20, 20);

        assertEquals(
                HungerEffect.STARVE,
                starving.effect(),
                "На нуле сытости дух получает урон от голода: голод наказывает, а не только блокирует лечение");

        HungerTick atFloor = run(SpiritHunger.empty(), RULES.starveIntervalTicks(), RULES.starveFloor(), 20);

        assertEquals(
                HungerEffect.NONE,
                atFloor.effect(),
                "У порога голодного истощения урон прекращается: на обычной сложности голод не убивает");
    }

    @Test
    void tick_Dead_NoHealNoDamage() {
        HungerTick tick = run(SpiritHunger.empty(), RULES.starveIntervalTicks(), 0, 20);

        assertEquals(HungerEffect.NONE, tick.effect(), "Мёртвого не лечат и не ранят: эффект голода — для живых");
    }

    @Test
    void constructor_BadValues_Throw() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SpiritHunger.of(21, 0, 0),
                "Сытость выше 20 отвергается: шкала голода по образцу ванили");
        assertThrows(
                IllegalArgumentException.class,
                () -> SpiritHunger.of(-1, 0, 0),
                "Отрицательная сытость отвергается: голод не уходит в минус");
        assertThrows(
                IllegalArgumentException.class,
                () -> SpiritHunger.of(5, 6, 0),
                "Насыщение выше сытости отвергается: буфер не переживает шкалу");
        assertThrows(
                IllegalArgumentException.class,
                () -> SpiritHunger.of(5, 0, SpiritHunger.EXHAUSTION_PER_DRAIN),
                "Истощение на самом пороге отвергается: оно обязано быть уже истрачено");
        assertThrows(
                IllegalArgumentException.class,
                () -> new SpiritHunger(5, 0, 0, -1),
                "Отрицательный таймер отвергается: отсчёт не идёт назад");
    }

    @Test
    void foodText_NineteenOfTwenty_PrintsFraction() {
        assertEquals(
                "food 0.95 saturation 0.0",
                SpiritHunger.of(19, 0, 0).foodText(),
                "Лог еды обязан печатать ту же долю шкалы [0; 1], что REST food: иначе 19 в логе и 0.95 "
                        + "в /v1/bots выглядят как разные величины");
    }

    @Test
    void addExhaustion_BadAmount_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SpiritHunger.full().addExhaustion(-1),
                "Отрицательный расход отвергается: истощение не отматывается назад");
        assertThrows(
                IllegalArgumentException.class,
                () -> SpiritHunger.full().addExhaustion(Double.NaN),
                "NaN-расход отвергается: накопленное истощение не станет NaN");
    }
}
