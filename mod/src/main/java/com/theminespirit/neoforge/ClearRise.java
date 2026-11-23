package com.theminespirit.neoforge;

import com.theminespirit.neoforge.board.SettlementPlotStore;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

final class ClearRise {
    private ClearRise() {}

    static boolean allowed(Level level, int feetY) {
        return above(feetY, settlementLevel(level));
    }

    static boolean above(int feetY, int settlementLevel) {
        return feetY > settlementLevel;
    }

    private static int settlementLevel(Level level) {
        if (!(level instanceof ServerLevel server)) {
            return Integer.MIN_VALUE;
        }
        SettlementPlotStore store = SettlementPlotStore.get(server);
        return store.plot()
                .map(plot -> plot.anchor().y())
                .orElseGet(() -> store.site().map(site -> site.anchor().y()).orElse(Integer.MIN_VALUE));
    }
}
