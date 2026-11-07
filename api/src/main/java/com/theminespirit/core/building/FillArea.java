package com.theminespirit.core.building;

public interface FillArea {
    int x1();

    int y1();

    int z1();

    int x2();

    int y2();

    int z2();

    default int volume() {
        return (x2() - x1() + 1) * (y2() - y1() + 1) * (z2() - z1() + 1);
    }
}
