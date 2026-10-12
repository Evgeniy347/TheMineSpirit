package com.theminespirit.neoforge.husbandry;

import com.theminespirit.core.script.HerdCheckParams;
import com.theminespirit.neoforge.SpiritEntity;

public final class HerdCheckScript extends HerdScript {
    public HerdCheckScript(SpiritEntity spirit, HerdCheckParams params) {
        super(HerdSession.herdCheck(spirit, params.species(), params.radius()));
    }
}
