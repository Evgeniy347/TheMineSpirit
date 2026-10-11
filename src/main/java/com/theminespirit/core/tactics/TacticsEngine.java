package com.theminespirit.core.tactics;

import java.util.Optional;

public final class TacticsEngine {
    private final TacticsConfig config;
    private final Scorer scorer = new Scorer();
    private final RuleCounter ruleCounter = new RuleCounter();
    private final Decider decider;
    private final ActionTable actions;
    private final RetreatSelector retreat;
    private final StateMachine states;
    private final ModeMachine modes;

    public TacticsEngine(TacticsConfig config) {
        this.config = config;
        Rules rules = config.rules();
        this.decider = new Decider(new HardRules(rules.hard(), ruleCounter), config.weights());
        this.actions = new ActionTable(rules.actions(), ruleCounter);
        this.retreat = new RetreatSelector(rules.retreat(), ruleCounter);
        this.states = new StateMachine(rules.states(), ruleCounter);
        this.modes = new ModeMachine(rules.modeTransitions(), ruleCounter);
    }

    public Scorer scorer() {
        return scorer;
    }

    public RuleCounter ruleCounter() {
        return ruleCounter;
    }

    public int totalRules() {
        Rules rules = config.rules();
        return rules.hard().size() + rules.actions().size() + rules.modeTransitions().size() + rules.states().size()
                + rules.retreat().entries().size() + 1;
    }

    public TacticalOutcome step(Situation situation, TacticsMemory memory, long nowTick) {
        if (nowTick < 0) {
            throw new IllegalArgumentException("nowTick = " + nowTick + ": номер тика не может быть отрицательным");
        }
        CombatMode mode = memory.orderedMode() == situation.orders().mode() ? memory.mode() : situation.orders().mode();
        ModeParams params = config.modes().of(mode);
        FactorScales scales = config.weights().scales();
        EnemyMetrics metrics = EnemyMetrics.of(situation, config.enemies(), scales.armor());
        long lastSeen = metrics.visibleCount() > 0 ? nowTick : memory.lastSeenTick();
        ModeGeometry geometry = ModeGeometry.of(situation, params, memory.anchor());
        Assessment base = new Assessment(metrics, WinMargin.of(situation.self(), metrics, scales), 0, mode,
                params.initiates(), geometry.zoneDistance(), geometry.leashLeft(), geometry.engageLeft(),
                metrics.speedRatio(situation.self().speed(), scales.speedRatioCap()), memory.state(),
                ticksBetween(memory.stateSince(), nowTick), ticksBetween(lastSeen, nowTick));
        RetreatOptions options = retreat.evaluate(FactMap.from(situation, base));
        Assessment assessment = base.withEscapeRoutes(options.routes());
        FactMap facts = FactMap.from(situation, assessment);
        return conclude(situation, memory, nowTick, new Inputs(mode, params, geometry, lastSeen, options, assessment,
                facts));
    }

    private TacticalOutcome conclude(Situation situation, TacticsMemory memory, long nowTick, Inputs in) {
        Scores scores = scorer.score(situation, in.assessment(), config.weights());
        Decision decision = decider.decide(in.facts(), scores, in.assessment().escapeRoutes(), memory, nowTick);
        ActionChoice action = actions.choose(decision.option(), in.facts());
        boolean retreating = decision.option() == TacticalOption.RETREAT;
        RetreatChoice choice = retreating ? in.options().choice() : RetreatChoice.NONE;
        boolean callHelp = retreating && retreat.callHelp(in.facts());
        StateStep state = states.next(memory.state(), decision.option(), in.facts());
        ModeStep modeStep = modes.next(in.mode(), in.facts());
        Optional<Point> returnTo = state.state() == TacticalState.CALM
                ? Leash.returnTarget(in.params(), Optional.of(in.geometry().anchor()), situation.self().pos(),
                        config.modes().returnTolerance())
                : Optional.empty();
        TacticsMemory next = nextMemory(situation, memory, nowTick, in, decision, state, modeStep);
        return new TacticalOutcome(decision.option(), action.action(), choice.method(), callHelp, state.state(),
                modeStep.mode(), returnTo, TraceBuilder.build(decision, scores, action, choice, state, modeStep),
                next);
    }

    private TacticsMemory nextMemory(Situation situation, TacticsMemory memory, long nowTick, Inputs in,
            Decision decision, StateStep state, ModeStep modeStep) {
        TacticsMemory remembered = decider.hysteresis().remember(memory, decision.option(), nowTick,
                situation.self().hpFraction());
        boolean stateChanged = state.state() != memory.state();
        Optional<Point> anchor = anchorAfter(situation, memory, state.state());
        return new TacticsMemory(remembered.previous(), remembered.decidedAt(), state.state(),
                stateChanged ? nowTick : memory.stateSince(), modeStep.mode(), situation.orders().mode(), anchor,
                in.lastSeen(), remembered.hpWindowRef(), remembered.hpWindowStart());
    }

    private static Optional<Point> anchorAfter(Situation situation, TacticsMemory memory, TacticalState next) {
        if (next == TacticalState.CALM) {
            return Optional.empty();
        }
        return memory.state() == TacticalState.CALM ? Optional.of(situation.self().pos()) : memory.anchor();
    }

    private static long ticksBetween(long from, long to) {
        return Math.min(Situation.MAX_TICKS, Math.max(0, to - from));
    }

    private record Inputs(CombatMode mode, ModeParams params, ModeGeometry geometry, long lastSeen,
            RetreatOptions options, Assessment assessment, FactMap facts) {
    }
}
