package com.theminespirit.core.tactics;

import java.util.List;

public final class LayerOrder {
    public static final List<Layer> PRIORITY = List.of(Layer.POLICY, Layer.TACTICS, Layer.STRATEGY, Layer.IDLE);
    public static final boolean INTERRUPTED_LAYER_RESUMES = true;

    private LayerOrder() {}

    public static boolean outranks(Layer higher, Layer lower) {
        return PRIORITY.indexOf(higher) < PRIORITY.indexOf(lower);
    }
}
