package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.TaskRunner;
import com.theminespirit.core.plan.TaskNode;
import java.util.Optional;
import org.slf4j.Logger;

public final class SpiritBrain {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final SpiritEntity spirit;
    private TaskRunner runner;
    private RunStatus lastStatus;
    private String lastReason;
    private Throwable lastCause;

    public SpiritBrain(SpiritEntity spirit) {
        this.spirit = spirit;
    }

    public void start(TaskNode plan) {
        if (runner != null) {
            runner.cancel();
        }
        runner = TaskRunner.start(plan, SpiritScripts.registryFor(spirit), SpiritScripts.conditions());
        lastStatus = RunStatus.RUNNING;
        lastReason = null;
        lastCause = null;
    }

    public void cancel() {
        if (runner != null) {
            runner.cancel();
            recordResult();
            runner = null;
        }
    }

    public Optional<RunStatus> status() {
        if (runner != null) {
            return Optional.of(runner.status());
        }
        return Optional.ofNullable(lastStatus);
    }

    public Optional<String> failureReason() {
        return Optional.ofNullable(lastReason);
    }

    public Optional<Throwable> failureCause() {
        return Optional.ofNullable(lastCause);
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
            recordResult();
            runner = null;
        }
    }

    private void recordResult() {
        lastStatus = runner.status();
        lastReason = runner.failureReason().orElse(null);
        lastCause = runner.failureCause().orElse(null);
        if (lastStatus == RunStatus.FAILED) {
            LOGGER.warn("Дух {} провалил задачу: {}", spirit.getUUID(), lastReason, lastCause);
        }
    }
}
