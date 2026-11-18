package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.TownEntry;

public final class RepairTitle {
    public static final String PREFIX = "repair:";

    private RepairTitle() {}

    public static String of(String buildingId, BoardCase step) {
        return DataTables.INSTANCE
                .town()
                .current()
                .entry(buildingId)
                .map(TownEntry::name)
                .orElse(StageRepair.label(step));
    }
}
