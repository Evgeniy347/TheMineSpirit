package com.theminespirit.core.board;

public final class BoardBackoff {
    private BoardBackoff() {}

    public static long pauseTicks(int consecutiveFailures) {
        return pauseTicks(
                consecutiveFailures,
                BoardTuning.caseFailPauseTicks(),
                BoardTuning.caseFailMultiplier(),
                BoardTuning.caseFailPauseCapTicks());
    }

    public static long pauseTicks(int consecutiveFailures, long initialTicks, long multiplier, long capTicks) {
        if (consecutiveFailures < 1) {
            throw new IllegalArgumentException(
                    "Провалов " + consecutiveFailures + ": пауза считается " + "после хотя бы одного провала");
        }
        if (initialTicks < 1 || multiplier < 1 || capTicks < 1) {
            throw new IllegalArgumentException("Пауза " + initialTicks + ", множитель " + multiplier + ", потолок "
                    + capTicks + ": все числа обязаны быть не меньше 1");
        }
        long pause = Math.min(initialTicks, capTicks);
        for (int step = 1; step < consecutiveFailures && pause < capTicks; step++) {
            if (pause > capTicks / multiplier) {
                return capTicks;
            }
            pause *= multiplier;
        }
        return Math.min(pause, capTicks);
    }
}
