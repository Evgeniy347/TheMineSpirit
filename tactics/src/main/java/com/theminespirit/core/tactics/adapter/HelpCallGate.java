package com.theminespirit.core.tactics.adapter;

public final class HelpCallGate {
    public static final long NEVER = -1;

    private long lastCallTick = NEVER;

    public boolean ready(long nowTick, int intervalTicks) {
        if (nowTick < 0) {
            throw new IllegalArgumentException(
                    "HelpCallGate.ready: nowTick = " + nowTick + ": тик не может быть отрицательным");
        }
        if (intervalTicks < 1) {
            throw new IllegalArgumentException("HelpCallGate.ready: intervalTicks = " + intervalTicks
                    + ": предел частоты зова обязан быть не меньше тика");
        }
        return lastCallTick == NEVER || nowTick - lastCallTick >= intervalTicks;
    }

    public void called(long nowTick) {
        if (nowTick < 0) {
            throw new IllegalArgumentException(
                    "HelpCallGate.called: nowTick = " + nowTick + ": тик не может быть отрицательным");
        }
        lastCallTick = nowTick;
    }

    public long lastCallTick() {
        return lastCallTick;
    }
}
