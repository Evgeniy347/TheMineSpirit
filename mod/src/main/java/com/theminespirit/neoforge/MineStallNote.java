package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.StallWatch;
import org.slf4j.Logger;

final class MineStallNote {
    private static final Logger LOGGER = LogUtils.getLogger();

    private MineStallNote() {}

    static void warn(String state) {
        LOGGER.warn("Шахта: {} — {} тиков без прогресса", state, StallWatch.REPORT_TICKS);
    }
}
