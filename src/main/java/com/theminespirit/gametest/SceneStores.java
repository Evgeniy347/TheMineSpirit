package com.theminespirit.gametest;

import com.theminespirit.neoforge.board.RouteStore;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import net.minecraft.server.level.ServerLevel;

final class SceneStores {
    private SceneStores() {}

    static void clearSettlement(ServerLevel level) {
        SettlementPlotStore.get(level).clear();
        RouteStore.get(level).clear();
    }
}
