package com.theminespirit.core.tactics.adapter;

import com.theminespirit.core.tactics.RetreatMethod;
import com.theminespirit.core.tactics.TacticalOption;
import com.theminespirit.core.tactics.TacticalOutcome;

public record RetreatTracker(RetreatMethod method, long sinceTick) {
    public static final RetreatTracker IDLE = new RetreatTracker(RetreatMethod.NONE, 0);

    public RetreatTracker {
        if (method == null) {
            throw new IllegalArgumentException(
                    "RetreatTracker.method = null: неясно, какой отход исполняется; для простоя — RetreatMethod.NONE");
        }
        if (sinceTick < 0) {
            throw new IllegalArgumentException("RetreatTracker.sinceTick = " + sinceTick
                    + ": тик не может быть отрицательным; проверьте счётчик тиков адаптера");
        }
        if (method == RetreatMethod.NONE && sinceTick != 0) {
            throw new IllegalArgumentException("RetreatTracker.sinceTick = " + sinceTick
                    + " при method = NONE: простой не несёт прогресса отхода; для простоя — RetreatTracker.IDLE");
        }
    }

    public boolean retreating() {
        return method != RetreatMethod.NONE;
    }

    public long progressTicks(long nowTick) {
        if (!retreating()) {
            return 0;
        }
        if (nowTick < sinceTick) {
            throw new IllegalArgumentException("progressTicks: nowTick = " + nowTick + " меньше sinceTick = "
                    + sinceTick + ": время не откатывается, проверьте порядок вызовов");
        }
        return nowTick - sinceTick;
    }

    public RetreatTracker advance(TacticalOutcome outcome, long nowTick, int holdTicks) {
        if (outcome == null) {
            throw new IllegalArgumentException(
                    "RetreatTracker.advance: outcome = null; проверьте вызов обновления отхода");
        }
        if (holdTicks < 1) {
            throw new IllegalArgumentException("RetreatTracker.advance: holdTicks = " + holdTicks
                    + ": удержание обязано быть не меньше тика; возьмите retreatHoldTicks из настроек");
        }
        if (nowTick < sinceTick) {
            throw new IllegalArgumentException("RetreatTracker.advance: nowTick = " + nowTick + " меньше sinceTick = "
                    + sinceTick + ": время не откатывается, проверьте порядок вызовов");
        }
        boolean wants = outcome.decision() == TacticalOption.RETREAT && outcome.retreatMethod() != RetreatMethod.NONE;
        if (!retreating()) {
            return wants ? new RetreatTracker(outcome.retreatMethod(), nowTick) : IDLE;
        }
        if (wants && outcome.retreatMethod() == method) {
            return this;
        }
        return nowTick - sinceTick < holdTicks
                ? this
                : (wants ? new RetreatTracker(outcome.retreatMethod(), nowTick) : IDLE);
    }

    public RetreatTracker advance(TacticalOutcome outcome, long nowTick, TacticsAdapterSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException(
                    "RetreatTracker.advance: settings = null; возьмите TacticsAdapterSettings.loadDefault()");
        }
        return advance(outcome, nowTick, settings.retreatHoldTicks());
    }
}
