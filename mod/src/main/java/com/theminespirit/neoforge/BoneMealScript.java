package com.theminespirit.neoforge;

import com.theminespirit.core.script.BoneMealParams;

final class BoneMealScript extends FarmScript {
    BoneMealScript(SpiritEntity spirit, BoneMealParams params) {
        super(FarmSession.boneMeal(spirit, params.crop(), params.radius(), params.count()));
    }
}
