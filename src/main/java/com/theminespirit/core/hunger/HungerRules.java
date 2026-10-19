package com.theminespirit.core.hunger;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.reload.DataTables;
import java.util.Set;

public record HungerRules(
        int regenFoodThreshold,
        int regenIntervalTicks,
        double regenExhaustion,
        int starveIntervalTicks,
        float starveFloor,
        int eatFoodThreshold,
        double sprintExhaustionPerMeter,
        double combatExhaustionPerAttack,
        double digExhaustionPerBlock,
        double damageExhaustionPerHit) {

    public static final String RESOURCE = "data/theminespirit/hunger/hunger.json";

    private static final String REGEN_INTERVAL_TICKS = "regenIntervalTicks";
    private static final String REGEN_EXHAUSTION = "regenExhaustion";
    private static final String STARVE_INTERVAL_TICKS = "starveIntervalTicks";
    private static final String SPRINT_EXHAUSTION_PER_METER = "sprintExhaustionPerMeter";
    private static final String COMBAT_EXHAUSTION_PER_ATTACK = "combatExhaustionPerAttack";
    private static final String DIG_EXHAUSTION_PER_BLOCK = "digExhaustionPerBlock";
    private static final String DAMAGE_EXHAUSTION_PER_HIT = "damageExhaustionPerHit";
    private static final Set<String> FIELDS = Set.of(
            "regenFoodThreshold",
            REGEN_INTERVAL_TICKS,
            REGEN_EXHAUSTION,
            STARVE_INTERVAL_TICKS,
            "starveFloor",
            "eatFoodThreshold",
            SPRINT_EXHAUSTION_PER_METER,
            COMBAT_EXHAUSTION_PER_ATTACK,
            DIG_EXHAUSTION_PER_BLOCK,
            DAMAGE_EXHAUSTION_PER_HIT);

    private static final HungerRules DEFAULTS = new HungerRules(18, 80, 6.0, 80, 1.0F, 14, 0.1, 0.1, 0.005, 0.1);

    public HungerRules {
        if (regenFoodThreshold < 0 || regenFoodThreshold > SpiritHunger.MAX_FOOD) {
            throw new IllegalArgumentException("Порог восстановления " + regenFoodThreshold + " вне [0, "
                    + SpiritHunger.MAX_FOOD + "]: сытость, ниже которой дух не лечится, вне шкалы голода");
        }
        if (starveFloor < 0) {
            throw new IllegalArgumentException("Порог голодной смерти " + starveFloor
                    + " меньше 0: здоровье не может быть отрицательным, а «не до смерти» — это порог выше нуля");
        }
        if (starveFloor >= SpiritHunger.MAX_FOOD) {
            throw new IllegalArgumentException("Порог голодной смерти " + starveFloor + " не меньше "
                    + SpiritHunger.MAX_FOOD + ": голод не может калечить сытого, это ошибка данных");
        }
        if (eatFoodThreshold < 0 || eatFoodThreshold > SpiritHunger.MAX_FOOD) {
            throw new IllegalArgumentException("Порог обеда " + eatFoodThreshold + " вне [0, " + SpiritHunger.MAX_FOOD
                    + "]: дух не поймёт, при какой сытости искать еду");
        }
        requireTicks(REGEN_INTERVAL_TICKS, regenIntervalTicks);
        requireTicks(STARVE_INTERVAL_TICKS, starveIntervalTicks);
        requireRate(REGEN_EXHAUSTION, regenExhaustion);
        requireRate(SPRINT_EXHAUSTION_PER_METER, sprintExhaustionPerMeter);
        requireRate(COMBAT_EXHAUSTION_PER_ATTACK, combatExhaustionPerAttack);
        requireRate(DIG_EXHAUSTION_PER_BLOCK, digExhaustionPerBlock);
        requireRate(DAMAGE_EXHAUSTION_PER_HIT, damageExhaustionPerHit);
    }

    public static HungerRules defaults() {
        return DEFAULTS;
    }

    public static HungerRules loadDefault() {
        return DataTables.INSTANCE.hunger().current();
    }

    public static HungerRules load() {
        return parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }

    public static HungerRules parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, FIELDS, origin, "hunger.json");
        try {
            return new HungerRules(
                    JsonResource.integer(root.get("regenFoodThreshold"), origin, "regenFoodThreshold"),
                    JsonResource.integer(root.get(REGEN_INTERVAL_TICKS), origin, REGEN_INTERVAL_TICKS),
                    JsonResource.decimal(root.get(REGEN_EXHAUSTION), origin, REGEN_EXHAUSTION),
                    JsonResource.integer(root.get(STARVE_INTERVAL_TICKS), origin, STARVE_INTERVAL_TICKS),
                    (float) JsonResource.decimal(root.get("starveFloor"), origin, "starveFloor"),
                    JsonResource.integer(root.get("eatFoodThreshold"), origin, "eatFoodThreshold"),
                    JsonResource.decimal(root.get(SPRINT_EXHAUSTION_PER_METER), origin, SPRINT_EXHAUSTION_PER_METER),
                    JsonResource.decimal(root.get(COMBAT_EXHAUSTION_PER_ATTACK), origin, COMBAT_EXHAUSTION_PER_ATTACK),
                    JsonResource.decimal(root.get(DIG_EXHAUSTION_PER_BLOCK), origin, DIG_EXHAUSTION_PER_BLOCK),
                    JsonResource.decimal(root.get(DAMAGE_EXHAUSTION_PER_HIT), origin, DAMAGE_EXHAUSTION_PER_HIT));
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': " + error.getMessage(), error);
        }
    }

    private static void requireTicks(String name, int ticks) {
        if (ticks < 1) {
            throw new IllegalArgumentException("Поле " + name + " = " + ticks
                    + ": интервал считается в тиках, он обязан быть не меньше 1, иначе эффект срабатывает каждый тик");
        }
    }

    private static void requireRate(String name, double rate) {
        if (!Double.isFinite(rate) || rate < 0) {
            throw new IllegalArgumentException("Поле " + name + " = " + rate
                    + ": расход обязан быть конечным и не меньше 0, иначе голод растёт на пустом месте");
        }
    }
}
