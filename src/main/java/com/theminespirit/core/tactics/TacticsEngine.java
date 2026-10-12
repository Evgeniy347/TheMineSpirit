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
        FactorScales scales = config.weights().scales();
        EnemyMetrics metrics = EnemyMetrics.of(situation, config.enemies(), scales.armor());
        long lastSeen = metrics.visibleCount() > 0 ? nowTick : memory.lastSeenTick();
        CombatMode ordered = situation.orders().mode();
        CombatMode current = memory.orderedMode() == ordered ? memory.mode() : ordered;
        Frame frame = new Frame(situation, memory, nowTick, metrics, lastSeen);
        ModeStep modeStep = modes.next(current, FactMap.from(situation, frame.assessment(current).assessment()));
        Assessed assessed = frame.assessment(modeStep.mode());
        RetreatOptions options = retreat.evaluate(FactMap.from(situation, assessed.assessment()));
        Assessment assessment = assessed.assessment().withEscapeRoutes(options.routes())
                .withEscapeQuality(options.bestUtility());
        FactMap facts = FactMap.from(situation, assessment);
        return conclude(frame, modeStep, assessed.geometry(), options, assessment, facts);
    }

    private TacticalOutcome conclude(Frame frame, ModeStep modeStep, ModeGeometry geometry, RetreatOptions options,
            Assessment assessment, FactMap facts) {
        Situation situation = frame.situation();
        TacticsMemory memory = frame.memory();
        Scores scores = scorer.score(situation, assessment, config.weights());
        Decision decision = decider.decide(facts, scores, assessment.escapeRoutes(), memory, frame.nowTick());
        ActionChoice action = actions.choose(decision.option(), facts);
        boolean retreating = decision.option() == TacticalOption.RETREAT;
        RetreatChoice choice = retreating ? options.choice() : RetreatChoice.NONE;
        boolean callHelp = retreating && retreat.callHelp(facts);
        StateStep state = states.next(memory.state(), decision.option(), facts);
        ModeParams params = config.modes().of(modeStep.mode());
        Optional<Point> returnTo = state.state() == TacticalState.CALM
                ? Leash.returnTarget(params, Optional.of(geometry.anchor()), situation.self().pos(),
                        config.modes().returnTolerance())
                : Optional.empty();
        TacticsMemory next = nextMemory(frame, decision, state, modeStep.mode());
        return new TacticalOutcome(decision.option(), action.action(), choice.method(), callHelp, state.state(),
                modeStep.mode(), returnTo, TraceBuilder.build(decision, scores, action, choice, state, modeStep),
                next);
    }

    private TacticsMemory nextMemory(Frame frame, Decision decision, StateStep state, CombatMode mode) {
        Situation situation = frame.situation();
        TacticsMemory memory = frame.memory();
        TacticsMemory remembered = decider.hysteresis().remember(memory, decision, frame.nowTick(),
                situation.self().hpFraction());
        boolean stateChanged = state.state() != memory.state();
        Optional<Point> anchor = anchorAfter(situation, memory, state.state());
        return new TacticsMemory(remembered.previous(), remembered.previousSource(), remembered.decidedAt(),
                state.state(), stateChanged ? frame.nowTick() : memory.stateSince(), mode, situation.orders().mode(),
                anchor, frame.lastSeen(), remembered.hpWindowRef(), remembered.hpWindowStart());
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

    private record Assessed(Assessment assessment, ModeGeometry geometry) {
    }

    private final class Frame {
        private final Situation situation;
        private final TacticsMemory memory;
        private final long nowTick;
        private final EnemyMetrics metrics;
        private final long lastSeen;

        Frame(Situation situation, TacticsMemory memory, long nowTick, EnemyMetrics metrics, long lastSeen) {
            this.situation = situation;
            this.memory = memory;
            this.nowTick = nowTick;
            this.metrics = metrics;
            this.lastSeen = lastSeen;
        }

        Situation situation() {
            return situation;
        }

        TacticsMemory memory() {
            return memory;
        }

        long nowTick() {
            return nowTick;
        }

        long lastSeen() {
            return lastSeen;
        }

        Assessed assessment(CombatMode mode) {
            FactorScales scales = config.weights().scales();
            ModeParams params = config.modes().of(mode);
            ModeGeometry geometry = ModeGeometry.of(situation, params, memory.anchor());
            Assessment assessment = new Assessment(metrics, WinMargin.of(situation.self(), metrics, scales),
                    0, 0, mode, params.initiates(), geometry.zoneDistance(), geometry.leashLeft(),
                    geometry.engageLeft(), metrics.speedRatio(situation.self().speed(), scales.speedRatioCap()),
                    memory.state(), ticksBetween(memory.stateSince(), nowTick), ticksBetween(lastSeen, nowTick));
            return new Assessed(assessment, geometry);
        }
    }
}
