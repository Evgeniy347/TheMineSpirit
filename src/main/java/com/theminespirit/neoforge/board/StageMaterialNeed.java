package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BuildGap;
import com.theminespirit.core.board.PlotLeveling;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;

final class StageMaterialNeed {
    private static final String COBBLESTONE = "cobblestone";

    private StageMaterialNeed() {}

    static Map<String, Integer> orchard(ServerLevel level, TicketTarget target, String wood) {
        return BuildGap.requirements(
                TicketPlans.plan(BoardCase.BUILD_ORCHARD, 1, target),
                wood,
                FirstStepsRunner.probe(level),
                FirstStepsRunner.SCHEMES);
    }

    static void mine(StockView stock, Map<String, Integer> need) {
        int floors = need.getOrDefault(COBBLESTONE, 0);
        need.remove(COBBLESTONE);
        if (floors <= 0) {
            return;
        }
        String block = stock.available().getOrDefault(COBBLESTONE, 0) >= floors
                        || stock.available().getOrDefault(PlotLeveling.DEFAULT_BLOCK, 0) < floors
                ? COBBLESTONE
                : PlotLeveling.DEFAULT_BLOCK;
        need.merge(block, floors, Integer::sum);
    }
}
