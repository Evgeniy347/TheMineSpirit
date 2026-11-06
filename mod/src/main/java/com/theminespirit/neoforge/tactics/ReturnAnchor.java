package com.theminespirit.neoforge.tactics;

import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.mining.AnchorSpot;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.AnchorReturn;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.neoforge.LevelSafetyView;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Optional;
import net.minecraft.core.BlockPos;

final class ReturnAnchor {
    private ReturnAnchor() {}

    static boolean busy(SpiritEntity spirit) {
        return spirit.taskStatus().orElse(null) == RunStatus.RUNNING
                || spirit.currentTaskScript().isPresent();
    }

    static Optional<Point> stand(SpiritEntity spirit, Point anchor) {
        if (!AnchorReturn.allowed(busy(spirit))) {
            return Optional.empty();
        }
        BlockPos feet = spirit.blockPosition();
        BlockCoord from = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        BlockCoord at = new BlockCoord(
                (int) Math.floor(anchor.x()), (int) Math.floor(anchor.y()), (int) Math.floor(anchor.z()));
        return AnchorSpot.near(at, from, new LevelSafetyView(spirit.level()))
                .map(stand -> new Point(stand.x() + 0.5, stand.y(), stand.z() + 0.5));
    }
}
