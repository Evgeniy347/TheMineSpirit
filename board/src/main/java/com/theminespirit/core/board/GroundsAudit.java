package com.theminespirit.core.board;

import com.theminespirit.core.town.PlotLeveling;
import org.jspecify.annotations.Nullable;

public final class GroundsAudit {
    private GroundsAudit() {}

    public static boolean uneven(BoardCase boardCase, @Nullable PlotLeveling leveling) {
        return boardCase == BoardCase.CLEAR_GROUNDS
                && leveling != null
                && (leveling.cutVolume() > 0 || leveling.fillVolume() > 0);
    }
}
