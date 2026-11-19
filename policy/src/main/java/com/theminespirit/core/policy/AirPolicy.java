package com.theminespirit.core.policy;

import com.theminespirit.core.policy.api.PolicyId;

public final class AirPolicy {
    public static final int AIR_ALARM_TICKS = 200;

    private AirPolicy() {}

    public static PolicyVerdict evaluate(int airSupplyTicks, boolean headUnderWater) {
        if (headUnderWater && airSupplyTicks <= AIR_ALARM_TICKS) {
            return new PolicyVerdict.Reflex(
                    PolicyId.AIR,
                    new ReflexAction.Surface(),
                    "запас воздуха " + airSupplyTicks + " тиков, голова под водой");
        }
        return new PolicyVerdict.Clear();
    }
}
