package com.theminespirit.neoforge.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.SettlementPlot;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Optional;

final class SettlementHome {
    private SettlementHome() {}

    static Optional<BlockCoord> center(List<BlockCoord> homes) {
        if (homes.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(SettlementPlot.center(homes));
    }

    static List<BlockCoord> homes(List<SpiritEntity> spirits) {
        return spirits.stream().flatMap(spirit -> spirit.home().stream()).toList();
    }
}
