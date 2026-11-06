package com.theminespirit.neoforge.crater;

import com.theminespirit.core.building.BuildMaterials;
import com.theminespirit.core.building.Crater;
import com.theminespirit.core.building.CraterPlan;
import com.theminespirit.core.engine.ConditionRegistry;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.engine.TaskRunner;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.FillSession;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.SpiritScripts;
import com.theminespirit.neoforge.inventory.SpiritBuildSupplies;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class FillCraterScript implements ScriptInstance {
    static final String FILL_BLOCK = "minecraft:dirt";

    private final SpiritEntity spirit;
    private final Crater crater;
    private final CraterPlan.Plan plan;
    private final FillSession session;

    private TaskRunner withdrawRunner;
    private boolean withdrawPlanned;
    private ScriptStatus outcome = ScriptStatus.RUNNING;
    private String failureReason;

    public FillCraterScript(SpiritEntity spirit, Crater crater) {
        this.spirit = spirit;
        this.crater = crater;
        this.plan = CraterPlan.plan(new LevelCraterSurface(spirit.level(), spirit, crater));
        this.session = new FillSession(spirit, FILL_BLOCK, plan.cells());
    }

    Crater crater() {
        return crater;
    }

    CraterPlan.Plan plan() {
        return plan;
    }

    @Override
    public ScriptStatus tick() {
        if (outcome != ScriptStatus.RUNNING) {
            return outcome;
        }
        if (withdrawRunner == null && !withdrawPlanned) {
            withdrawPlanned = true;
            withdrawRunner = startWithdraw();
        }
        if (withdrawRunner != null) {
            ScriptStatus withdraw = tickWithdraw();
            if (withdraw == ScriptStatus.RUNNING) {
                return ScriptStatus.RUNNING;
            }
            withdrawRunner = null;
        }
        return switch (session.tick()) {
            case RUNNING -> ScriptStatus.RUNNING;
            case SUCCEEDED -> {
                outcome = ScriptStatus.SUCCEEDED;
                yield outcome;
            }
            case FAILED -> {
                failureReason = session.failureReason();
                outcome = ScriptStatus.FAILED;
                yield outcome;
            }
        };
    }

    private TaskRunner startWithdraw() {
        BuildMaterials.Plan supplies =
                BuildMaterials.plan(FILL_BLOCK, Math.max(plan.blockCount(), 1), new SpiritBuildSupplies(spirit));
        if (supplies.steps().isEmpty()) {
            return null;
        }
        List<TaskNode> steps = new ArrayList<>(supplies.steps());
        TaskNode node = new SequenceNode(steps);
        return TaskRunner.start(node, SpiritScripts.registryFor(spirit), ConditionRegistry.of(Map.of()));
    }

    private ScriptStatus tickWithdraw() {
        return switch (withdrawRunner.tick()) {
            case RUNNING -> ScriptStatus.RUNNING;
            case SUCCEEDED -> ScriptStatus.SUCCEEDED;
            case FAILED, CANCELLED -> ScriptStatus.SUCCEEDED;
        };
    }

    @Override
    public Optional<Progress> progress() {
        return Optional.of(session.progress());
    }

    @Override
    public Optional<ScriptResult> result() {
        return outcome == ScriptStatus.SUCCEEDED
                ? Optional.of(new ScriptResult.Data(session.resultData()))
                : Optional.empty();
    }

    @Override
    public void cancel() {
        session.cancel();
        if (withdrawRunner != null) {
            withdrawRunner.cancel();
        }
    }

    @Override
    public void suspend() {
        session.suspend();
        if (withdrawRunner != null) {
            withdrawRunner.suspend();
        }
    }

    @Override
    public void resume() {
        if (withdrawRunner != null) {
            withdrawRunner.resume();
        }
    }

    @Override
    public String failureReason() {
        return failureReason;
    }

    @Override
    public Map<String, Object> saveState() {
        return session.saveState();
    }

    @Override
    public void restoreState(Map<String, Object> state) {
        session.restoreState(state);
        withdrawPlanned = true;
    }
}
