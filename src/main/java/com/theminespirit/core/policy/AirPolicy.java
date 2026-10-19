package com.theminespirit.core.policy;

public final class AirPolicy {
    public static final int AIR_ALARM_TICKS = 100;

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
