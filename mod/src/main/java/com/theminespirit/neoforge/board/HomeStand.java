package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.StandSearch;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.LevelSafetyView;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;

final class HomeStand {
    private HomeStand() {}

    static BlockCoord of(ServerLevel level, StageSite stage, List<SpiritEntity> spirits) {
        LevelSafetyView view = new LevelSafetyView(level);
        return of(SettlementHome.homes(spirits), stage.anchor(), cell -> MiningSafety.canStand(cell, view));
    }

    static BlockCoord at(ServerLevel level, BlockCoord home) {
        LevelSafetyView view = new LevelSafetyView(level);
        return at(home, cell -> MiningSafety.canStand(cell, view));
    }

    static BlockCoord at(BlockCoord home, Predicate<BlockCoord> canStand) {
        return of(List.of(home), home, canStand);
    }

    static BlockCoord of(List<BlockCoord> homes, BlockCoord anchor, Predicate<BlockCoord> canStand) {
        BlockCoord center = SettlementHome.center(homes).orElse(anchor);
        return StandSearch.nearest(center, canStand);
    }
}
