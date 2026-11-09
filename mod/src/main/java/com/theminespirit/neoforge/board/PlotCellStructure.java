package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.PlanBuilding;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.board.PlotPlanBlocks.Plan;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class PlotCellStructure {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static @Nullable StageSite cachedSite;
    private static @Nullable String cachedRulesHash;
    private static @Nullable Plan cachedPlan;
    private static Set<BlockCoord> cachedMine = Set.of();

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
        BlockCoord cell = new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
        if (plan(site).blocks().containsKey(cell)) {
            return true;
        }
        return site.protectionBuilding(cell.x(), cell.y(), cell.z()) == PlanBuilding.MINE
                && mineFloors(site).contains(cell);
    }

    private static @Nullable StageSite site(ServerLevel level) {
        return SettlementPlotStore.liveSite(level).orElse(null);
    }

    private static Plan plan(StageSite site) {
        String rulesHash = DataTables.INSTANCE.stages().status().hash();
        Plan cached = cachedPlan;
        if (cached == null || !site.equals(cachedSite) || !rulesHash.equals(cachedRulesHash)) {
            try {
                cached = PlotPlanBlocks.of(site);
            } catch (RuntimeException failure) {
                cachedPlan = null;
                LOGGER.error("План построек этапа не собран для проверки клетки защиты: {}", failure.getMessage());
                return new Plan(Map.of());
            }
            cachedPlan = cached;
            cachedMine = mine(site);
            cachedSite = site;
            cachedRulesHash = rulesHash;
        }
        return cached;
    }

    private static Set<BlockCoord> mineFloors(StageSite site) {
        plan(site);
        return cachedMine;
    }

    private static Set<BlockCoord> mine(StageSite site) {
        return MineSnapshots.geometry(site).map(geometry -> geometry.floors()).orElse(Set.of());
    }
}
