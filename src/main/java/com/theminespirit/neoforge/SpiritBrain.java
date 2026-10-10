package com.theminespirit.neoforge;

import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.TaskRunner;
import com.theminespirit.core.plan.TaskNode;
import java.util.Optional;

public final class SpiritBrain {
    private final SpiritEntity spirit;
    private TaskRunner runner;

    public SpiritBrain(SpiritEntity spirit) {
        this.spirit = spirit;
    }

    public void start(TaskNode plan) {
        stopNavigation();
        runner = TaskRunner.start(plan, SpiritScripts.registryFor(spirit), SpiritScripts.conditions());
    }

    public void cancel() {
        if (runner != null) {
            runner.cancel();
            runner = null;
        }
        stopNavigation();
    }

    public Optional<RunStatus> status() {
        return runner == null ? Optional.empty() : Optional.of(runner.status());
    }

    public Optional<String> currentScript() {
        return runner == null ? Optional.empty() : runner.currentScript();
    }

    public void tick() {
        if (runner == null) {
            return;
        }
        RunStatus status = runner.tick();
        if (status != RunStatus.RUNNING) {
            runner = null;
            stopNavigation();
        }
    }

    private void stopNavigation() {
        spirit.getNavigation().stop();
    }
}