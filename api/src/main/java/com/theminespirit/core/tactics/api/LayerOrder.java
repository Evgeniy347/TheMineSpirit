package com.theminespirit.core.tactics.api;

import java.util.List;

public final class LayerOrder {
    public static final List<Layer> PRIORITY = List.of(Layer.POLICY, Layer.TACTICS, Layer.STRATEGY, Layer.IDLE);

    private LayerOrder() {}

    public static boolean outranks(Layer higher, Layer lower) {
        return PRIORITY.indexOf(higher) < PRIORITY.indexOf(lower);
    }
}
