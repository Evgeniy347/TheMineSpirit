package com.theminespirit.core.tactics;

final class Assessments {
    static final ArmorModel ARMOR = new ArmorModel(0.04, 0.8);
    private static final EnemyProfiles PROFILES = EnemyProfilesParser
            .parse(ConfigNode.root("enemies.json", TacticsTexts.read("enemies.json")));

    private Assessments() {
    }

    private static boolean initiates(Situation situation) {
        return TacticsConfig.loadDefault().modes().of(situation.orders().mode()).initiates();
    }

    private static double ratio(Situation situation, EnemyMetrics metrics) {
        return metrics.speedRatio(situation.self().speed(), Scales.defaults().speedRatioCap());
    }

    static EnemyProfiles profiles() {
        return PROFILES;
    }

    static Assessment of(Situation situation) {
        EnemyMetrics metrics = EnemyMetrics.of(situation, PROFILES, ARMOR);
        return new Assessment(metrics, 0, 0, situation.orders().mode(), initiates(situation), 0, Assessment.UNBOUNDED,
                Assessment.UNBOUNDED, ratio(situation, metrics), TacticalState.CALM, 0, 1000);
    }

    static Assessment full(Situation situation) {
        EnemyMetrics metrics = EnemyMetrics.of(situation, PROFILES, ARMOR);
        double margin = WinMargin.of(situation.self(), metrics, Scales.defaults());
        return new Assessment(metrics, margin, 1, situation.orders().mode(), initiates(situation), 0, Assessment.UNBOUNDED,
                Assessment.UNBOUNDED, ratio(situation, metrics), TacticalState.CALM, 0, 1000);
    }

    static Assessment custom(Situation situation, int routes, double leashLeft, double engageLeft,
            TacticalState state) {
        Assessment base = full(situation);
        return new Assessment(base.enemies(), base.winMargin(), routes, base.mode(), base.initiates(),
                base.zoneDistance(), leashLeft, engageLeft, base.speedRatio(), state, 0, 1000);
    }

    static FactMap factsInMode(Situation situation, CombatMode mode) {
        Assessment base = full(situation);
        return FactMap.from(situation, new Assessment(base.enemies(), base.winMargin(), base.escapeRoutes(), mode,
                base.initiates(), base.zoneDistance(), base.leashLeft(), base.engageLeft(), base.speedRatio(),
                base.state(), base.ticksInState(), base.ticksSinceEnemySeen()));
    }

    static FactMap facts(Situation situation) {
        return FactMap.from(situation, of(situation));
    }
}
