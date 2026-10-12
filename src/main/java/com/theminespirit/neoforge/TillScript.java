package com.theminespirit.neoforge;

import com.theminespirit.core.script.TillParams;

final class TillScript extends FarmScript {
    TillScript(SpiritEntity spirit, TillParams params) {
        super(FarmSession.till(spirit, params.radius(), params.count()));
    }
}
