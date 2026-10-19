package com.theminespirit.core.tactics;

import com.theminespirit.core.reload.DataTables;

public record TacticsConfig(EnemyProfiles enemies, ScoreWeights weights, Rules rules, Modes modes) {
    public static TacticsConfig loadDefault() {
        return DataTables.INSTANCE.tactics().current();
    }

    public static TacticsConfig fromJson(String enemiesJson, String weightsJson, String rulesJson, String modesJson) {
        return new TacticsConfig(
                EnemyProfilesParser.parse(ConfigNode.root("enemies.json", enemiesJson)),
                WeightsParser.parse(ConfigNode.root("weights.json", weightsJson)),
                RulesParser.parse(ConfigNode.root("rules.json", rulesJson)),
                ModesParser.parse(ConfigNode.root("modes.json", modesJson)));
    }

    /** Начальная версия для держателя: читается один раз за процесс. */
    public static TacticsConfig readFromClasspath() {
        return fromJson(TacticsResources.read("enemies.json"), TacticsResources.read("weights.json"),
                TacticsResources.read("rules.json"), TacticsResources.read("modes.json"));
    }
}