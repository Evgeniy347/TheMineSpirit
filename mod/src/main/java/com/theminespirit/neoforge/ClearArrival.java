package com.theminespirit.neoforge;

final class ClearArrival {
    private ClearArrival() {}

    static boolean reached(ClearAnchor.Stand stand, int x, int y, int z) {
        return y == stand.y() && Math.abs(x - stand.x()) <= 1 && Math.abs(z - stand.z()) <= 1;
    }
}
