package com.theminespirit.neoforge.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.SettlementPlot;
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
        Optional<StageSite> site = SettlementPlotStore.liveSite(level);
        return site.map(stage -> HomeStand.of(level, stage, List.of(spirit)));
    }

    public static BlockCoord of(ServerLevel level, List<SpiritEntity> spirits) {
        List<BlockCoord> homes = SettlementHome.homes(spirits);
        if (!homes.isEmpty()) {
            return SettlementPlot.center(homes);
        }
        return SettlementPlotStore.liveSite(level)
                .map(stage -> HomeStand.of(level, stage, spirits))
                .orElseGet(() -> SettlementHome.of(spirits));
    }
}
