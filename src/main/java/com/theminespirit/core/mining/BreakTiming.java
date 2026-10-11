package com.theminespirit.core.mining;

public final class BreakTiming {
    public static final double CORRECT_TOOL_FACTOR = 30.0;
    public static final double WRONG_TOOL_FACTOR = 100.0;
    private static final double ROUNDING_NOISE = 1e-9;

    private BreakTiming() {
    }

    public static int ticks(double hardness, double toolSpeed, boolean correctForDrops) {
        if (!(hardness >= 0)) {
            throw new IllegalArgumentException("Твёрдость " + hardness + " отрицательна или не число: неразрушимый блок "
                    + "ломать нельзя, вызывающий обязан отказаться до расчёта срока");
        }
        if (!(toolSpeed > 0)) {
            throw new IllegalArgumentException("Скорость инструмента " + toolSpeed + " не положительна: срок ломки "
                    + "был бы бесконечным, проверьте выбор инструмента");
        }
        double factor = correctForDrops ? CORRECT_TOOL_FACTOR : WRONG_TOOL_FACTOR;
        double ticks = Math.ceil(hardness * factor / toolSpeed - ROUNDING_NOISE);
        if (ticks >= Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return Math.max(1, (int) ticks);
    }
}
