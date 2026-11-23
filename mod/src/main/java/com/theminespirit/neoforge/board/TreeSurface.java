package com.theminespirit.neoforge.board;

import java.util.function.IntPredicate;

final class TreeSurface {
    private TreeSurface() {}

    static int skipWood(int top, int depth, IntPredicate wood) {
        int y = top;
        for (int step = 0; step < depth && wood.test(y); step++) {
            y--;
        }
        return y + 1;
    }
}
