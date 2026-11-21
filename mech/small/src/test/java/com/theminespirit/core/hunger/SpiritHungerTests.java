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

        assertEquals(
                SpiritHunger.MAX_FOOD,
                hunger.foodLevel(),
                "Полный дух сыт по максимуму, иначе он ищет еду сытым и тратит запас поселения впустую. При смене "
                        + "SpiritHunger.full проверь долю ниже в тесте");
        assertEquals(
                1.0,
                hunger.satietyFraction(),
                "Доля сытости полного духа — единица: по ней StrengthAssessor видит голод, иначе тактика слепа. При "
                        + "смене satietyFraction проверь строку половины ниже в тесте");
    }

    @Test
    void satietyFraction_HalfFood_Half() {
        assertEquals(
                0.5,
                SpiritHunger.of(10, 10, 0).satietyFraction(),
                "Доля сытости — сытость, делённая на максимум: фактор голода тактики считается от неё, иначе оценка "
                        + "неверна. При смене satietyFraction проверь строку истощения ниже в тесте");
    }

    @Test
    void addExhaustion_BelowDrain_KeepsFood() {
        SpiritHunger hunger = SpiritHunger.full().addExhaustion(1.0);

        assertEquals(
                SpiritHunger.MAX_FOOD,
                hunger.foodLevel(),
                "Истощение меньше порога не тратит сытость, иначе каждый шаг лишает духа обеда. При смене "
                        + "addExhaustion проверь насыщение ниже в тесте");
        assertEquals(
                SpiritHunger.MAX_FOOD,
                hunger.saturation(),
                "Истощение меньше порога не тратит насыщение, иначе буфер тает на каждом шаге. При смене "
                        + "addExhaustion проверь строку накопления ниже в тесте");
        assertEquals(
                1.0,
                hunger.exhaustion(),
                "Накопленное истощение видно до траты, иначе расход не учитывается. При смене addExhaustion проверь "
                        + "строку порога ниже в тесте");
    }

    @Test
    void addExhaustion_ReachesDrain_SpendsSaturationFirst() {
        SpiritHunger hunger = SpiritHunger.full().addExhaustion(SpiritHunger.EXHAUSTION_PER_DRAIN);

        assertEquals(
                SpiritHunger.MAX_FOOD,
                hunger.foodLevel(),
                "Пока есть насыщение, сытость не тратится: насыщение — буфер быстрого голода, иначе сытость падает "
                        + "раньше. При смене addExhaustion проверь строку насыщения ниже в тесте");
        assertEquals(
                SpiritHunger.MAX_FOOD - 1.0,
                hunger.saturation(),
                "Порог истощения тратит единицу насыщения: без этого буфер не работает, иначе истощение не тратится. "
                        + "При смене проверки проверь строку обнуления ниже в тесте");
        assertEquals(
                0.0,
                hunger.exhaustion(),
                "Трата обнуляет накопленное до порога истощения, иначе остаток копится. При смене addExhaustion "
                        + "проверь строку без насыщения ниже в тесте");
    }

    @Test
    void addExhaustion_NoSaturation_SpendsFood() {
        SpiritHunger hunger = SpiritHunger.of(20, 0, 0).addExhaustion(SpiritHunger.EXHAUSTION_PER_DRAIN);

        assertEquals(
                19,
                hunger.foodLevel(),
                "Без насыщения порог истощения тратит сытость, иначе голод никогда не наступит. При смене "
                        + "addExhaustion проверь строку большого ниже в тесте");
    }

    @Test
    void addExhaustion_BigAmount_DrainsRepeatedly() {
        SpiritHunger hunger = SpiritHunger.of(20, 0, 0).addExhaustion(SpiritHunger.EXHAUSTION_PER_DRAIN * 3);

        assertEquals(
                17,
                hunger.foodLevel(),
                "Истощение сверх порога тратит несколько единиц сытости подряд, иначе излишек теряется. При смене "
                        + "addExhaustion проверь остаток ниже в тесте");
        assertEquals(
                0.0,
                hunger.exhaustion(),
                "Остаток меньше порога остаётся неистраченным, иначе лишняя единица тратится. При смене "
                        + "addExhaustion проверь строку пустого ниже в тесте");
    }

    @Test
    void addExhaustion_Empty_StaysEmpty() {
        SpiritHunger hunger = SpiritHunger.empty().addExhaustion(10.0);

        assertEquals(
                0,
                hunger.foodLevel(),
                "Голодный дух не может уйти в минус: сытость неотрицательна, иначе дух уходит в минус. При смене "
                        + "addExhaustion проверь строку еды ниже в тесте");
    }

    @Test
    void eat_MoreNutrition_AddsFoodAndSaturation() {
        SpiritHunger hunger = SpiritHunger.empty().eat(5, 0.6);

        assertEquals(
                5,
                hunger.foodLevel(),
                "Съеденное добавляет питательность: хлеб 5 даёт 5 сытости, иначе еда не кормит. При смене eat "
                        + "проверь насыщение ниже в тесте");
        assertEquals(
                5.0,
                hunger.saturation(),
                "Насыщение — питательность на модификатор: хлеб 5 при 0.6 даёт буфер 5 (обрезан сытостью), иначе "
                        + "буфер больше еды. При смене eat проверь строку полного ниже в тесте");
    }

    @Test
    void eat_WhenFull_StaysFull() {
        SpiritHunger hunger = SpiritHunger.full().eat(5, 0.6);

        assertEquals(
                SpiritHunger.MAX_FOOD,
                hunger.foodLevel(),
                "Сытость не растёт выше шкалы 20, иначе шкала переполняется и голод считается отрицательным. При "
                        + "смене eat проверь насыщение ниже в тесте");
        assertEquals(
                SpiritHunger.MAX_FOOD,
                hunger.saturation(),
                "Насыщение не выше сытости, иначе буфер переживает шкалу и StrengthAssessor ошибается. При смене eat "
                        + "проверь строку тика ниже в тесте");
    }

    @Test
    void tick_WellFedAndHurt_HealsAtInterval() {
        HungerTick early = run(SpiritHunger.of(20, 0, 0), RULES.regenIntervalTicks() - 1, 10, 20);

        assertEquals(
                HungerEffect.NONE,
                early.effect(),
                "Восстановление не мгновенно: до интервала тик ничего не делает, иначе еда тратится сразу. При смене "
                        + "tick проверь строку лечения ниже в тесте");

        HungerTick healed = run(SpiritHunger.of(20, 0, 0), RULES.regenIntervalTicks(), 10, 20);

        assertEquals(
                HungerEffect.HEAL,
                healed.effect(),
                "Сытный раненый дух лечится по интервалу: на этом стоит «поесть до максимума», иначе лечение не "
                        + "наступает. При смене tick проверь строку расхода ниже в тесте");
        assertTrue(
                healed.hunger().foodLevel() < SpiritHunger.MAX_FOOD,
                "Лечение тратит сытость: восстановление не бесплатно, иначе еда бесконечна. При смене regenExhaustion "
                        + "проверь строку здорового ниже в тесте");
    }

    @Test
    void tick_WellFedAndHealthy_DoesNotHeal() {
        HungerTick tick = run(SpiritHunger.of(20, 0, 0), RULES.regenIntervalTicks(), 20, 20);

        assertEquals(
                HungerEffect.NONE,
                tick.effect(),
                "Здоровый сытый дух не лечится: восстановление тратило бы еду впустую, иначе дух ест без раны. При "
                        + "смене tick проверь строку порога ниже в тесте");
    }

    @Test
    void tick_BelowRegenThreshold_DoesNotHeal() {
        HungerTick tick =
                run(SpiritHunger.of(RULES.regenFoodThreshold() - 1, 0, 0), RULES.regenIntervalTicks(), 10, 20);

        assertEquals(
                HungerEffect.NONE,
                tick.effect(),
                "Ниже порога сытости лечение запрещено, иначе голодный дух восстановится без обеда. При смене "
                        + "regenFoodThreshold проверь строку голода ниже в тесте");
    }

    @Test
    void tick_StarvingAtInterval_Damages() {
        HungerTick starving = run(SpiritHunger.empty(), RULES.starveIntervalTicks(), 20, 20);

        assertEquals(
                HungerEffect.STARVE,
                starving.effect(),
                "На нуле сытости дух получает урон от голода: голод наказывает, а не только блокирует лечение, иначе "
                        + "голод безопасен. При смене tick проверь строку порога ниже в тесте");

        HungerTick atFloor = run(SpiritHunger.empty(), RULES.starveIntervalTicks(), RULES.starveFloor(), 20);

        assertEquals(
                HungerEffect.NONE,
                atFloor.effect(),
                "У порога голодного истощения урон прекращается: на обычной сложности голод не убивает, иначе дух "
                        + "умирает от голода. При смене starveFloor проверь строку мёртвого ниже в тесте");
    }

    @Test
    void tick_Dead_NoHealNoDamage() {
        HungerTick tick = run(SpiritHunger.empty(), RULES.starveIntervalTicks(), 0, 20);

        assertEquals(
                HungerEffect.NONE,
                tick.effect(),
                "Мёртвого не лечат и не ранят: эффект голода — для живых, иначе труп лечат. При смене tick проверь "
                        + "строку конструктора ниже в тесте");
    }

    @Test
    void constructor_BadValues_Throw() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SpiritHunger.of(21, 0, 0),
                "Сытость выше 20 отвергается: шкала голода по образцу ванили, иначе состояние вне шкалы. При смене "
                        + "конструктора проверь строку -1 ниже в тесте");
        assertThrows(
                IllegalArgumentException.class,
                () -> SpiritHunger.of(-1, 0, 0),
                "Отрицательная сытость отвергается: голод не уходит в минус, иначе шкала ломается. При смене проверки проверь строку "
                        + "насыщения ниже в тесте");
        assertThrows(
                IllegalArgumentException.class,
                () -> SpiritHunger.of(5, 6, 0),
                "Насыщение выше сытости отвергается: буфер не переживает шкалу, иначе насыщение врёт. При смене проверки проверь строку "
                        + "порога ниже в тесте");
        assertThrows(
                IllegalArgumentException.class,
                () -> SpiritHunger.of(5, 0, SpiritHunger.EXHAUSTION_PER_DRAIN),
                "Истощение на самом пороге отвергается: оно обязано быть уже истрачено, иначе порог не тратится. При "
                        + "смене проверки проверь строку таймера ниже в тесте");
        assertThrows(
                IllegalArgumentException.class,
                () -> new SpiritHunger(5, 0, 0, -1),
                "Отрицательный таймер отвергается: отсчёт не идёт назад, иначе таймер зависает. При смене проверки проверь строку текста "
                        + "ниже в тесте");
    }

    @Test
    void foodText_NineteenOfTwenty_PrintsFraction() {
        assertEquals(
                "food 0.95 saturation 0.0",
                SpiritHunger.of(19, 0, 0).foodText(),
                "Лог еды обязан печатать ту же долю шкалы [0; 1], что REST food: иначе 19 в логе и 0.95 в /v1/bots "
                        + "выглядят как разные величины. При смене foodText проверь строку расхода ниже в тесте");
    }

    @Test
    void addExhaustion_BadAmount_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SpiritHunger.full().addExhaustion(-1),
                "Отрицательный расход отвергается: истощение не отматывается назад, иначе шкала уйдёт "
                        + "в минус. При смене addExhaustion проверь "
                        + "строку NaN ниже в тесте");
        assertThrows(
                IllegalArgumentException.class,
                () -> SpiritHunger.full().addExhaustion(Double.NaN),
                "NaN-расход отвергается: накопленное истощение не станет NaN, иначе состояние ломается. При смене "
                        + "проверки проверь строку выше в тесте");
    }
}
