package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.mining.MiningSafety;
import com.theminespirit.core.movement.DropProfile;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

final class WallEscape {
    static final int RADIUS = 2;
    private static final Logger LOGGER = LogUtils.getLogger();

    private WallEscape() {}

    static void tick(SpiritEntity spirit) {
        if (!spirit.isInWall()) {
            return;
        }
        BlockPos feet = spirit.blockPosition();
        Optional<BlockPos> free = nearestFree(spirit.level(), feet);
        if (free.isEmpty()) {
            return;
        }
        BlockPos cell = free.get();
        spirit.getNavigation().stop();
        spirit.getNavigation().moveTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5, 1.0);
        LOGGER.info(
                "[wall] {} в стене у {}: шаг в свободную клетку {}",
                spirit.getName().getString(),
                feet.toShortString(),
                cell.toShortString());
    }

    static Optional<BlockPos> nearestFree(Level level, BlockPos feet) {
        List<BlockPos> cells = new ArrayList<>();
        for (int dy = -1; dy <= RADIUS; dy++) {
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    cells.add(feet.offset(dx, dy, dz));
                }
            }
        }
        cells.sort(Comparator.comparingDouble((BlockPos cell) -> cell.distSqr(feet))
                .thenComparing(Comparator.naturalOrder()));
        return cells.stream().filter(cell -> standable(level, cell)).findFirst();
    }

    private static boolean standable(Level level, BlockPos cell) {
        return level.isLoaded(cell)
                && MiningSafety.canStandOnGround(
                        new BlockCoord(cell.getX(), cell.getY(), cell.getZ()),
                        new LevelSafetyView(level),
                        DropProfile.MAX_SAFE_DROP);
    }
}
