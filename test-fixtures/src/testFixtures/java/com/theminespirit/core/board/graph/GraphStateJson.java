package com.theminespirit.core.board.graph;

import java.util.LinkedHashMap;
import java.util.Map;

public final class GraphStateJson {
    private GraphStateJson() {}

    public static Map<String, Object> state(GraphState state) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stock", state.stock());
        out.put("stage", state.stage());
        out.put("kits", state.kits());
        out.put("buildings", state.buildings());
        out.put("materials", state.materials());
        out.put("storageFreePercent", state.storageFreePercent());
        out.put("events", state.events().stream().sorted().toList());
        out.put("world", state.world().stream().sorted().toList());
        out.put("distances", state.distances());
        out.put("stations", state.stations());
        out.put("orders", state.orders());
        out.put("facts", state.facts());
        return out;
    }
}
