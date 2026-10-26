package com.theminespirit.core.ui;

import java.util.Objects;

public record BoardTimeView(long startedAt, long finishedAt, long minutes, boolean running) {
    public static BoardTimeView of(BoardTile tile) {
        Objects.requireNonNull(tile, "Плитка не может быть null: время берут с неё");
        long minutes = tile.durationTicks() > 0 ? BoardClock.spentMinutes(tile.durationTicks()) : 0L;
        return new BoardTimeView(tile.startedAt(), tile.finishedAt(), minutes, tile.finishedAt() == 0);
    }

    public boolean showsMoment() {
        return startedAt > 0;
    }

    public boolean showsFinish() {
        return finishedAt > 0;
    }

    public boolean showsSpent() {
        return minutes > 0;
    }
}
