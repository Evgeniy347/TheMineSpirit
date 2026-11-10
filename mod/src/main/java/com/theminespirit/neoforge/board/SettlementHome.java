package com.theminespirit.neoforge.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.SettlementPlot;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;

final class SettlementHome {
    private SettlementHome() {}

    static BlockCoord of(List<SpiritEntity> spirits) {
        List<BlockCoord> homes = homes(spirits);
        if (homes.isEmpty()) {
            return new BlockCoord(0, 64, 0);
        }
        return SettlementPlot.center(homes);
    }

    static List<BlockCoord> homes(List<SpiritEntity> spirits) {
        return spirits.stream().flatMap(spirit -> spirit.home().stream()).toList();
    }

    static int y(List<SpiritEntity> spirits) {
        return SettlementPlot.center(homes(spirits)).y();
    }
}
