package com.theminespirit.neoforge.husbandry;

import com.theminespirit.core.script.BreedParams;
import com.theminespirit.neoforge.SpiritEntity;

public final class BreedScript extends HerdScript {
    public BreedScript(SpiritEntity spirit, BreedParams params) {
        super(HerdSession.breed(spirit, params.species(), params.radius(), params.pairs()));
    }
}
