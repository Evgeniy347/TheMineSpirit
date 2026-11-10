package com.theminespirit.neoforge.board;

import com.theminespirit.neoforge.SpiritBrain;
import com.theminespirit.neoforge.SpiritEntity;

final class SpiritFacts {
    private SpiritFacts() {}

    static boolean noPath(SpiritEntity spirit) {
        return spirit.brainIfPresent().flatMap(SpiritBrain::failureFact).isPresent();
    }
}
