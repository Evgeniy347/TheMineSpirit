package com.theminespirit.core.town;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;

public final class TreeClearance {

    private TreeClearance() {}

    public static boolean allowed(Optional<StageSite> site, BlockCoord cell) {
        return site.map(plot -> allowed(plot, cell)).orElse(true);
    }

    public static boolean allowed(StageSite site, BlockCoord cell) {
        return site.inside(cell.x(), cell.z()) && !planned(site, cell);
    }

    public static boolean planned(Optional<StageSite> site, BlockCoord cell) {
        return site.map(plot -> planned(plot, cell)).orElse(false);
    }

    public static boolean planned(StageSite site, BlockCoord cell) {
        return site.planCell(cell.x(), cell.y(), cell.z()) || site.orchardTree(cell.x(), cell.y(), cell.z());
    }
}
