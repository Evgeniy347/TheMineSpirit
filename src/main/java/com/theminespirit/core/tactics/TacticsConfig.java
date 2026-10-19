package com.theminespirit.core.tactics;

import com.theminespirit.core.reload.DataTables;
import java.util.concurrent.atomic.AtomicInteger;

public record TacticsConfig(EnemyProfiles enemies, ScoreWeights weights, Rules rules, Modes modes) {
    private static final AtomicInteger LOADS = new AtomicInteger();

    public static TacticsConfig loadDefault() {
        return DataTables.INSTANCE.tactics().current();
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

    public static TacticsConfig readFromClasspath() {
        LOADS.incrementAndGet();
        return fromJson(TacticsResources.read("enemies.json"), TacticsResources.read("weights.json"),
                TacticsResources.read("rules.json"), TacticsResources.read("modes.json"));
    }
}