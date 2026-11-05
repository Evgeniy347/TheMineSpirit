package com.theminespirit.neoforge;

import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.script.AcquireParams;
import org.jspecify.annotations.Nullable;

final class HarvestRearm {
    private final SpiritEntity spirit;
    private @Nullable ScriptInstance refill;
    private @Nullable String pending;
    private @Nullable String failure;
    private boolean tried;

    HarvestRearm(SpiritEntity spirit) {
        this.spirit = spirit;
    }

    void broken(BlockBreaker breaker) {
        String suffix = breaker.brokenToolSuffix();
        if (suffix.isEmpty()) {
            return;
        }
        pending = suffix;
        tried = false;
        failure = null;
    }

    boolean tried() {
        return tried;
    }

    @Nullable
    String failure() {
        return failure;
    }

    boolean startPending(int mined, int count) {
        String suffix = pending;
        pending = null;
        if (suffix == null || mined >= count) {
            return false;
        }
        start(suffix);
        return true;
    }

    void start(String suffix) {
        tried = true;
        failure = null;
        try {
            refill = SpiritScripts.acquire(spirit, new AcquireParams("*" + suffix, 1));
        } catch (RuntimeException error) {
            failure = error.getMessage();
        }
    }

    boolean tick() {
        if (refill == null) {
            return true;
        }
        ScriptStatus status = refill.tick();
        if (status == ScriptStatus.RUNNING) {
            return false;
        }
        ScriptInstance done = refill;
        refill = null;
        if (status != ScriptStatus.SUCCEEDED) {
            failure = done.failureReason();
        }
        return true;
    }

    void cancel() {
        if (refill != null) {
            refill.cancel();
        }
    }

    void suspend() {
        if (refill != null) {
            refill.suspend();
        }
    }
}
