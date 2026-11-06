package com.theminespirit.neoforge;

import com.theminespirit.core.building.FencePlan;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.BuildFenceParams;
import com.theminespirit.core.script.PlaceBlockParams;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

final class BuildFenceScript implements ScriptInstance {
    private static final String GATE_DONE_KEY = "gate_done";

    private final SpiritEntity spirit;
    private final FillSession fence;
    private final BlockCoord gate;
    private final String gateBlock;
    private ScriptInstance gateScript;
    private ScriptStatus outcome = ScriptStatus.RUNNING;
    private String failureReason;
    private int gateDone;

    BuildFenceScript(SpiritEntity spirit, BuildFenceParams params) {
        this.spirit = spirit;
        FencePlan plan = FencePlan.contour(params.points());
        this.fence = new FillSession(spirit, params.block(), plan.cells());
        this.gate = plan.gate();
        this.gateBlock = params.gate();
    }

    @Override
    public ScriptStatus tick() {
        if (outcome != ScriptStatus.RUNNING) {
            return outcome;
        }
        if (gateDone == 0 || gateBlock.isEmpty()) {
            FillSession.Outcome fenceStatus = fence.tick();
            if (fenceStatus == FillSession.Outcome.RUNNING) {
                return ScriptStatus.RUNNING;
            }
            if (fenceStatus == FillSession.Outcome.FAILED) {
                failureReason = fence.failureReason();
                outcome = ScriptStatus.FAILED;
                return outcome;
            }
            if (gateBlock.isEmpty()) {
                outcome = ScriptStatus.SUCCEEDED;
                return outcome;
            }
        }
        if (gateScript == null) {
            gateScript = new PlaceBlockScript(spirit, new PlaceBlockParams(gateBlock, gate.x(), gate.y(), gate.z()));
        }
        ScriptStatus gateStatus = gateScript.tick();
        if (gateStatus == ScriptStatus.FAILED) {
            failureReason = gateScript.failureReason();
            outcome = ScriptStatus.FAILED;
            return outcome;
        }
        if (gateStatus == ScriptStatus.SUCCEEDED) {
            gateDone = 1;
            outcome = ScriptStatus.SUCCEEDED;
        }
        return outcome;
    }

    @Override
    public Optional<Progress> progress() {
        Progress fenceProgress = fence.progress();
        int done = fenceProgress.done() + gateDone;
        int target = fenceProgress.target() + (gateBlock.isEmpty() ? 0 : 1);
        return Optional.of(new Progress(done, Math.max(target, 1)));
    }

    @Override
    public Optional<ScriptResult> result() {
        if (outcome != ScriptStatus.SUCCEEDED) {
            return Optional.empty();
        }
        Map<String, Object> data = new LinkedHashMap<>(fence.resultData());
        data.put("gate", gateBlock);
        data.put(GATE_DONE_KEY, gateDone);
        return Optional.of(new ScriptResult.Data(Map.copyOf(data)));
    }

    @Override
    public void cancel() {
        fence.cancel();
        if (gateScript != null) {
            gateScript.cancel();
        }
    }

    @Override
    public void suspend() {
        fence.suspend();
        if (gateScript != null) {
            gateScript.suspend();
        }
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    @Override
    public Map<String, Object> saveState() {
        Map<String, Object> state = new LinkedHashMap<>(fence.saveState());
        state.put(GATE_DONE_KEY, gateDone);
        return Map.copyOf(state);
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        fence.restoreState(state);
        if (state.get(GATE_DONE_KEY) instanceof Number number && number.intValue() > 0) {
            gateDone = 1;
            outcome = ScriptStatus.SUCCEEDED;
        }
    }
}
