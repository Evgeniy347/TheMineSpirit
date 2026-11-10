package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.StandSearch;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.LevelSafetyView;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import net.minecraft.server.level.ServerLevel;

final class HomeStand {
    private HomeStand() {}

    static BlockCoord of(ServerLevel level, StageSite stage, List<SpiritEntity> spirits) {
        LevelSafetyView view = new LevelSafetyView(level);
        return SettlementHome.center(SettlementHome.homes(spirits))
                .orElseGet(() -> StandSearch.nearest(stage.anchor(), cell -> MiningSafety.canStand(cell, view)));
    }
}
