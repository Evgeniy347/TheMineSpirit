package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.LevelingRules;
import com.theminespirit.core.board.MineDescentBottom;
import com.theminespirit.core.board.StageCatalog;
import com.theminespirit.core.board.StageRules;
import com.theminespirit.core.board.WaterLevel;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.SchematicGround;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.PlotLeveling;
import com.theminespirit.core.town.SiteLevels;
import com.theminespirit.core.town.StageSite;
import java.util.ArrayList;
import java.util.List;
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

    public record WaterScan(int blocks, int median, int floor) {
        public boolean limited() {
            return floor != Integer.MIN_VALUE;
        }
    }

    public static WaterScan waterScan(ServerLevel level, BlockCoord base, LevelingRules rules) {
        int radius = rules.waterRadius();
        List<Integer> tops = new ArrayList<>();
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int water = GroundProbe.waterTop(level, base.x() + dx, base.z() + dz, Integer.MIN_VALUE);
                if (water != Integer.MIN_VALUE) {
                    tops.add(water);
                }
            }
        }
        int[] levels = tops.stream().mapToInt(Integer::intValue).toArray();
        return WaterLevel.of(levels, rules.waterMinBlocks())
                .map(water -> new WaterScan(water.blocks(), water.median(), water.minSettlementLevel()))
                .orElseGet(() -> new WaterScan(levels.length, Integer.MIN_VALUE, Integer.MIN_VALUE));
    }

    static PlotLeveling leveling(ServerLevel level, StageSite site, int stage) {
        IntBinaryOperator tops = tops(level, site.anchor().y() + 1);
        PlotLeveling plan = SiteLevels.leveling(site, tops, PlotPlanBlocks.of(site)::planned, area(site, stage));
        if (!plan.cut().isEmpty() || !plan.fill().isEmpty()) {
            LOGGER.info(
                    "Выравнивание до y0={} (проходы y0+1={}): срезка {} боксов, подсыпка {} боксов",
                    SiteLevels.level(site),
                    site.anchor().y(),
                    plan.cut().size(),
                    plan.fill().size());
            if (plan.cut().size() <= LEVELING_SHOWN) {
                LOGGER.info("[plot] срезка выше уровня: {}", plan.cut());
            }
            if (plan.fill().size() <= LEVELING_SHOWN) {
                LOGGER.info("[plot] подсыпка до уровня: {}", plan.fill());
            }
        }
        return plan;
    }

    static ColumnMask area(StageSite site, int stage) {
        ColumnMask area = site.streets();
        List<String> plan = StageRules.current().plans().get(stage - 1);
        for (String id : plan) {
            for (var place : site.placements()) {
                if (id.equals(place.id()) && !tilled(place.id())) {
                    area = area.union(place.mask());
                }
            }
        }
        return area;
    }

    private static boolean tilled(String id) {
        return StageCatalog.entry(id)
                .flatMap(entry -> PresetLibrary.current().byId(entry.scheme()))
                .map(preset -> SchematicGround.tilled(preset.schematic()))
                .orElse(false);
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
