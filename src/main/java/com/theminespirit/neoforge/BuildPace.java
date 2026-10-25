package com.theminespirit.neoforge;

import com.theminespirit.core.reload.DataTables;

public final class BuildPace {
    private BuildPace() {}

    public static int intervalTicks(SpiritEntity spirit) {
        return DataTables.INSTANCE.building().current().intervalTicks(spirit.isDemo());
    }
}
