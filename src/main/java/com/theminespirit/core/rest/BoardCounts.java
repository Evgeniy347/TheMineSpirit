package com.theminespirit.core.rest;

public record BoardCounts(long done, long reopened) {
    public BoardCounts {
        if (done < 0 || reopened < 0) {
            throw new IllegalArgumentException(
                    "Счётчики доски " + done + "/" + reopened + " отрицательны: итоги идут вперёд, а не назад");
        }
    }
}
