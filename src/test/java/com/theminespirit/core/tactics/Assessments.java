package com.theminespirit.core.tactics;

final class Assessments {
    static final ArmorModel ARMOR = new ArmorModel(0.04, 0.8);
    private static final EnemyProfiles PROFILES = EnemyProfilesParser
            .parse(ConfigNode.root("enemies.json", TacticsTexts.read("enemies.json")));

    private Assessments() {
    }

    static EnemyProfiles profiles() {
        return PROFILES;
    }

    static Assessment of(Situation situation) {
        EnemyMetrics metrics = EnemyMetrics.of(situation, PROFILES, ARMOR);
        return new Assessment(metrics, 0, 0, situation.orders().mode(), true, 0, Assessment.UNBOUNDED,
                Assessment.UNBOUNDED, 10, TacticalState.CALM, 0, 1000);
    }

    static Assessment full(Situation situation) {
        EnemyMetrics metrics = EnemyMetrics.of(situation, PROFILES, ARMOR);
        double margin = WinMargin.of(situation.self(), metrics, Scales.defaults());
        return new Assessment(metrics, margin, 1, situation.orders().mode(), true, 0, Assessment.UNBOUNDED,
                Assessment.UNBOUNDED, 10, TacticalState.CALM, 0, 1000);
    }

    static FactMap facts(Situation situation) {
        return FactMap.from(situation, of(situation));
    }
}
