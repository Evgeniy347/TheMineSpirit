package com.theminespirit.core.tactics.adapter;

import com.theminespirit.core.tactics.EnemyFact;
import com.theminespirit.core.tactics.RetreatMethod;

public sealed interface ExecutionResult {
    record Moving(RetreatMethod method) implements ExecutionResult {
        public Moving {
            requireMethod(method);
        }
    }

    record Attacking(EnemyFact enemy) implements ExecutionResult {
        public Attacking {
            requireEnemy(enemy);
        }
    }

    record Shooting(EnemyFact enemy) implements ExecutionResult {
        public Shooting {
            requireEnemy(enemy);
        }
    }

    record Idle() implements ExecutionResult {
        static final Idle INSTANCE = new Idle();
    }

    static ExecutionResult idle() {
        return Idle.INSTANCE;
    }

    private static void requireMethod(RetreatMethod method) {
        if (method == null || method == RetreatMethod.NONE) {
            throw new IllegalArgumentException("Способ отхода = " + method + ": движение требует реального "
                    + "способа отхода; RetreatMethod.NONE означает простой (Idle)");
        }
    }

    private static void requireEnemy(EnemyFact enemy) {
        if (enemy == null) {
            throw new IllegalArgumentException(
                    "Цель = null: атака и стрельба требуют врага; если цели нет, решение — " + "простой (Idle)");
        }
    }
}
