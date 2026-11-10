package com.theminespirit.core.board.route;

public interface QuarryProbe {

    int NO_WATER = Integer.MIN_VALUE;

    boolean loaded(int x, int z);

    int top(int x, int z);

    String block(int x, int y, int z);

    int waterLevel();

    int waterAround(int x, int y, int z);

    boolean forbidden(int x, int y, int z);
}
