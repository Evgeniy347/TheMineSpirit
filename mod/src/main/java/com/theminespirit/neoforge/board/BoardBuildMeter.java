package com.theminespirit.neoforge.board;

public final class BoardBuildMeter {
    private long rebuildStarted;
    private long pruneStarted;
    private long rebuildMillis;
    private long pruneMillis;

    void rebuildStart() {
        rebuildStarted = System.nanoTime();
    }

    void pruneStart() {
        pruneStarted = System.nanoTime();
    }

    void pruneEnd() {
        pruneMillis = (System.nanoTime() - pruneStarted) / 1_000_000;
    }

    void rebuildEnd() {
        rebuildMillis = (System.nanoTime() - rebuildStarted) / 1_000_000;
    }

    public long rebuildMillis() {
        return rebuildMillis;
    }

    public long pruneMillis() {
        return pruneMillis;
    }
}
