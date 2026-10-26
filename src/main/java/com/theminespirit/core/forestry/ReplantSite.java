package com.theminespirit.core.forestry;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.Placement;
import com.theminespirit.core.town.TownPlan;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class ReplantSite {
    public static final Set<String> GREEN_PLOTS = Set.of("grove", "orchard");

    private ReplantSite() {}

    public static boolean allowed(@Nullable TownPlan town, BlockCoord cell) {
        Objects.requireNonNull(cell, "cell не может быть null: саженец сажают в конкретную клетку");
        if (town == null) {
            return true;
        }
        for (Placement place : town.placements()) {
            if (place.covers(cell.x(), cell.z())) {
                return GREEN_PLOTS.contains(place.id());
            }
        }
        return !town.streets().contains(cell.x(), cell.z());
    }
}
