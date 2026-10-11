package com.theminespirit.core.tactics;

final class DecisionRun {
    private final TacticsConfig config;
    private final Decider decider;
    private final Scorer scorer = new Scorer();
    private final int escapeRoutes;
    private TacticsMemory memory;

    DecisionRun(TacticsConfig config, Situation first, int escapeRoutes) {
        this.config = config;
        this.decider = new Decider(new HardRules(config.rules().hard(), new RuleCounter()), config.weights());
        this.escapeRoutes = escapeRoutes;
        this.memory = TacticsMemory.initial(first.orders().mode(), 0, first.self().hpFraction());
    }

    Decision step(Situation situation, long tick) {
        Assessment assessment = Assessments.full(situation).withEscapeRoutes(escapeRoutes);
        Scores scores = scorer.score(situation, assessment, config.weights());
        Decision decision = decider.decide(FactMap.from(situation, assessment), scores, escapeRoutes, memory, tick);
        memory = decider.hysteresis().remember(memory, decision.option(), tick, situation.self().hpFraction());
        return decision;
    }

    TacticsMemory memory() {
        return memory;
    }
}
