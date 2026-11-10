package com.theminespirit.neoforge;

import com.theminespirit.core.mining.StandSpots;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.lease.LeaseRun;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

final class DigOutCleanup {
    private static final int LIMIT_TICKS = 120;
    private static final double ARRIVAL = 0.6;

    private final DigOutRestore restore;
    private final LeaseRun lease;
    private final DigOutRollback rollback;
    private final String failure;
    private final Approach approach;
    private int ticks;

    private DigOutCleanup(
            SpiritEntity spirit,
            DigOutRestore restore,
            LeaseRun lease,
            DigOutRollback rollback,
            String failure,
            Vec3 stand) {
        this.restore = restore;
        this.lease = lease;
        this.rollback = rollback;
        this.failure = failure;
        this.approach = new Approach(spirit);
        this.approach.begin(stand, ARRIVAL);
    }

    static @Nullable DigOutCleanup begin(
            SpiritEntity spirit,
            Level level,
            DigOutRestore restore,
            LeaseRun lease,
            DigOutRollback rollback,
            String failure) {
        BlockPos feet = spirit.blockPosition();
        BlockCoord body = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        if (spirit.isRemoved() || !restore.pendingOver(feet)) {
            return null;
        }
        Optional<BlockCoord> stand = StandSpots.of(body, new LevelSafetyView(level), body).stream()
                .filter(cell -> Math.abs(cell.y() - feet.getY()) <= 1)
                .filter(cell -> cell.x() != feet.getX() || cell.z() != feet.getZ())
                .findFirst();
        if (stand.isEmpty()) {
            return null;
        }
        BlockCoord at = stand.orElseThrow();
        return new DigOutCleanup(
                spirit, restore, lease, rollback, failure, new Vec3(at.x() + 0.5, at.y(), at.z() + 0.5));
    }

    boolean tick() {
        if (++ticks > LIMIT_TICKS) {
            approach.cancel();
            return true;
        }
        Approach.State state = approach.tick();
        if (state == Approach.State.MOVING) {
            return false;
        }
        if (state == Approach.State.STUCK || state == Approach.State.TIMEOUT) {
            approach.cancel();
        }
        return true;
    }

    String failure() {
        return failure;
    }

    void finish() {
        rollback.forgetReturned(restore.returnSpoil());
        lease.orphan();
    }

    void cancel() {
        approach.cancel();
    }
}
