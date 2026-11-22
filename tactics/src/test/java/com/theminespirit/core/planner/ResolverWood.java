package com.theminespirit.core.planner;

import com.theminespirit.core.forestry.api.ChopTuning;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.api.CatalogLimits;
import java.util.List;
import java.util.Map;

final class ResolverWood {
    private ResolverWood() {}

    static TaskNode wood(String block, int count) {
        return new FallbackNode(List.of(chop(speciesOf(block), trees(count)), gather(block, count)));
    }

    static ScriptCall chop(String species, int trees) {
        return new ScriptCall(
                "chop_tree",
                Map.of(
                        "species",
                        species,
                        "count",
                        trees,
                        "radius",
                        CatalogLimits.HARVEST_RADIUS_MAX,
                        "replant",
                        false));
    }

    static int trees(int logs) {
        int estimated = (logs + ChopTuning.LOGS_PER_TREE - 1) / ChopTuning.LOGS_PER_TREE;
        return Math.min(CatalogLimits.COUNT_MAX, Math.max(1, estimated));
    }

    static String speciesOf(String block) {
        return block.substring(0, block.length() - "_log".length());
    }

    static ScriptCall gather(String block, int count) {
        int radius = block.endsWith("_log") ? CatalogLimits.HARVEST_RADIUS_MAX : CatalogLimits.HARVEST_RADIUS_DEFAULT;
        return new ScriptCall("gather", Map.of("block", block, "count", count, "radius", radius));
    }
}
