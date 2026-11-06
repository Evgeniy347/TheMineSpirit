package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.building.BuildKey;
import com.theminespirit.core.building.MakeWayPolicy;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

final class BuildMakeWay {
    private static final int RADIUS = 6;
    private static final Logger LOGGER = LogUtils.getLogger();

    private BuildMakeWay() {}

    static boolean selfIn(SpiritEntity spirit, BlockCoord cell) {
        return spirit.getBoundingBox().intersects(new AABB(SchematicSite.toPos(cell)));
    }

    static boolean clear(SpiritEntity builder, Level level, SchematicSite site, BuildKey key, BlockCoord cell) {
        AABB box = new AABB(SchematicSite.toPos(cell));
        boolean clear = true;
        for (SpiritEntity other : level.getEntitiesOfClass(SpiritEntity.class, box)) {
            if (other.equals(builder)) {
                clear = false;
                continue;
            }
            boolean mustMove = MakeWayPolicy.mustMove(
                    other.taskStatus().orElse(null) == RunStatus.RUNNING,
                    other.currentTaskScript().orElse(""));
            if (!mustMove) {
                clear = false;
                continue;
            }
            BlockPos feet = other.blockPosition();
            Optional<BlockCoord> stand = outsideStand(site, new BlockCoord(feet.getX(), feet.getY(), feet.getZ()));
            if (stand.isEmpty()) {
                clear = false;
                continue;
            }
            LOGGER.info(
                    "Стройка {}: дух {} стоит в клетке {} — отходит к {}",
                    key,
                    other.displayName(),
                    cell,
                    stand.orElseThrow());
            BlockCoord target = stand.orElseThrow();
            other.startTask(new ScriptCall("goto", Map.of("x", target.x(), "y", target.y(), "z", target.z())));
            clear = false;
        }
        return clear;
    }

    static Optional<BlockCoord> outsideStand(SchematicSite site, BlockCoord from) {
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
