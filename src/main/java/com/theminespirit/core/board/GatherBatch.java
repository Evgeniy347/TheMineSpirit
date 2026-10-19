package com.theminespirit.core.board;

/**
 * Партия добычи: недостача округляется вверх до целых крафтов материала, чтобы acquire
 * не дробил рецепт. Выход рецепта — данные игры, а не число в коде.
 */
public final class GatherBatch {
    private GatherBatch() {}

    public static int round(int shortage, int craftOutput) {
        if (shortage <= 0) {
            return 0;
        }
        if (craftOutput <= 1) {
            return shortage;
        }
        long batches = (shortage + (long) craftOutput - 1) / craftOutput;
        return (int) Math.min(batches * craftOutput, Integer.MAX_VALUE);
    }
}
