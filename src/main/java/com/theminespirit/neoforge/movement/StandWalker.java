package com.theminespirit.neoforge.movement;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.movement.StandChoice;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.LevelSafetyView;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class StandWalker {
    public static final double STAND_ARRIVAL = 0.6;
    public static final int MAX_ATTEMPTS = 3;

    private final SpiritEntity spirit;
    private final LevelSafetyView safety;
    private final SafetyView withoutFailedStands;
    private final Set<BlockCoord> failedStands = new HashSet<>();
    private final Approach approach;
    private BlockCoord target;
    private BlockCoord stand;
    private int standChoices;

    public StandWalker(SpiritEntity spirit) {
        this.spirit = spirit;
        this.safety = new LevelSafetyView(spirit.level());
        this.withoutFailedStands = coord -> failedStands.contains(coord) ? Cell.SOLID : safety.cellAt(coord);
        this.approach = new Approach(spirit);
    }

    public boolean begin(BlockCoord goal) {
        target = goal;
        failedStands.clear();
        return startAttempt();
    }

    public Approach.State tick() {
        Approach.State state = approach.tick();
        if (state != Approach.State.STUCK && state != Approach.State.TIMEOUT) {
            return state;
        }
        failedStands.add(stand);
        if (failedStands.size() < MAX_ATTEMPTS && startAttempt()) {
            return Approach.State.MOVING;
        }
        return state;
    }

    public boolean inReach(BlockCoord goal) {
        Vec3 eye = spirit.position().add(0, StandChoice.EYE_HEIGHT, 0);
        double dx = eye.x - (goal.x() + 0.5);
        double dy = eye.y - (goal.y() + 0.5);
        double dz = eye.z - (goal.z() + 0.5);
        return dx * dx + dy * dy + dz * dz <= StandChoice.REACH_DISTANCE * StandChoice.REACH_DISTANCE;
    }

    public void cancel() {
        approach.cancel();
    }

    public void suspend() {
        approach.suspend();
    }

    public int standChoices() {
        return standChoices;
    }

    public int failedStands() {
        return failedStands.size();
    }

    private boolean startAttempt() {
        BlockPos feet = spirit.blockPosition();
        standChoices++;
        Optional<BlockCoord> chosen =
                StandChoice.choose(target, new BlockCoord(feet.getX(), feet.getY(), feet.getZ()), withoutFailedStands);
        if (chosen.isEmpty()) {
            return false;
        }
        stand = chosen.get();
        approach.begin(new Vec3(stand.x() + 0.5, stand.y(), stand.z() + 0.5), STAND_ARRIVAL);
        return true;
    }
}
