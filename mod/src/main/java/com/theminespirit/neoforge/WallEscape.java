package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.mining.api.MiningSafety;
import com.theminespirit.core.movement.api.DropProfile;
import com.theminespirit.core.movement.api.WallEscapeRules;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

final class WallEscape {
    private static final Logger LOGGER = LogUtils.getLogger();

    private WallEscape() {}

    static void tick(SpiritEntity spirit) {
        if (!spirit.isInWall()) {
            return;
        }
        BlockPos feet = spirit.blockPosition();
        BlockCoord from = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        Optional<BlockCoord> free = WallEscapeRules.nearestFree(from, around(feet), cell -> usable(spirit, cell));
        if (free.isEmpty()) {
            return;
        }
        BlockCoord picked = free.get();
        spirit.getNavigation().stop();
        spirit.getNavigation().moveTo(picked.x() + 0.5, picked.y(), picked.z() + 0.5, 1.0);
        LOGGER.info(
                "[wall] {} в стене у {}: шаг в свободную клетку {}",
                spirit.getName().getString(),
                feet.toShortString(),
                new BlockPos(picked.x(), picked.y(), picked.z()).toShortString());
    }

    private static List<BlockCoord> around(BlockPos feet) {
        List<BlockCoord> cells = new ArrayList<>();
        for (int dy = -1; dy <= SpiritConfig.wallEscapeRadius(); dy++) {
            for (int dx = -SpiritConfig.wallEscapeRadius(); dx <= SpiritConfig.wallEscapeRadius(); dx++) {
                for (int dz = -SpiritConfig.wallEscapeRadius(); dz <= SpiritConfig.wallEscapeRadius(); dz++) {
                    cells.add(new BlockCoord(feet.getX() + dx, feet.getY() + dy, feet.getZ() + dz));
                }
            }
        }
        return cells;
    }

    private static boolean usable(SpiritEntity spirit, BlockCoord cell) {
        BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
        return standable(spirit.level(), pos) && bodyFree(spirit, pos);
    }

    private static boolean bodyFree(SpiritEntity spirit, BlockPos cell) {
        double half = spirit.getBbWidth() / 2.0;
        AABB box = new AABB(
                cell.getX() + 0.5 - half,
                cell.getY(),
                cell.getZ() + 0.5 - half,
                cell.getX() + 0.5 + half,
                cell.getY() + spirit.getBbHeight(),
                cell.getZ() + 0.5 + half);
        return !spirit.level().getBlockCollisions(spirit, box).iterator().hasNext();
    }

    private static boolean standable(Level level, BlockPos cell) {
        return level.isLoaded(cell)
                && MiningSafety.canStandOnGround(
                        new BlockCoord(cell.getX(), cell.getY(), cell.getZ()),
                        new LevelSafetyView(level),
                        DropProfile.MAX_SAFE_DROP);
    }
}
