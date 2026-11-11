package com.theminespirit.neoforge;

import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.mining.ProgressWatchdog;
import com.theminespirit.core.observation.BlockCoord;
import org.jspecify.annotations.Nullable;

final class UndoStallWatch {
    private final ProgressWatchdog watchdog = new ProgressWatchdog(BoardTuning.undoNoProgressTicks());
    private @Nullable BlockCoord lastFeet;
    private int lastCursor = -1;

    boolean stalled(BlockCoord feet, int cursor) {
        watchdog.tick();
        boolean moved = lastFeet == null || !lastFeet.equals(feet);
        boolean advanced = cursor != lastCursor;
        lastFeet = feet;
        lastCursor = cursor;
        if (moved || advanced) {
            watchdog.progress();
        }
        return watchdog.stalled();
    }

    int limitTicks() {
        return watchdog.limitTicks();
    }
}
