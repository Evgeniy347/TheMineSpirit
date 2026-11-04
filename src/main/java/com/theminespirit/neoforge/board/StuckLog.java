package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.StuckSpirits;
import org.slf4j.Logger;

final class StuckLog {
    private static final Logger LOGGER = LogUtils.getLogger();

    private StuckLog() {}

    static void log(Board board) {
        for (StuckSpirits.Change change : board.stuck().drainChanges()) {
            LOGGER.info(
                    "[stuck] дух {}: метка «не двигается» {} с тика {} в {}: {}",
                    change.spirit(),
                    change.marked() ? "поставлена" : "снята",
                    change.tick(),
                    change.at() == null ? "-" : change.at(),
                    change.reason());
        }
    }
}
