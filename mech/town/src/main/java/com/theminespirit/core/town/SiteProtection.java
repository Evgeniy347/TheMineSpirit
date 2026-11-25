package com.theminespirit.core.town;

import com.theminespirit.core.town.api.PlanBuilding;

public record SiteProtection(boolean protectedCell, PlanBuilding building, String area) {
    public static final SiteProtection NONE = new SiteProtection(false, PlanBuilding.NONE, "");
}
