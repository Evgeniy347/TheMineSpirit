package com.theminespirit.core.tactics;

import java.util.Map;
import java.util.Optional;

public final class SpiritTargets {
    public enum Condition {
        ALWAYS {
            @Override
            public boolean holds(float mobLightMagicValue, boolean day, boolean targetInWater) {
                return true;
            }
        },
        LIGHT_BELOW_HALF {
            @Override
            public boolean holds(float mobLightMagicValue, boolean day, boolean targetInWater) {
                return mobLightMagicValue < 0.5F;
            }
        },
        NIGHT_OR_IN_WATER {
            @Override
            public boolean holds(float mobLightMagicValue, boolean day, boolean targetInWater) {
                return !day || targetInWater;
            }
        };

        public abstract boolean holds(float mobLightMagicValue, boolean day, boolean targetInWater);
    }

    private static final Map<String, Condition> RULES = Map.ofEntries(
            Map.entry("minecraft:blaze", Condition.ALWAYS),
            Map.entry("minecraft:bogged", Condition.ALWAYS),
            Map.entry("minecraft:cave_spider", Condition.LIGHT_BELOW_HALF),
            Map.entry("minecraft:creeper", Condition.ALWAYS),
            Map.entry("minecraft:drowned", Condition.NIGHT_OR_IN_WATER),
            Map.entry("minecraft:elder_guardian", Condition.ALWAYS),
            Map.entry("minecraft:endermite", Condition.ALWAYS),
            Map.entry("minecraft:ghast", Condition.ALWAYS),
            Map.entry("minecraft:giant", Condition.ALWAYS),
            Map.entry("minecraft:guardian", Condition.ALWAYS),
            Map.entry("minecraft:husk", Condition.ALWAYS),
            Map.entry("minecraft:magma_cube", Condition.ALWAYS),
            Map.entry("minecraft:phantom", Condition.ALWAYS),
            Map.entry("minecraft:shulker", Condition.ALWAYS),
            Map.entry("minecraft:silverfish", Condition.ALWAYS),
            Map.entry("minecraft:skeleton", Condition.ALWAYS),
            Map.entry("minecraft:slime", Condition.ALWAYS),
            Map.entry("minecraft:spider", Condition.LIGHT_BELOW_HALF),
            Map.entry("minecraft:stray", Condition.ALWAYS),
            Map.entry("minecraft:vex", Condition.ALWAYS),
            Map.entry("minecraft:witch", Condition.ALWAYS),
            Map.entry("minecraft:wither", Condition.ALWAYS),
            Map.entry("minecraft:wither_skeleton", Condition.ALWAYS),
            Map.entry("minecraft:zombie", Condition.ALWAYS),
            Map.entry("minecraft:zombie_villager", Condition.ALWAYS));

    private SpiritTargets() {}

    public static boolean attacksSpirits(String entityTypeId) {
        return conditionFor(entityTypeId).isPresent();
    }

    public static Optional<Condition> conditionFor(String entityTypeId) {
        if (entityTypeId == null || entityTypeId.isBlank()) {
            throw new IllegalArgumentException(
                    "entityTypeId пуст: тип моба называется его id, " + "например minecraft:zombie");
        }
        return Optional.ofNullable(RULES.get(entityTypeId));
    }
}
