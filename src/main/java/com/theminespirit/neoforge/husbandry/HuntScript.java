package com.theminespirit.neoforge.husbandry;

import com.theminespirit.core.script.HuntParams;
import com.theminespirit.neoforge.SpiritEntity;

public final class HuntScript extends HerdScript {
    public HuntScript(SpiritEntity spirit, HuntParams params) {
        super(HerdSession.hunt(spirit, params.species(), params.radius(), params.count()));
    }
}
