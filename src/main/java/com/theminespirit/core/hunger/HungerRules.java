package com.theminespirit.core.hunger;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
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

    private static final Set<String> FIELDS = Set.of("regenFoodThreshold", "regenIntervalTicks", "regenExhaustion",
            "starveIntervalTicks", "starveFloor", "eatFoodThreshold", "sprintExhaustionPerMeter",
            "combatExhaustionPerAttack", "digExhaustionPerBlock", "damageExhaustionPerHit");

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
        requireTicks("regenIntervalTicks", regenIntervalTicks);
        requireTicks("starveIntervalTicks", starveIntervalTicks);
        requireRate("regenExhaustion", regenExhaustion);
        requireRate("sprintExhaustionPerMeter", sprintExhaustionPerMeter);
        requireRate("combatExhaustionPerAttack", combatExhaustionPerAttack);
        requireRate("digExhaustionPerBlock", digExhaustionPerBlock);
        requireRate("damageExhaustionPerHit", damageExhaustionPerHit);
    }

    public static HungerRules defaults() {
        return DEFAULTS;
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
                    JsonResource.integer(root.get("regenIntervalTicks"), origin, "regenIntervalTicks"),
                    JsonResource.decimal(root.get("regenExhaustion"), origin, "regenExhaustion"),
                    JsonResource.integer(root.get("starveIntervalTicks"), origin, "starveIntervalTicks"),
                    (float) JsonResource.decimal(root.get("starveFloor"), origin, "starveFloor"),
                    JsonResource.integer(root.get("eatFoodThreshold"), origin, "eatFoodThreshold"),
                    JsonResource.decimal(root.get("sprintExhaustionPerMeter"), origin,
                            "sprintExhaustionPerMeter"),
                    JsonResource.decimal(root.get("combatExhaustionPerAttack"), origin,
                            "combatExhaustionPerAttack"),
                    JsonResource.decimal(root.get("digExhaustionPerBlock"), origin, "digExhaustionPerBlock"),
                    JsonResource.decimal(root.get("damageExhaustionPerHit"), origin, "damageExhaustionPerHit"));
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
