package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.engine.RunProgress;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.TaskRunner;
import com.theminespirit.core.engine.TaskSnapshot;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.policy.PolicyExemptions;
import com.theminespirit.core.task.SpiritReading;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;

public final class SpiritBrain {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final AtomicLong GENERATIONS = new AtomicLong(SpiritReading.UNKNOWN_GENERATION);

    private final SpiritEntity spirit;
    private TaskRunner runner;
    private RunStatus lastStatus;
    private String lastReason;
    private Throwable lastCause;
    private String suspendReason;
    private RunProgress lastFailedAt;
    private long generation = SpiritReading.UNKNOWN_GENERATION;

    public SpiritBrain(SpiritEntity spirit) {
        this.spirit = spirit;
    }

    public void start(TaskNode plan) {
        if (runner != null) {
            runner.cancel();
        }
        runner = TaskRunner.start(plan, SpiritScripts.registryFor(spirit), SpiritScripts.conditions());
        generation = GENERATIONS.incrementAndGet();
        suspendReason = null;
        lastStatus = RunStatus.RUNNING;
        lastReason = null;
        lastCause = null;
        lastFailedAt = null;
    }

    public long generation() {
        return generation;
    }

    public void suspend(String reason) {
        if (runner != null && runner.status() == RunStatus.RUNNING && !runner.isSuspended()) {
            runner.suspend();
            suspendReason = reason;
        }
    }

    public void resume() {
        if (runner != null) {
            runner.resume();
        }
        suspendReason = null;
    }

    public boolean isSuspended() {
        return runner != null && runner.isSuspended();
    }

    public Optional<String> suspendReason() {
        return isSuspended() ? Optional.ofNullable(suspendReason) : Optional.empty();
    }

    public PolicyExemptions policyExemptions() {
        return runner == null ? PolicyExemptions.empty() : runner.policyExemptions();
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

    public Optional<TaskNode> plan() {
        return runner == null ? Optional.empty() : Optional.of(runner.plan());
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

    public Optional<RunProgress> failedAt() {
        return Optional.ofNullable(lastFailedAt);
    }

    public Optional<RunProgress> progress() {
        return runner == null ? Optional.empty() : runner.progress();
    }

    public Optional<TaskSnapshot> snapshot() {
        if (runner != null) {
            return runner.snapshot();
        }
        if (lastStatus != null) {
            return Optional.of(new TaskSnapshot(TaskSnapshot.VERSION, lastStatus, lastReason, null, null));
        }
        return Optional.empty();
    }

    public void restore(TaskSnapshot snapshot) {
        runner = TaskRunner.restore(snapshot, SpiritScripts.registryFor(spirit), SpiritScripts.conditions());
        generation = SpiritReading.UNKNOWN_GENERATION;
        lastStatus = runner.status();
        lastFailedAt = null;
        if (lastStatus != RunStatus.RUNNING) {
            lastReason = runner.failureReason().orElse(null);
            lastCause = runner.failureCause().orElse(null);
        } else {
            lastReason = null;
            lastCause = null;
        }
    }

    public void failRestore(String reason) {
        runner = null;
        generation = SpiritReading.UNKNOWN_GENERATION;
        lastStatus = RunStatus.FAILED;
        lastReason = reason;
        lastCause = null;
        lastFailedAt = null;
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
        lastFailedAt = runner.failedAt().orElse(null);
        if (lastStatus == RunStatus.FAILED) {
            LOGGER.warn("Дух {} провалил задачу: {}", spirit.getUUID(), lastReason, lastCause);
        }
    }
}
