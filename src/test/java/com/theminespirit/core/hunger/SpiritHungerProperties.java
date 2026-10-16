package com.theminespirit.core.hunger;

import static org.junit.jupiter.api.Assertions.assertTrue;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;

class SpiritHungerProperties {
    private static final HungerRules RULES = HungerRules.defaults();

    private static SpiritHunger anyState(int food, double saturation, double exhaustion) {
        return SpiritHunger.of(food, Math.min(saturation, food), Math.min(exhaustion, 3.9));
    }

    @Property
    void satietyFraction_AnyState_StaysInUnitRange(@ForAll @IntRange(min = 0, max = 20) int food,
            @ForAll @DoubleRange(min = 0, max = 20) double saturation,
            @ForAll @DoubleRange(min = 0, max = 3.9) double exhaustion) {
        double fraction = anyState(food, saturation, exhaustion).satietyFraction();

        assertTrue(fraction >= 0.0 && fraction <= 1.0,
                "Доля сытости обязана остаться в [0;1] при любом состоянии: иначе фактор голода тактики "
                        + "получит духа сытее полного или голоднее пустого");
    }

    @Property
    void eat_AnyFood_NeverExceedsScale(@ForAll @IntRange(min = 0, max = 20) int food,
            @ForAll @DoubleRange(min = 0, max = 20) double saturation,
            @ForAll @IntRange(min = 0, max = 20) int nutrition,
            @ForAll @DoubleRange(min = 0, max = 1.2) double modifier) {
        SpiritHunger eaten = anyState(food, saturation, 0).eat(nutrition, modifier);

        assertTrue(eaten.foodLevel() <= SpiritHunger.MAX_FOOD,
                "Сытость не перерастает шкалу 20: иначе голод станет отрицательным по знаку");
        assertTrue(eaten.saturation() <= eaten.foodLevel(),
                "Насыщение остаётся в пределах сытости: буфер еды не переживает шкалу");
        assertTrue(eaten.foodLevel() >= food,
                "Еда не отнимает сытость: питательность неотрицательна");
    }

    @Property
    void addExhaustion_AnyAmount_StaysValid(@ForAll @IntRange(min = 0, max = 20) int food,
            @ForAll @DoubleRange(min = 0, max = 20) double saturation,
            @ForAll @DoubleRange(min = 0, max = 40) double amount) {
        SpiritHunger hungry = anyState(food, saturation, 0).addExhaustion(amount);

        assertTrue(hungry.foodLevel() >= 0 && hungry.foodLevel() <= food,
                "Истощение не восполняет и не переполняет сытость: расход только уменьшает её");
        assertTrue(hungry.saturation() >= 0 && hungry.saturation() <= hungry.foodLevel(),
                "Насыщение после расхода остаётся в [0; сытость]: состояние сытости всегда допустимо");
        assertTrue(hungry.exhaustion() >= 0 && hungry.exhaustion() < SpiritHunger.EXHAUSTION_PER_DRAIN,
                "Остаток истощения меньше порога траты: иначе единица сытости осталась бы неизрасходованной");
    }

    @Property
    void tick_AnyState_ProducesValidHunger(@ForAll @IntRange(min = 0, max = 20) int food,
            @ForAll @DoubleRange(min = 0, max = 20) double saturation,
            @ForAll @DoubleRange(min = 0, max = 3.9) double exhaustion,
            @ForAll @DoubleRange(min = 0, max = 20) double health) {
        HungerTick tick = anyState(food, saturation, exhaustion).tick(RULES, (float) health, 20);

        assertTrue(tick.hunger().regenTimer() >= 0,
                "Таймер тика не уходит в минус: иначе восстановление или голод не наступит вовсе");
        assertTrue(tick.hunger().foodLevel() >= 0 && tick.hunger().foodLevel() <= SpiritHunger.MAX_FOOD,
                "Сытость после тика остаётся в шкале: голод не портит состояние духа");
    }
}
