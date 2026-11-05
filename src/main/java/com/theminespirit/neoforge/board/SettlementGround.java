package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.MineDescentBottom;
import com.theminespirit.core.board.PlotLeveling;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.SiteLevels;
import com.theminespirit.core.town.StageSite;
import java.util.function.IntBinaryOperator;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

public final class SettlementGround {
    private static final int LEVELING_SHOWN = 6;
    private static final Logger LOGGER = LogUtils.getLogger();

    private SettlementGround() {}

    public static IntBinaryOperator feet(ServerLevel level, int fallback) {
        return (x, z) -> GroundProbe.feet(level, x, z, fallback);
    }

    public static IntBinaryOperator tops(ServerLevel level, int fallback) {
        return (x, z) -> GroundProbe.top(level, x, z, fallback);
    }

    public static int waterAbove(ServerLevel level, ColumnMask area) {
        int above = Integer.MIN_VALUE;
        for (ColumnMask.Cell cell : area.cells()) {
            int water = GroundProbe.waterTop(level, cell.x(), cell.z(), Integer.MIN_VALUE);
            if (water != Integer.MIN_VALUE) {
                above = Math.max(above, water + 1);
            }
        }
        return above;
    }

    static PlotLeveling leveling(ServerLevel level, StageSite site) {
        IntBinaryOperator tops = tops(level, site.anchor().y() + 1);
        PlotLeveling plan = SiteLevels.leveling(site, tops, PlotPlanBlocks.of(site)::planned);
        if (!plan.cut().isEmpty()) {
            LOGGER.info(
                    "Расчистка на y={}: лишнего выше уровня {} ящиков, подсыпки нет",
                    site.anchor().y(),
                    plan.cut().size());
            if (plan.cut().size() <= LEVELING_SHOWN) {
                LOGGER.info("[plot] срезка выше уровня: {}", plan.cut());
            }
        }
        return plan;
    }

    static IntBinaryOperator plotFeet(ServerLevel level, StageSite site) {
        int passageEnd = descentEnd(site);
        BlockCoord anchor = site.anchor();
        IntBinaryOperator feet = feet(level, anchor.y());
        return (x, z) -> {
            if (site.minePassage(x, z) && z <= passageEnd) {
                return anchor.y();
            }
            int here = feet.applyAsInt(x, z);
            return site.mineColumn(x, z) ? Math.min(here, anchor.y()) : here;
        };
    }

    private static int descentEnd(StageSite site) {
        try {
            return MineDescentBottom.standOf(site.mineEntrance())
                    .map(BlockCoord::z)
                    .orElse(Integer.MAX_VALUE);
        } catch (IllegalStateException | IllegalArgumentException unbuildable) {
            return Integer.MAX_VALUE;
        }
    }
}
