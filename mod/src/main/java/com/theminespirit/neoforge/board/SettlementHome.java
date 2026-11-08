package com.theminespirit.neoforge.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.SettlementPlot;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Optional;

final class SettlementHome {
    private SettlementHome() {}

    static BlockCoord of(List<SpiritEntity> spirits) {
        return spirits.stream()
                .map(SpiritEntity::home)
                .flatMap(Optional::stream)
                .findFirst()
                .orElse(new BlockCoord(0, 64, 0));
    }

    static List<BlockCoord> homes(List<SpiritEntity> spirits) {
        return spirits.stream().flatMap(spirit -> spirit.home().stream()).toList();
    }

    static int y(List<SpiritEntity> spirits) {
        return SettlementPlot.center(homes(spirits)).y();
    }
}
