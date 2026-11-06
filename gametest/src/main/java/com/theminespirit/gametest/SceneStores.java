package com.theminespirit.gametest;

import com.theminespirit.core.board.BoardState;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.RouteStore;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import com.theminespirit.neoforge.doors.DoorWatchService;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;

final class SceneStores {
    private SceneStores() {}

    static void clearSettlement(ServerLevel level) {
        SettlementPlotStore.get(level).clear();
        RouteStore.get(level).clear();
        GameTestFixtures.forgetChests(level);
        BoardRegistry.get(level).board().restore(new BoardState(List.of(), Set.of()));
        DoorWatchService.forget(level);
    }
}
