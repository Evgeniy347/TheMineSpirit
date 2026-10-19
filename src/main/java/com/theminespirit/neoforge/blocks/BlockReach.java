package com.theminespirit.neoforge.blocks;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.ReachApproach;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.core.BlockPos;

public final class BlockReach {
    private final SpiritEntity spirit;
    private final BlockCoord target;
    private final ReachApproach approach;
    private boolean arrived;
    private String refusal;

    public BlockReach(SpiritEntity spirit, BlockCoord target) {
        this.spirit = spirit;
        this.target = target;
        this.approach = new ReachApproach(spirit);
    }

    public boolean inReach() {
        if (arrived) {
            return true;
        }
        if (!approach.isActive()) {
            approach.begin(toPos(target));
        }
        switch (approach.tick()) {
            case MOVING -> {}
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
