package com.theminespirit.neoforge.policy;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BodyFlags;
import com.theminespirit.core.policy.LocalSurroundings;
import com.theminespirit.neoforge.LevelSafetyView;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritView;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;

final class SurroundingsReader {

    private SurroundingsReader() {}

    static LocalSurroundings read(
            SpiritView spirit, LevelSafetyView view, BodyFlags flags, Optional<BlockCoord> water) {
        BlockPos feet = spirit.blockPosition();
        return LocalSurroundings.read(new BlockCoord(feet.getX(), feet.getY(), feet.getZ()), view, flags, water);
    }

    record WaterSearch(Optional<BlockCoord> cell, int reads) {}

    static WaterSearch nearestWater(SpiritView spirit) {
        Level level = spirit.level();
        BlockPos feet = spirit.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int reads = 0;
        for (int dx = -SpiritConfig.waterSearchRadius(); dx <= SpiritConfig.waterSearchRadius(); dx++) {
            for (int dz = -SpiritConfig.waterSearchRadius(); dz <= SpiritConfig.waterSearchRadius(); dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos pos = feet.offset(dx, dy, dz);
                    reads++;
                    double distance = pos.distSqr(feet);
                    if (distance < bestDistance && level.getFluidState(pos).is(FluidTags.WATER)) {
                        best = pos;
                        bestDistance = distance;
                    }
                }
            }
        }
        Optional<BlockCoord> cell =
                best == null ? Optional.empty() : Optional.of(new BlockCoord(best.getX(), best.getY(), best.getZ()));
        return new WaterSearch(cell, reads);
    }
}
