package com.theminespirit.neoforge;

import com.theminespirit.core.building.ShelterPlan;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.BuildShelterParams;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;

final class BuildShelterScript implements ScriptInstance {
    private final FillSession session;
    private String failureReason;
    private ScriptStatus outcome = ScriptStatus.RUNNING;

    BuildShelterScript(SpiritEntity spirit, BuildShelterParams params) {
        Optional<String> material = ShelterMaterial.placeableBlockId(spirit.getInventory());
        if (material.isEmpty()) {
            this.session = null;
            this.failureReason = "нет блоков для укрытия: в инвентаре нет полного блока, который можно поставить";
            this.outcome = ScriptStatus.FAILED;
            return;
        }
        BlockPos feet = spirit.blockPosition();
        List<BlockCoord> cells = ShelterPlan.shelter(
                new BlockCoord(feet.getX() - 1, feet.getY(), feet.getZ() - 1),
                params.side(),
                ShelterPlan.WALL_HEIGHT,
                ShelterPlan.BLOCK_BUDGET);
        this.session = new FillSession(spirit, material.get(), cells);
    }

    @Override
    public ScriptStatus tick() {
        if (outcome != ScriptStatus.RUNNING) {
            return outcome;
        }
        outcome = switch (session.tick()) {
            case RUNNING -> ScriptStatus.RUNNING;
            case SUCCEEDED -> ScriptStatus.SUCCEEDED;
            case FAILED -> {
                failureReason = session.failureReason();
                yield ScriptStatus.FAILED;
            }
        };
        return outcome;
    }

    @Override
    public Optional<Progress> progress() {
        return session == null ? Optional.empty() : Optional.of(session.progress());
    }

    @Override
    public Optional<ScriptResult> result() {
        return outcome == ScriptStatus.SUCCEEDED
                ? Optional.of(new ScriptResult.Data(session.resultData()))
                : Optional.empty();
    }

    @Override
    public void cancel() {
        if (session != null) {
            session.cancel();
        }
    }

    @Override
    public void suspend() {
        if (session != null) {
            session.suspend();
        }
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    @Override
    public Map<String, Object> saveState() {
        return session == null ? Map.of() : session.saveState();
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        if (session != null) {
            session.restoreState(state);
        }
    }
}
