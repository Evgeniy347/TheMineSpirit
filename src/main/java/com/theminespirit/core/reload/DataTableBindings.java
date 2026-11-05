package com.theminespirit.core.reload;

import com.theminespirit.core.base.RespawnRules;
import com.theminespirit.core.board.BoardConfig;
import com.theminespirit.core.board.ConfigStamp;
import com.theminespirit.core.board.DemandRules;
import com.theminespirit.core.board.LevelingRules;
import com.theminespirit.core.board.StageRules;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.husbandry.FeedTable;
import com.theminespirit.core.observation.ObservationLimits;
import com.theminespirit.core.repair.RepairData;
import com.theminespirit.core.town.TownRules;

final class DataTableBindings {
    private DataTableBindings() {}

    static void bind(DataTables tables) {
        ConfigStamp.useCurrent(() -> tables.board().status().hash());
        ObservationLimits.useSource(() -> tables.observation().current());
        FeedTable.useSource(() -> tables.feed().current());
        RespawnRules.useSource(() -> tables.respawn().current());
        BoardConfig.useSource(() -> tables.board().current());
        StageRules.useSource(() -> tables.stages().current());
        DemandRules.useSource(() -> tables.demand().current());
        LevelingRules.useSource(() -> tables.leveling().current());
        SupplyGraph.useSource(() -> tables.graph().current());
        PresetLibrary.useSource(() -> tables.presets().current());
        TownRules.useSource(() -> tables.town().current());
        RepairData.useSource(() -> tables.repair().current());
    }
}
