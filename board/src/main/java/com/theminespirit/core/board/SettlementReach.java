package com.theminespirit.core.board;

import com.theminespirit.core.town.ColumnMask;
import org.jspecify.annotations.Nullable;

public final class SettlementReach {
    public static final int MARGIN = 100;

    private SettlementReach() {}

    public static boolean near(@Nullable ColumnMask region, int x, int z) {
        if (region == null) {
            return false;
        }
        int maxX = region.minX() + region.width() - 1;
        int maxZ = region.minZ() + region.depth() - 1;
        int dx = Math.max(0, Math.max(region.minX() - x, x - maxX));
        int dz = Math.max(0, Math.max(region.minZ() - z, z - maxZ));
        return dx * dx + dz * dz <= MARGIN * MARGIN;
    }
}
