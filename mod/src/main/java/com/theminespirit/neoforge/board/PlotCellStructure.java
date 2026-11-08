package com.theminespirit.neoforge.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.board.PlotPlanBlocks.Plan;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

public final class PlotCellStructure {
    private static @Nullable StageSite cachedSite;
    private static @Nullable String cachedRulesHash;
    private static @Nullable Plan cachedPlan;

    private PlotCellStructure() {}

    public static boolean isStructure(ServerLevel level, BlockPos pos) {
        StageSite site = site(level);
        if (site == null || !site.protectionCell(pos.getX(), pos.getY(), pos.getZ())) {
            return false;
        }
        PlacedBlocksData placed = PlacedBlocksData.get(level);
        if (placed.isSpiritMarked(pos) || placed.isMarked(pos)) {
            return true;
        }
        return plan(site).blocks().containsKey(new BlockCoord(pos.getX(), pos.getY(), pos.getZ()));
    }

    private static @Nullable StageSite site(ServerLevel level) {
        return SettlementPlotStore.get(level).site().orElse(null);
    }

    private static Plan plan(StageSite site) {
        String rulesHash = DataTables.INSTANCE.stages().status().hash();
        Plan cached = cachedPlan;
        if (cached == null || !site.equals(cachedSite) || !rulesHash.equals(cachedRulesHash)) {
            try {
                cached = PlotPlanBlocks.of(site);
            } catch (RuntimeException failure) {
                cachedPlan = null;
                return new Plan(Map.of());
            }
            cachedPlan = cached;
            cachedSite = site;
            cachedRulesHash = rulesHash;
        }
        return cached;
    }
}
