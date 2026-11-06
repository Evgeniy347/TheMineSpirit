package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.PlotPlanBlocks.Plan;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class PlotEditLog {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int PLAN_ABOVE = 24;

    private static @Nullable StageSite cachedSite;
    private static @Nullable String cachedRulesHash;
    private static @Nullable Plan cachedPlan;

    private PlotEditLog() {}

    public static void broken(ServerLevel level, BlockPos pos, @Nullable Entity breaker) {
        Optional<StageSite> found = SettlementPlotStore.get(level).site();
        if (found.isEmpty()) {
            return;
        }
        StageSite site = found.get();
        if (pos.getY() < site.anchor().y() - 1 || pos.getY() > site.anchor().y() + PLAN_ABOVE) {
            return;
        }
        if (!site.planned(pos.getX(), pos.getY(), pos.getZ())) {
            return;
        }
        Plan plan = plan(site);
        BlockCoord cell = new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
        String expected = plan.blocks().get(cell);
        if (expected == null) {
            return;
        }
        LOGGER.info(
                "[plot-edit] «{}»: сломан блок плана {} в ({}, {}, {}) — {}; задание {}; клетку вернёт ремонт",
                area(site, cell),
                expected,
                cell.x(),
                cell.y(),
                cell.z(),
                who(breaker),
                task(breaker));
    }

    static String task(@Nullable Entity breaker) {
        if (!(breaker instanceof SpiritEntity spirit) || !(spirit.level() instanceof ServerLevel server)) {
            return "—";
        }
        String boardTask = BoardRegistry.peek(server)
                .map(service -> BoardLog.culprit(service.board().assignedTo(spirit.getUUID())))
                .orElse(null);
        return BoardLog.culpritOrAction(boardTask, spirit.currentTaskScript().orElse(null));
    }

    private static Plan plan(StageSite site) {
        String rulesHash = DataTables.INSTANCE.stages().status().hash();
        Plan cached = cachedPlan;
        if (cached == null || !site.equals(cachedSite) || !rulesHash.equals(cachedRulesHash)) {
            try {
                cached = PlotPlanBlocks.of(site);
            } catch (RuntimeException failure) {
                cachedPlan = null;
                LOGGER.error("План построек этапа не собран для лога правок мира: {}", failure.getMessage());
                return new Plan(Map.of());
            }
            cachedPlan = cached;
            cachedSite = site;
            cachedRulesHash = rulesHash;
        }
        return cached;
    }

    private static String area(StageSite site, BlockCoord cell) {
        if (site.hut().covers(cell.x(), cell.z())) {
            return StageRepair.label(BoardCase.BUILD_HOUSE);
        }
        if (site.yard().covers(cell.x(), cell.z())) {
            return StageRepair.label(BoardCase.STOCK_CHESTS);
        }
        if (site.orchard().covers(cell.x(), cell.z()) || site.orchardTree(cell.x(), cell.y(), cell.z())) {
            return StageRepair.label(BoardCase.BUILD_ORCHARD);
        }
        return StageRepair.label(BoardCase.BUILD_FENCE);
    }

    private static String who(@Nullable Entity breaker) {
        if (breaker instanceof Player player) {
            return "игрок " + player.getGameProfile().getName();
        }
        if (breaker instanceof SpiritEntity spirit) {
            return "дух " + spirit.getName().getString() + " (" + spirit.getUUID() + ")";
        }
        if (breaker == null) {
            return "взрыв или мир";
        }
        return breaker.getType().toString() + " " + breaker.getUUID();
    }
}
