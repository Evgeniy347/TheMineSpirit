package com.theminespirit.neoforge.board;

import java.util.function.IntPredicate;

public final class TreeSurface {
    private TreeSurface() {}

    static int skipWood(int top, int depth, IntPredicate wood) {
        int y = top;
        for (int step = 0; step < depth && wood.test(y); step++) {
            y--;
        }
        return y + 1;
    }

    public static boolean sky(int height, int y, boolean woodBelow) {
        return !woodBelow && height <= y;
    }
}
