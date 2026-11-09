package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.StuckSpirits;
import com.theminespirit.core.log.LogCause;
import com.theminespirit.neoforge.log.SpiritWorldLog;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class StuckLog {
    private static final Logger LOGGER = LogUtils.getLogger();

    private StuckLog() {}

    static void log(ServerLevel level, Board board) {
        for (StuckSpirits.Change change : board.stuck().drainChanges()) {
            LOGGER.info(
                    "[stuck] дух {}: метка «не двигается» {} с тика {} в {}: {}",
                    change.spirit(),
                    change.marked() ? "поставлена" : "снята",
                    change.tick(),
                    change.at() == null ? "-" : change.at(),
                    change.reason());
            SpiritWorldLog.stuck(
                    level,
                    change.spirit(),
                    change.marked() ? "mark" : "clear",
                    change.at(),
                    "",
                    null,
                    change.reason(),
                    LogCause.STUCK,
                    change.tick());
        }
    }
}
