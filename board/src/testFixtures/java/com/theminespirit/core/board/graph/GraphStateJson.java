package com.theminespirit.core.board.graph;

import com.theminespirit.core.board.GraphBlock;
import java.util.LinkedHashMap;
import java.util.List;
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
        out.put("blocks", blocks(state.blocks()));
        return out;
    }

    private static List<Map<String, Object>> blocks(List<GraphBlock> blocks) {
        return blocks.stream()
                .map(block -> {
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("node", block.node());
                    out.put("target", block.target());
                    out.put("need", need(block.need()));
                    return out;
                })
                .toList();
    }

    private static Map<String, Object> need(GraphBlock.Need need) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (need instanceof GraphBlock.Item item) {
            out.put("kind", "item");
            out.put("item", item.item());
            out.put("count", item.count());
        } else if (need instanceof GraphBlock.Tool tool) {
            out.put("kind", "tool");
            out.put("tool", tool.kind());
            out.put("rank", tool.rank());
            out.put("spirit", tool.spirit());
        } else if (need instanceof GraphBlock.Building building) {
            out.put("kind", "building");
            out.put("building", building.id());
        } else if (need instanceof GraphBlock.Path path) {
            out.put("kind", "path");
            out.put("point", path.point());
        } else if (need instanceof GraphBlock.Holder holder) {
            out.put("kind", "holder");
            out.put("spirit", holder.spirit());
            out.put("stuck", holder.stuck());
        } else if (need instanceof GraphBlock.Impossible impossible) {
            out.put("kind", "impossible");
            out.put("node", impossible.node());
        }
        return out;
    }
}
