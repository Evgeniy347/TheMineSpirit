package com.theminespirit.core.ui;

import java.util.Locale;

public record BoardStage(int number) {
    public static final String KEY_PREFIX = "theminespirit.board.stage.";
    public static final BoardStage FIRST = new BoardStage(1);

    public BoardStage {
        if (number < 1) {
            throw new IllegalArgumentException("Этап " + number + ": этапы поселения считаются с первого");
        }
    }

    public String nameKey() {
        return KEY_PREFIX + Integer.toString(number).toLowerCase(Locale.ROOT);
    }
}
