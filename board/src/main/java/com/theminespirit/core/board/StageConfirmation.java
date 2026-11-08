package com.theminespirit.core.board;

import java.util.Objects;

public final class StageConfirmation {
    public static final int DEFAULT_CONFIRMATIONS = 3;

    private final int confirmations;
    private int stage;
    private int candidate;
    private int streak;

    public StageConfirmation(int stage) {
        this(stage, DEFAULT_CONFIRMATIONS);
    }

    public StageConfirmation(int stage, int confirmations) {
        if (stage < 1) {
            throw new IllegalArgumentException(
                    "Этап " + stage + " для подтверждения: этапы поселения начинаются с первого");
        }
        if (confirmations < 1) {
            throw new IllegalArgumentException(
                    "Подтверждений " + confirmations + " для смены этапа: смена принимается хотя бы по одному аудиту");
        }
        this.stage = stage;
        this.candidate = stage;
        this.confirmations = confirmations;
    }

    public static StageConfirmation of(int stage) {
        return new StageConfirmation(stage);
    }

    public int stage() {
        return stage;
    }

    public int candidate() {
        return candidate;
    }

    public int streak() {
        return streak;
    }

    public StageGate gate(StageWorld world) {
        Objects.requireNonNull(world, "Мир этапа не может быть null: по нему видно, что стоит на самом деле");
        int stable = observe(SettlementStage.of(world));
        return SettlementStage.gate(world, stable);
    }

    public int observe(int observed) {
        if (observed < 1) {
            throw new IllegalArgumentException(
                    "Наблюдённый этап " + observed + ": этапы поселения начинаются с первого");
        }
        if (observed == stage) {
            candidate = stage;
            streak = 0;
            return stage;
        }
        if (observed != candidate) {
            candidate = observed;
            streak = 0;
        }
        streak++;
        if (streak >= confirmations) {
            stage = observed;
            streak = 0;
        }
        return stage;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StageConfirmation that)) {
            return false;
        }
        return stage == that.stage
                && candidate == that.candidate
                && streak == that.streak
                && confirmations == that.confirmations;
    }

    @Override
    public int hashCode() {
        return Objects.hash(stage, candidate, streak, confirmations);
    }

    @Override
    public String toString() {
        return "StageConfirmation[stage=" + stage + ", candidate=" + candidate + ", streak=" + streak
                + ", confirmations=" + confirmations + "]";
    }
}
