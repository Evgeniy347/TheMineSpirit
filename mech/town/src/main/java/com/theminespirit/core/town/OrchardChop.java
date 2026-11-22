package com.theminespirit.core.town;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;

public final class OrchardChop {

    private OrchardChop() {}

    public static boolean allowed(Optional<StageSite> site, BlockCoord cell) {
        return site.map(plot -> allowed(plot, cell)).orElse(true);
    }

    public static boolean allowed(StageSite site, BlockCoord cell) {
        return site.orchardTerritory(cell.x(), cell.z()) && !site.orchardTree(cell.x(), cell.y(), cell.z());
    }
}
