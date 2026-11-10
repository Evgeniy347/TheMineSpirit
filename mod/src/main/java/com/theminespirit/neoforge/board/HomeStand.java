package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.StandSearch;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.SettlementPlot;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.LevelSafetyView;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import net.minecraft.server.level.ServerLevel;

final class HomeStand {
    private HomeStand() {}

    static BlockCoord of(ServerLevel level, StageSite stage, List<SpiritEntity> spirits) {
        List<BlockCoord> homes = SettlementHome.homes(spirits);
        if (!homes.isEmpty()) {
            return SettlementPlot.center(homes);
        }
        BlockCoord anchor = stage.anchor();
        LevelSafetyView view = new LevelSafetyView(level);
        return StandSearch.nearest(anchor, cell -> MiningSafety.canStand(cell, view));
    }
}
