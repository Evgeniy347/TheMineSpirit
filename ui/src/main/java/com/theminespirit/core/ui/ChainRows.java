package com.theminespirit.core.ui;

public record ChainRows(int shown, int hidden) {
    public static final int MAX_ITEMS = 3;

    public ChainRows {
        if (shown < 0 || hidden < 0) {
            throw new IllegalArgumentException(
                    "Строки цепочки " + shown + " и скрытые " + hidden + ": отрицательных строк не бывает");
        }
    }

    public static ChainRows of(int total, int availableRows) {
        if (total < 0 || availableRows < 0) {
            throw new IllegalArgumentException(
                    "Цепочка из " + total + " строк при " + availableRows + ": оба числа неотрицательны");
        }
        int shown = Math.min(total, Math.min(MAX_ITEMS, availableRows));
        return new ChainRows(shown, total - shown);
    }

    public boolean more() {
        return hidden > 0;
    }
}
