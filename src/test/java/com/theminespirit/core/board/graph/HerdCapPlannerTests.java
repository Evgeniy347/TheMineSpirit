package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HerdCapPlannerTests {
    @Test
    void plan_PerSpiritHerdCap_CullRaisedBySpiritCountNotFixedNumber() {
        GraphWorld crowded = GraphWorld.settled()
                .built("pen_cow", Built.BUILT)
                .built("pen_sheep", Built.BUILT)
                .with("wheat", 20);
        crowded.stage = 2;
        crowded.graph = perSpiritCows(0.5);
        crowded.kits.clear();
        crowded.kits.put("spirit-1", Map.of());
        crowded.kits.put("spirit-2", Map.of());
        crowded.kits.put("spirit-3", Map.of());
        GraphPlan low = crowded.with("herd_cow", 0).with("beef", 64).plan();
        GraphPlan high = crowded.with("herd_cow", 2).with("beef", 64).plan();

        assertTrue(
                high.priority("cull_cow") > low.priority("cull_cow"),
                "Лимит «половина духов»: три духа держат одну корову, две головы — перебор и забой важнее: "
                        + high.priority("cull_cow") + " против " + low.priority("cull_cow"));
    }

    private static SupplyGraph perSpiritCows(double share) {
        SupplyGraph base = GraphWorld.GRAPH;
        Map<String, HerdCap> caps = new LinkedHashMap<>(base.herdCaps());
        caps.put("herd_cow", new HerdCap(null, share));
        return new SupplyGraph(
                base.decay(),
                base.urgentBoost(),
                base.buildReadyBoost(),
                base.maxPriority(),
                base.tiers(),
                base.kit(),
                base.storage(),
                base.items(),
                base.buildings(),
                base.ways(),
                base.edges(),
                base.unimplemented(),
                base.events(),
                base.groups(),
                base.triggers(),
                caps,
                base.buildStartShare(),
                base.costs());
    }
}
