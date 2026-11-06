package com.theminespirit.neoforge.husbandry;

import com.theminespirit.core.script.CollectEggsParams;
import com.theminespirit.neoforge.SpiritEntity;

public final class CollectEggsScript extends HerdScript {
    public CollectEggsScript(SpiritEntity spirit, CollectEggsParams params) {
        super(HerdSession.collectEggs(spirit, params.radius()));
    }
}
