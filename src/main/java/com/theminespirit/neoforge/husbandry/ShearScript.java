package com.theminespirit.neoforge.husbandry;

import com.theminespirit.core.script.ShearParams;
import com.theminespirit.neoforge.SpiritEntity;

public final class ShearScript extends HerdScript {
    public ShearScript(SpiritEntity spirit, ShearParams params) {
        super(HerdSession.shear(spirit, params.radius(), params.count()));
    }
}
