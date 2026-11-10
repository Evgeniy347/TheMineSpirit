package com.theminespirit.core.board.lease;

import java.util.Objects;

public final class RollbackReach {
    public enum Action {
        CLIMB,
        STEP_DOWN,
        DIG
    }

    public record Step(Action action, int rise) {
        public Step {
            Objects.requireNonNull(action, "Действие подхода не может быть null: дух не знает шага");
            if (action == Action.CLIMB ? rise < 1 : rise != 0) {
                throw new IllegalArgumentException("Шаг " + action + " с подъёмом " + rise
                        + ": подъём положителен, остальные шаги высоту не несут");
            }
        }
    }

    private RollbackReach() {}

    public static Step next(int rise, boolean onOwnScaffold) {
        if (rise > 0) {
            return new Step(Action.CLIMB, rise);
        }
        if (rise == 0) {
            return new Step(Action.DIG, 0);
        }
        return new Step(onOwnScaffold ? Action.STEP_DOWN : Action.DIG, 0);
    }
}
