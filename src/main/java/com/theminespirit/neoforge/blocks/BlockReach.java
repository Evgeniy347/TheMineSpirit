package com.theminespirit.neoforge.blocks;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.Approach;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class BlockReach {
    private static final double REACH = 4.5;
    private static final double ARRIVAL = 1.25;

    private final SpiritEntity spirit;
    private final BlockCoord target;
    private final Approach approach;
    private boolean arrived;
    private String refusal;

    public BlockReach(SpiritEntity spirit, BlockCoord target) {
        this.spirit = spirit;
        this.target = target;
        this.approach = new Approach(spirit);
    }

    public boolean inReach() {
        if (arrived) {
            return true;
        }
        if (spirit.getEyePosition().distanceTo(Vec3.atCenterOf(toPos(target))) <= REACH) {
            arrived = true;
            return true;
        }
        if (!approach.isActive()) {
            approach.begin(new Vec3(target.x() + 0.5, target.y(), target.z() + 0.5), ARRIVAL);
        }
        switch (approach.tick()) {
            case MOVING -> {
            }
            case ARRIVED -> arrived = true;
            case STUCK, TIMEOUT -> refusal = approach.refusal();
        }
        return arrived;
    }

    public boolean stuck() {
        return refusal != null;
    }

    public String refusal() {
        return refusal;
    }

    public void cancel() {
        approach.cancel();
    }

    public void suspend() {
        approach.suspend();
    }

    public boolean arrived() {
        return arrived;
    }

    public void restoreArrived(boolean value) {
        arrived = value;
    }

    public static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }
}
