package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.engine.ScriptInstance;
import java.util.Comparator;
import java.util.Map;
import org.slf4j.Logger;

final class FillReport {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final FillSession host;

    FillReport(FillSession host) {
        this.host = host;
    }

    FillSession.Outcome outcome() {
        return host.outcome;
    }

    String failureReason() {
        return host.failureReason;
    }

    FillSession.Outcome finish() {
        if (host.outcome != FillSession.Outcome.RUNNING) {
            return host.outcome;
        }
        host.releaseAll();
        if (host.tally.failedCount() > 0) {
            return host.outcome;
        }
        if (host.terrain.complete()) {
            host.tally.supplies(host.tally.placedCount(), 0);
            host.outcome = FillSession.Outcome.SUCCEEDED;
            return host.outcome;
        }
        return fail(host.tally.summary(host.region.size()));
    }

    FillSession.Outcome fail(String reason) {
        LOGGER.info(
                "[fill] {}: {} клеток {} от {} не засыпаны: {}",
                host.spirit.getName().getString(),
                host.blockId(),
                host.region.size(),
                host.region.stream().min(Comparator.naturalOrder()).orElse(null),
                reason);
        host.failureReason = reason;
        host.outcome = FillSession.Outcome.FAILED;
        host.cancel();
        return host.outcome;
    }

    ScriptInstance.Progress progress() {
        int total = host.order.size();
        return new ScriptInstance.Progress(Math.min(host.cursor, total), Math.max(total, 1));
    }

    int placed() {
        return host.tally.placedCount();
    }

    int skipped() {
        return host.tally.skippedCount();
    }

    Map<String, Object> resultData() {
        return host.tally.data();
    }

    int blockReads() {
        return host.terrain.blockReads();
    }

    Map<String, Object> saveState() {
        return host.tally.save(host.outcome == FillSession.Outcome.RUNNING);
    }

    void restoreState(Map<String, Object> saved) {
        host.tally.restore(saved, host.blockId());
    }
}
