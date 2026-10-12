package com.theminespirit.neoforge.husbandry;

import com.theminespirit.core.script.FeedParams;
import com.theminespirit.neoforge.SpiritEntity;

public final class FeedScript extends HerdScript {
    public FeedScript(SpiritEntity spirit, FeedParams params) {
        super(HerdSession.feed(spirit, params.species(), params.radius(), params.count(), params.babiesFirst()));
    }
}
