package com.theminespirit.core.reload;

import com.theminespirit.core.base.RespawnRules;
import com.theminespirit.core.board.ConfigStamp;
import com.theminespirit.core.husbandry.FeedTable;
import com.theminespirit.core.observation.ObservationLimits;

final class DataTableBindings {
    private DataTableBindings() {}

    static void bind(DataTables tables) {
        ConfigStamp.useCurrent(() -> tables.board().status().hash());
        ObservationLimits.useSource(() -> tables.observation().current());
        FeedTable.useSource(() -> tables.feed().current());
        RespawnRules.useSource(() -> tables.respawn().current());
    }
}
