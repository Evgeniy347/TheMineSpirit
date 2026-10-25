package com.theminespirit.core.town;

public final class TownFixtures {
    public static final int LEVEL = 70;
    private static final TownPlan FLAT = TownPlannerTests.plan((x, z) -> LEVEL, (x, z) -> 0);

    private TownFixtures() {}

    public static TownPlan flatPlan() {
        return FLAT;
    }

    public static StageSite flatSite() {
        return StageSites.of(FLAT, 1, LEVEL, TownRules.load());
    }
}
