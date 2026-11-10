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
        String script =
                spirit.brainIfPresent().flatMap(SpiritBrain::currentScript).orElse(null);
        return WaterTasks.allowsSwimming(script, spirit.currentPlan().orElse(null));
    }
}
