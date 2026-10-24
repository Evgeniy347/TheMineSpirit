package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

/**
 * Рефлекс «выйти из блока» (INC-61): дух, у которого голова в твёрдом блоке (удушье в стене), шагает в ближайшую
 * свободную клетку — два воздуха над твёрдым полом — в пределах {@link #RADIUS}. Как у застревания: не ждёт, пока
 * задохнётся, и задачу не прерывает.
 */
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
        spirit.teleportTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5);
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
        BlockPos floor = cell.below();
        return level.isLoaded(cell)
                && level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()
                && level.getBlockState(cell.above())
                        .getCollisionShape(level, cell.above())
                        .isEmpty()
                && level.getFluidState(cell).isEmpty()
                && level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP);
    }
}
