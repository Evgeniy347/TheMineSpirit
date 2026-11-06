package com.theminespirit.core.tactics;

import com.theminespirit.core.reload.TableSlot;
import java.util.function.Supplier;

public record TacticsConfig(EnemyProfiles enemies, ScoreWeights weights, Rules rules, Modes modes) {
    private static final TableSlot<TacticsConfig> SOURCE = new TableSlot<>(TacticsConfig::readFromClasspath);

    public static TacticsConfig loadDefault() {
        return SOURCE.current();
    }

    public static void useSource(Supplier<TacticsConfig> supplier) {
        SOURCE.use(supplier);
    }

    public static TacticsConfig fromJson(String enemiesJson, String weightsJson, String rulesJson, String modesJson) {
        return new TacticsConfig(
                EnemyProfilesParser.parse(ConfigNode.root("enemies.json", enemiesJson)),
                WeightsParser.parse(ConfigNode.root("weights.json", weightsJson)),
                RulesParser.parse(ConfigNode.root("rules.json", rulesJson)),
                ModesParser.parse(ConfigNode.root("modes.json", modesJson)));
    }

    public static TacticsConfig readFromClasspath() {
        return fromJson(
                TacticsResources.read("enemies.json"),
                TacticsResources.read("weights.json"),
                TacticsResources.read("rules.json"),
                TacticsResources.read("modes.json"));
    }
}
