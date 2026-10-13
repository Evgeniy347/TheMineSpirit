package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildBox;

public final class DigAreaSession extends ClearAreaSession {
    public DigAreaSession(SpiritEntity spirit, int x, int y, int z, int size) {
        super(spirit, BuildBox.cube(x, y, z, size));
    }
}
