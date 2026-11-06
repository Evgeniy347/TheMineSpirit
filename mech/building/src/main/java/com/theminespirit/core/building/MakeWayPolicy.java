package com.theminespirit.core.building;

public final class MakeWayPolicy {
    private MakeWayPolicy() {}

    public static boolean mustMove(boolean running, String script) {
        return !(running && "goto".equals(script));
    }
}
