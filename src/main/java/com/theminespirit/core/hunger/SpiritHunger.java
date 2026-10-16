package com.theminespirit.core.hunger;

public record SpiritHunger(int foodLevel, double saturation, double exhaustion, int regenTimer) {

    public static final int MAX_FOOD = 20;
    public static final double EXHAUSTION_PER_DRAIN = 4.0;

    public SpiritHunger {
        if (foodLevel < 0 || foodLevel > MAX_FOOD) {
            throw new IllegalArgumentException("Сытость " + foodLevel + " вне [0, " + MAX_FOOD
                    + "]: шкала голода духа по образцу ванили");
        }
        if (!Double.isFinite(saturation) || saturation < 0) {
            throw new IllegalArgumentException("Насыщение " + saturation
                    + ": обязано быть конечным и не меньше 0, иначе запас еды отрицателен");
        }
        if (saturation > foodLevel) {
            throw new IllegalArgumentException("Насыщение " + saturation + " больше сытости " + foodLevel
                    + ": запас еды не переживает шкалу, которая его держит");
        }
        if (!Double.isFinite(exhaustion) || exhaustion < 0 || exhaustion >= EXHAUSTION_PER_DRAIN) {
            throw new IllegalArgumentException("Истощение " + exhaustion + " вне [0, " + EXHAUSTION_PER_DRAIN
                    + "): накопленное сверх порога немедленно тратит сытость");
        }
        if (regenTimer < 0) {
            throw new IllegalArgumentException("Таймер восстановления " + regenTimer
                    + ": отсчёт не может быть отрицательным");
        }
    }

    public static SpiritHunger full() {
        return new SpiritHunger(MAX_FOOD, MAX_FOOD, 0, 0);
    }

    public static SpiritHunger empty() {
        return new SpiritHunger(0, 0, 0, 0);
    }

    public static SpiritHunger of(int foodLevel, double saturation, double exhaustion) {
        return new SpiritHunger(foodLevel, saturation, exhaustion, 0);
    }

    public double satietyFraction() {
        return (double) foodLevel / MAX_FOOD;
    }

    public SpiritHunger addExhaustion(double amount) {
        if (!Double.isFinite(amount) || amount < 0) {
            throw new IllegalArgumentException("Расход " + amount
                    + ": истощение начисляется конечной неотрицательной величиной");
        }
        double left = exhaustion + amount;
        int food = foodLevel;
        double sat = saturation;
        while (left >= EXHAUSTION_PER_DRAIN) {
            left -= EXHAUSTION_PER_DRAIN;
            if (sat > 0) {
                sat = Math.max(0, sat - 1);
            } else if (food > 0) {
                food--;
            }
        }
        return new SpiritHunger(food, sat, left, regenTimer);
    }

    public SpiritHunger eat(int nutrition, double saturationModifier) {
        if (nutrition < 0) {
            throw new IllegalArgumentException("Питательность " + nutrition
                    + ": съеденное не может отнимать сытость");
        }
        if (!Double.isFinite(saturationModifier) || saturationModifier < 0) {
            throw new IllegalArgumentException("Модификатор насыщения " + saturationModifier
                    + ": обязан быть конечным и не меньше 0");
        }
        int food = Math.min(MAX_FOOD, foodLevel + nutrition);
        double sat = Math.min(food, saturation + nutrition * saturationModifier * 2.0);
        return new SpiritHunger(food, sat, exhaustion, regenTimer);
    }

    public HungerTick tick(HungerRules rules, float health, float maxHealth) {
        if (health <= 0) {
            return new HungerTick(this, HungerEffect.NONE);
        }
        if (foodLevel <= 0) {
            int timer = regenTimer + 1;
            if (timer < rules.starveIntervalTicks()) {
                return new HungerTick(withTimer(timer), HungerEffect.NONE);
            }
            HungerEffect effect = health > rules.starveFloor() ? HungerEffect.STARVE : HungerEffect.NONE;
            return new HungerTick(withTimer(0), effect);
        }
        if (foodLevel >= rules.regenFoodThreshold() && health < maxHealth) {
            int timer = regenTimer + 1;
            if (timer < rules.regenIntervalTicks()) {
                return new HungerTick(withTimer(timer), HungerEffect.NONE);
            }
            return new HungerTick(withTimer(0).addExhaustion(rules.regenExhaustion()), HungerEffect.HEAL);
        }
        return new HungerTick(withTimer(0), HungerEffect.NONE);
    }

    private SpiritHunger withTimer(int timer) {
        return new SpiritHunger(foodLevel, saturation, exhaustion, timer);
    }
}
