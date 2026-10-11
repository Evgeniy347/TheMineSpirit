package com.theminespirit.core.tactics;

import java.util.concurrent.atomic.AtomicInteger;

public record TacticsConfig(EnemyProfiles enemies, ScoreWeights weights, Rules rules, Modes modes) {
    private static final AtomicInteger LOADS = new AtomicInteger();

    public static TacticsConfig loadDefault() {
        return DefaultHolder.INSTANCE;
    }

    public static int defaultLoads() {
        return LOADS.get();
    }

    public static TacticsConfig fromJson(String enemiesJson, String weightsJson, String rulesJson, String modesJson) {
        return new TacticsConfig(
                EnemyProfilesParser.parse(ConfigNode.root("enemies.json", enemiesJson)),
                WeightsParser.parse(ConfigNode.root("weights.json", weightsJson)),
                RulesParser.parse(ConfigNode.root("rules.json", rulesJson)),
                ModesParser.parse(ConfigNode.root("modes.json", modesJson)));
    }

    private static final class DefaultHolder {
        private static final TacticsConfig INSTANCE = readDefault();

        private static TacticsConfig readDefault() {
            LOADS.incrementAndGet();
            return fromJson(TacticsResources.read("enemies.json"), TacticsResources.read("weights.json"),
                    TacticsResources.read("rules.json"), TacticsResources.read("modes.json"));
        }
    }
}
