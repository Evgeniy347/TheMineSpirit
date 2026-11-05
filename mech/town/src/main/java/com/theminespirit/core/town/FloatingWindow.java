package com.theminespirit.core.town;

import java.util.Objects;

public final class FloatingWindow {
    private static final int BELOW = 2;
    private static final int ABOVE = 32;

    private FloatingWindow() {}

    public static int bottomOf(SettlementPlot plot) {
        Objects.requireNonNull(plot, "Участок не может быть null: без него не видно, где искать висящее");
        return plot.min() - BELOW;
    }

    public static int topOf(SettlementPlot plot) {
        Objects.requireNonNull(plot, "Участок не может быть null: без него не видно, где искать висящее");
        return plot.max() + ABOVE;
    }
}
