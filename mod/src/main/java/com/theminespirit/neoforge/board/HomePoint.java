package com.theminespirit.neoforge.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;

public final class HomePoint {
    private HomePoint() {}

    public static Optional<BlockCoord> of(SpiritEntity spirit) {
        Optional<BlockCoord> own = spirit.home();
        if (own.isPresent() || !(spirit.level() instanceof ServerLevel level)) {
            return own;
        }
        Optional<StageSite> site = SettlementPlotStore.get(level).site();
        return site.map(stage -> HomeStand.of(level, stage, List.of(spirit)));
    }
}
