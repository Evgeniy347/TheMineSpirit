package com.theminespirit.core.building;

/**
 * Стоянка в пещере под участком (INC-67, INC-68): ниже низа ящика больше чем на {@link #BELOW_BOX} и без неба над
 * ней. Стоянка в открытой низине (небо видно) — законная: подсыпка и срезка идут снизу.
 */
public final class CaveStand {
    public static final int BELOW_BOX = 2;

    private CaveStand() {}

    public static boolean cave(int standY, int boxMinY, boolean sky) {
        return !sky && standY < boxMinY - BELOW_BOX;
    }
}
