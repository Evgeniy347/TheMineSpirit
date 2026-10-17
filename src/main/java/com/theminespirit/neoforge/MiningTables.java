package com.theminespirit.neoforge;

import com.theminespirit.core.mining.ToolReserve;
import com.theminespirit.core.reload.DataTables;

public final class MiningTables {
    private MiningTables() {
    }

    public static ToolReserve toolReserve() {
        return DataTables.INSTANCE.toolReplace().current();
    }
}
