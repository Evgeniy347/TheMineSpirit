package com.theminespirit.neoforge.husbandry;

import com.theminespirit.core.script.MilkParams;
import com.theminespirit.neoforge.SpiritEntity;

public final class MilkScript extends HerdScript {
    public MilkScript(SpiritEntity spirit, MilkParams params) {
        super(HerdSession.milk(spirit, params.radius(), params.count()));
    }
}
