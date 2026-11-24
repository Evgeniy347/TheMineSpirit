package com.theminespirit.neoforge.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.rest.ApiException;
import com.theminespirit.core.rest.ErrorCode;
import com.theminespirit.core.town.SettlementPlot;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;

final class SettlementCenter {
    private SettlementCenter() {}

    static BlockCoord of(ServerLevel level) {
        List<BlockCoord> homes = SettlementHome.homes(SpiritCensus.all(level));
        if (homes.isEmpty()) {
            throw new ApiException(
                    ErrorCode.INVALID_PLAN,
                    "У духов нет домов: центр поселения не задан, карту не от чего считать",
                    Map.of());
        }
        return SettlementPlot.center(homes);
    }
}
