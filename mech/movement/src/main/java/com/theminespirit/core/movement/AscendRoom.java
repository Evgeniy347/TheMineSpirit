package com.theminespirit.core.movement;

public final class AscendRoom {
    private AscendRoom() {}

    public static boolean blocked(boolean cellSolid, boolean aboveSolid) {
        return cellSolid || aboveSolid;
    }
}
