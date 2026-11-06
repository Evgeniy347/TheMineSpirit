package com.theminespirit.neoforge.board;

import com.theminespirit.core.observation.BlockCoord;
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
}
