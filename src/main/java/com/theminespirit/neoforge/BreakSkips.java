package com.theminespirit.neoforge;

import com.theminespirit.core.mining.BreakSkipTally;

final class BreakSkips {
    private BreakSkips() {}

    static BreakSkipTally.Reason reason(BlockBreaker.RefuseReason reason) {
        return switch (reason) {
            case OUT_OF_REACH -> BreakSkipTally.Reason.OUT_OF_REACH;
            case NO_SIGHT -> BreakSkipTally.Reason.NO_SIGHT;
            case CHUNK_NOT_LOADED -> BreakSkipTally.Reason.CHUNK_NOT_LOADED;
            case UNBREAKABLE -> BreakSkipTally.Reason.UNBREAKABLE;
            case AIR -> BreakSkipTally.Reason.VANISHED;
            case NEED_TOOL ->
                throw new IllegalArgumentException(
                        "Отказ «нужен инструмент» — не пропуск блока: его обрабатывает вызывающий");
        };
    }
}
