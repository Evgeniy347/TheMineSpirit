package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.MinePlot;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class MineTunnelRule {
    public static final int TUNNEL_REACH = 64;
    public static final int TUNNEL_HEIGHT = 4;

    private static final int TUNNEL_BELOW = 2;
    private static final int PLOT_MARGIN = 1;

    private MineTunnelRule() {}

    public static boolean allowed(
            BlockCoord feet, @Nullable MinePlot plot, @Nullable BlockCoord entrance, @Nullable BlockCoord bottom) {
        Objects.requireNonNull(feet, "Ноги духа не могут быть null: по ним решается, шахта это или склад");
        if (plot == null || entrance == null || bottom == null) {
            return true;
        }
        if (feet.y() > entrance.y()) {
            return false;
        }
        if (plot.covers(feet.x(), feet.z())) {
            return true;
        }
        if (feet.y() < bottom.y() - TUNNEL_BELOW || feet.y() > bottom.y() + TUNNEL_HEIGHT) {
            return false;
        }
        int zLow = plot.z1() - PLOT_MARGIN;
        int zHigh = plot.z2() + PLOT_MARGIN;
        int xLow = Math.min(plot.x1(), bottom.x()) - PLOT_MARGIN;
        int xHigh = Math.max(plot.x2(), bottom.x()) + TUNNEL_REACH;
        return feet.z() >= zLow && feet.z() <= zHigh && feet.x() >= xLow && feet.x() <= xHigh;
    }
}
