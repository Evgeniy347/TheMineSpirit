// lib: Из Baritone (github.com/cabaletta/baritone, src/main/java/baritone/utils/BlockStateInterface.java), лицензия
// LGPL-3.0.
package com.theminespirit.core.navigation;

public interface NavWorld {
    boolean available(int x, int z);

    boolean passable(int x, int y, int z);

    boolean walkable(int x, int y, int z);

    boolean water(int x, int y, int z);

    boolean climbable(int x, int y, int z);

    double breakTicks(int x, int y, int z);

    int minY();

    int maxY();
}
