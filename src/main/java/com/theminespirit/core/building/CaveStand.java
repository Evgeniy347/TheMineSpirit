package com.theminespirit.core.building;

public final class CaveStand {
    public static final int BELOW_BOX = 2;

    private CaveStand() {}

    public static boolean cave(int standY, int boxMinY, boolean sky) {
        return !sky && standY < boxMinY - BELOW_BOX;
    }
}
