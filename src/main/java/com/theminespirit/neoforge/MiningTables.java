package com.theminespirit.neoforge;

import com.theminespirit.core.mining.ToolReserve;
import com.theminespirit.core.reload.DataTables;
import java.util.List;

public final class MiningTables {
    private MiningTables() {}

    public static ToolReserve toolReserve() {
        return DataTables.INSTANCE.toolReplace().current();
    }

    public static List<String> repairTiers() {
        return DataTables.INSTANCE.board().current().tools().repairTiers();
    }
}
