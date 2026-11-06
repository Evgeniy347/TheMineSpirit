package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.FluidPlans;
import com.theminespirit.core.building.BuildKey;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.FluidKind;
import com.theminespirit.neoforge.board.FluidSources;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

final class BuildWater {
    private static final int STAND_RING = 6;
    private static final Logger LOGGER = LogUtils.getLogger();

    record Plan(Optional<TaskNode> task, String reason) {}

    private BuildWater() {}

    static Plan plan(SpiritEntity spirit, SchematicSite site, Schematic.PlacedEntry entry, BuildKey buildKey) {
        BlockCoord cell = entry.pos();
        if (spirit.getInventory().countItem(Items.WATER_BUCKET) > 0) {
            LOGGER.info("Стройка {}: вода ведром из инвентаря в {} — клетка схемы {}", buildKey, cell, entry.block());
            return new Plan(Optional.of(FluidPlans.pourFromBucket(FluidKind.WATER, cell)), "");
        }
        if (!(spirit.level() instanceof ServerLevel server)) {
            return new Plan(Optional.empty(), "мир не серверный: воду ведром не набрать");
        }
        Optional<BlockCoord> source = FluidSources.nearest(server, cell, FluidKind.WATER);
        if (source.isEmpty()) {
            String reason = "нет источника воды в радиусе: воду ведром не набрать";
            LOGGER.info("Стройка {}: нет источника воды для клетки {} — ищу дальше", buildKey, cell);
            return new Plan(Optional.empty(), reason);
        }
        BlockCoord at = source.orElseThrow();
        BlockCoord stand = waterStand(site, at).orElse(at);
        LOGGER.info(
                "Стройка {}: вода ведром из {} (стоянка {}) в {} — клетка схемы {}",
                buildKey,
                at,
                stand,
                cell,
                entry.block());
        return new Plan(Optional.of(FluidPlans.waterCrops(FluidKind.WATER, at, stand, cell)), "");
    }

    private static Optional<BlockCoord> waterStand(SchematicSite site, BlockCoord source) {
        for (int ring = 1; ring <= STAND_RING; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockCoord feet = new BlockCoord(source.x() + dx, source.y() + dy, source.z() + dz);
                        if (site.standable(feet)) {
                            return Optional.of(feet);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }
}
