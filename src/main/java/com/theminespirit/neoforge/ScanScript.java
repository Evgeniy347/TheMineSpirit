package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.ScanTuning;
import com.theminespirit.core.script.ScanParams;
import net.minecraft.core.BlockPos;

final class ScanScript implements ScriptInstance {
    private final SpiritEntity spirit;
    private final WorldScanner.Session session;
    private String failureReason;

    ScanScript(SpiritEntity spirit, ScanParams params) {
        this.spirit = spirit;
        BlockPos position = spirit.blockPosition();
        this.session = new WorldScanner.Session(spirit.level(), spirit.getUUID().toString(),
                new BlockCoord(position.getX(), position.getY(), position.getZ()), params.radius(), params.filter(),
                spirit.getInventory());
    }

    @Override
    public ScriptStatus tick() {
        if (spirit.isRemoved()) {
            failureReason = "дух исчез во время скана";
            return ScriptStatus.FAILED;
        }
        session.step(ScanTuning.SLICES_PER_TICK);
        if (!session.done()) {
            return ScriptStatus.RUNNING;
        }
        spirit.updateObservation(session.result().observation());
        return ScriptStatus.SUCCEEDED;
    }

    @Override
    public String failureReason() {
        return failureReason;
    }
}
