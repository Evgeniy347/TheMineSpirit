package com.theminespirit.gametest;

final class GameTestWorldSeed {
    static final long DEFAULT = 0L;
    static final String PROPERTY = "theminespirit.gametest.seed";

    private GameTestWorldSeed() {}

    static long resolve() {
        String raw = System.getProperty(PROPERTY, "").trim();
        return raw.isEmpty() ? DEFAULT : Long.parseLong(raw);
    }
}
