package com.theminespirit.neoforge.husbandry;

import com.theminespirit.core.script.CullParams;
import com.theminespirit.neoforge.SpiritEntity;

public final class CullScript extends HerdScript {
    public CullScript(SpiritEntity spirit, CullParams params) {
        super(HerdSession.cull(spirit, params.species(), params.keep(), params.radius()));
    }
}
