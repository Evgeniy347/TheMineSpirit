package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildReservations;
import com.theminespirit.core.mining.api.BreakSafety;
import com.theminespirit.core.mining.api.MiningSafety;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class ClearReach {
    private static final double REACH_FROM_PLACE = BlockBreaker.REACH;

    private ClearReach() {}

    static List<BlockCoord> inReach(List<BlockCoord> cells, Vec3 eye) {
        double limit = (REACH_FROM_PLACE + 2.0) * (REACH_FROM_PLACE + 2.0);
        List<BlockCoord> out = new ArrayList<>();
        for (BlockCoord cell : cells) {
            double dx = cell.x() + 0.5 - eye.x;
            double dy = cell.y() + 0.5 - eye.y;
            double dz = cell.z() + 0.5 - eye.z;
            if (dx * dx + dy * dy + dz * dz <= limit) {
                out.add(cell);
            }
        }
        return out;
    }

    static @Nullable BlockCoord near(
            SpiritEntity spirit,
            Level level,
            List<BlockCoord> top,
            BlockPos feet,
            LevelSafetyView safety,
            BuildReservations reservations) {
        if (!spirit.onGround()) {
            return null;
        }
        Vec3 eye = spirit.getEyePosition();
        BlockCoord best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockCoord cell : top) {
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            double distance = eye.distanceTo(Vec3.atCenterOf(pos));
            if (distance > REACH_FROM_PLACE || distance >= bestDistance || pos.equals(feet.below())) {
                continue;
            }
            if (reservations.foreign(cell, spirit.getUUID())
                    || !(MiningSafety.canBreak(cell, safety) instanceof BreakSafety.Safe)) {
                continue;
            }
            BlockHitResult hit = level.clip(new ClipContext(
                    eye, Vec3.atCenterOf(pos), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, spirit));
            if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)) {
                best = cell;
                bestDistance = distance;
            }
        }
        return best;
    }
}
