package com.theminespirit.core.town;

public final class TorchRule {
    private TorchRule() {}

    public static boolean adHoc(boolean plannedArea, int level, int cellY, boolean fenceBelow) {
        if (cellY == level + 2) {
            return fenceBelow;
        }
        if (plannedArea) {
            return false;
        }
        return cellY == level + 1;
    }
}
