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
        if (own.isPresent()) {
            if (spirit.level() instanceof ServerLevel level) {
                return Optional.of(HomeStand.at(level, own.orElseThrow(), List.of(spirit)));
            }
            return own;
        }
        if (!(spirit.level() instanceof ServerLevel level)) {
            return own;
        }
        Optional<StageSite> site = SettlementPlotStore.liveSite(level);
        return site.map(stage -> HomeStand.of(level, stage, List.of(spirit)));
    }

    public static Optional<BlockCoord> of(ServerLevel level, List<SpiritEntity> spirits) {
        Optional<BlockCoord> home = SettlementHome.center(SettlementHome.homes(spirits));
        if (home.isPresent()) {
            return Optional.of(HomeStand.at(level, home.orElseThrow(), spirits));
        }
        return SettlementPlotStore.liveSite(level).map(stage -> HomeStand.of(level, stage, spirits));
    }
}
