package com.theminespirit.neoforge;

import com.theminespirit.core.mining.BreakSkipTally;
import com.theminespirit.core.mining.ProgressWatchdog;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.CatalogLimits;
import java.util.Optional;

final class HarvestCounters {
    private final int count;
    private final ProgressWatchdog watchdog = new ProgressWatchdog(CatalogLimits.HARVEST_NO_PROGRESS_TICKS);
    private final ProgressWatchdog veinMining = new ProgressWatchdog(CatalogLimits.HARVEST_NO_PROGRESS_TICKS);
    private final BreakSkipTally breakSkips = new BreakSkipTally();

    private int mined;
    private int unsafe;
    private int unreachable;
    private int noTool;
    private int protectedSkipped;

    HarvestCounters(int count) {
        this.count = count;
    }

    int mined() {
        return mined;
    }

    void restoreMined(int saved) {
        mined = Math.max(mined, saved);
    }

    void addMined() {
        mined++;
    }

    void addUnsafe() {
        unsafe++;
    }

    void addUnsafe(int amount) {
        unsafe += amount;
    }

    void addUnreachable() {
        unreachable++;
    }

    void addUnreachable(int amount) {
        unreachable += amount;
    }

    void addNoTool() {
        noTool++;
    }

    void addProtected() {
        protectedSkipped++;
    }

    void recordNoStandSkips(int amount) {
        breakSkips.record(BreakSkipTally.Reason.NO_STAND, amount);
    }

    Optional<BreakSkipTally.Sample> recordSkip(BreakSkipTally.Reason reason, BlockCoord target, BlockCoord stand) {
        return breakSkips.record(reason, target, stand);
    }

    void tickWatchdogs(boolean vein) {
        watchdog.tick();
        if (vein && mined == 0) {
            veinMining.tick();
        }
    }

    int stallLimit(boolean vein) {
        ProgressWatchdog stalled =
                watchdog.stalled() ? watchdog : vein && mined == 0 && veinMining.stalled() ? veinMining : null;
        return stalled == null ? -1 : stalled.limitTicks();
    }

    void progress() {
        watchdog.progress();
        veinMining.progress();
    }

    void progressWatchdog() {
        watchdog.progress();
    }

    HarvestReasonText.Tally tally() {
        return new HarvestReasonText.Tally(mined, count, unsafe, noTool, protectedSkipped, unreachable, breakSkips);
    }
}
