package com.theminespirit.neoforge;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;

final class SelfCraftSteps {
    private SelfCraftSteps() {}

    static List<ScriptCall> of(SpiritEntity spirit, String blockId, int count) {
        List<ScriptCall> steps = new ArrayList<>();
        if (spirit.level() instanceof ServerLevel server) {
            SettlementPlotStore.liveSite(server).ifPresent(site -> {
                BlockCoord yard = site.workYard();
                steps.add(new ScriptCall("goto", Map.of("x", yard.x(), "y", yard.y(), "z", yard.z())));
            });
        }
        steps.add(new ScriptCall("acquire", Map.of("item", blockId, "count", count)));
        return steps;
    }
}
