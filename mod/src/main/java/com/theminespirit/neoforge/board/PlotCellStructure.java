package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.SiteProtection;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.api.PlanBuilding;
import com.theminespirit.neoforge.board.PlotPlanBlocks.Plan;
import java.util.Map;
import java.util.Objects;
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
    private static long generation;
    private static @Nullable BlockPos checkedCell;
    private static boolean checkedWithSite;
    private static long checkedGeneration = -1;
    private static boolean checkedStructure;

    private PlotCellStructure() {}

    public static boolean isStructure(ServerLevel level, BlockPos pos) {
        StageSite site = site(level);
        boolean hasSite = site != null;
        if (pos.equals(checkedCell) && checkedWithSite == hasSite && checkedGeneration == generation) {
            return checkedStructure;
        }
        boolean found = hasSite && computeStructure(site, pos);
        checkedCell = pos.immutable();
        checkedWithSite = hasSite;
        checkedGeneration = generation;
        checkedStructure = found;
        return found;
    }

    private static boolean computeStructure(StageSite site, BlockPos pos) {
        SiteProtection protection = site.protection(pos.getX(), pos.getY(), pos.getZ());
        if (!protection.protectedCell()) {
            return false;
        }
        BlockCoord cell = new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
        return plan(site).blocks().containsKey(cell)
                || (protection.building() == PlanBuilding.MINE
                        && mineFloors(site).contains(cell));
    }

    static boolean plannedChest(ServerLevel level, BlockCoord cell) {
        StageSite site = site(level);
        if (site == null) {
            return false;
        }
        String block = plan(site).blocks().get(cell);
        return block != null && block.endsWith("chest");
    }

    public static long generation() {
        return generation;
    }

    private static @Nullable StageSite site(ServerLevel level) {
        return SettlementPlotStore.liveSite(level).orElse(null);
    }

    private static Plan plan(StageSite site) {
        String rulesHash = DataTables.INSTANCE.stages().status().hash();
        Plan cached = cachedPlan;
        if (cached == null || !Objects.equals(site, cachedSite) || !rulesHash.equals(cachedRulesHash)) {
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
            generation++;
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
