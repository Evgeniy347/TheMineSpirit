package com.theminespirit.neoforge.policy;

import com.theminespirit.core.policy.BodyFlags;
import com.theminespirit.core.policy.PolicyId;
import com.theminespirit.core.policy.WaterTasks;
import com.theminespirit.neoforge.SpiritBrain;
import com.theminespirit.neoforge.SpiritEntity;

final class BodyFlagsReader {
    private BodyFlagsReader() {}

    static BodyFlags read(SpiritEntity spirit) {
        boolean swimmingAllowed =
                swimsByTask(spirit) || spirit.tactics().exemptions().has(PolicyId.WATER);
        return new BodyFlags(
                spirit.isInLava(), spirit.isOnFire(), spirit.isInWater() && !swimmingAllowed, spirit.hurtTime > 0);
    }

    private static boolean swimsByTask(SpiritEntity spirit) {
        return spirit.brainIfPresent()
                .flatMap(SpiritBrain::currentScript)
                .map(WaterTasks::allowsSwimming)
                .orElse(false);
    }
}
