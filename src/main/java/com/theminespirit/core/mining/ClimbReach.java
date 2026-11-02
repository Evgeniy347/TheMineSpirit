package com.theminespirit.core.mining;

public final class ClimbReach {
    private ClimbReach() {}

    public static int rise(int spiritY, int boxMinY) {
        return Math.max(0, boxMinY - spiritY);
    }

    public static boolean needed(int rise) {
        if (rise < 0) {
            throw new IllegalArgumentException("Подъём " + rise + " отрицателен: столб строят только вверх");
        }
        return rise > MiningTuning.MAX_STAND_RISE;
    }

    public static int height(int spiritY, int boxMinY, int available) {
        int rise = rise(spiritY, boxMinY);
        return Math.min(rise, Math.max(0, available));
    }
}
