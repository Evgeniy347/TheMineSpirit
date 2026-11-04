package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.building.BuildKey;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

final class BuildStands {
    private static final int RADIUS = 6;
    private static final Logger LOGGER = LogUtils.getLogger();

    private BuildStands() {}

    static void makeWay(SpiritEntity spirit, SchematicSite site, BlockCoord cell, BuildKey buildKey) {
        AABB box = new AABB(SchematicSite.toPos(cell));
        for (SpiritEntity other : spirit.level().getEntitiesOfClass(SpiritEntity.class, box, e -> !e.equals(spirit))) {
            if (other.taskStatus().orElse(null) == RunStatus.RUNNING) {
                continue;
            }
            BlockPos feet = other.blockPosition();
            outsideStand(site, new BlockCoord(feet.getX(), feet.getY(), feet.getZ()))
                    .ifPresent(stand -> {
                        LOGGER.info(
                                "Стройка {}: дух {} стоит в клетке {} — отходит к {}",
                                buildKey,
                                other.displayName(),
                                cell,
                                stand);
                        other.startTask(new ScriptCall("goto", Map.of("x", stand.x(), "y", stand.y(), "z", stand.z())));
                    });
        }
    }

    private static Optional<BlockCoord> outsideStand(SchematicSite site, BlockCoord from) {
        for (int ring = 1; ring <= RADIUS; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockCoord feet = new BlockCoord(from.x() + dx, from.y() + dy, from.z() + dz);
                        if (site.outsideFootprint(feet) && site.standable(feet)) {
                            return Optional.of(feet);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }
}
