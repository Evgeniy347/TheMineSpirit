package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.pickup.PickupTuning;
import com.theminespirit.core.script.CollectDropsParams;
import net.minecraft.core.BlockPos;

final class CollectDropsScript implements ScriptInstance {
    private final DropCollector collector;

    CollectDropsScript(SpiritEntity spirit, CollectDropsParams params) {
        BlockPos position = spirit.blockPosition();
        this.collector = new DropCollector(
                spirit,
                params.items(),
                new BlockCoord(position.getX(), position.getY(), position.getZ()),
                params.radius(),
                PickupTuning.DEFAULT,
                params.overflow());
    }

    @Override
    public ScriptStatus tick() {
        return switch (collector.tick()) {
            case RUNNING -> ScriptStatus.RUNNING;
            case DONE -> ScriptStatus.SUCCEEDED;
            case FAILED -> ScriptStatus.FAILED;
        };
    }

    @Override
    public void cancel() {
        collector.cancel();
    }

    @Override
    public void suspend() {
        collector.suspend();
    }

    @Override
    public String failureReason() {
        return collector.failureReason();
    }
}
