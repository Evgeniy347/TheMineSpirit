package com.theminespirit.neoforge.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;

public final class HomePoint {
    private static final Map<ServerLevel, HomeCache> CACHE = new WeakHashMap<>();

    private HomePoint() {}

    public static Optional<BlockCoord> of(SpiritEntity spirit) {
        Optional<BlockCoord> own = spirit.home();
        if (own.isEmpty()) {
            return spirit.level() instanceof ServerLevel level ? of(level, List.of(spirit)) : own;
        }
        if (!(spirit.level() instanceof ServerLevel level)) {
            return own;
        }
        BlockCoord home = own.orElseThrow();
        return cache(level)
                .resolve(
                        level.getGameTime(),
                        new HomeCache.Key(home, spirit.getUUID()),
                        () -> Optional.of(HomeStand.at(level, home, List.of(spirit), Optional.empty())));
    }

    public static Optional<BlockCoord> of(ServerLevel level, List<SpiritEntity> spirits) {
        SettlementPlotStore store = SettlementPlotStore.get(level);
        Optional<BlockCoord> center = SettlementHome.center(SettlementHome.homes(spirits));
        Optional<BlockCoord> stand;
        if (center.isPresent()) {
            BlockCoord point = center.orElseThrow();
            stand = cache(level)
                    .resolve(
                            level.getGameTime(),
                            new HomeCache.Key(point, null),
                            () -> Optional.of(HomeStand.at(level, point, spirits, store.home())));
        } else {
            Optional<StageSite> site = SettlementPlotStore.liveSite(level);
            stand = site.flatMap(stage -> cache(level)
                    .resolve(
                            level.getGameTime(),
                            new HomeCache.Key(stage.anchor(), null),
                            () -> Optional.of(HomeStand.of(level, stage, spirits, store.home()))));
        }
        stand.ifPresent(store::rememberHome);
        return stand;
    }

    private static HomeCache cache(ServerLevel level) {
        return CACHE.computeIfAbsent(level, ignored -> new HomeCache());
    }
}
