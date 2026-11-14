package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.log.Perf;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;

final class PerfReport {
    static final int WINDOW_TICKS = 1200;
    static final int TOP = 30;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static int lastTick = -1;

    private PerfReport() {}

    static void tick(int serverTick) {
        if (lastTick >= 0 && serverTick - lastTick < WINDOW_TICKS && serverTick >= lastTick) {
            return;
        }
        boolean first = lastTick < 0;
        lastTick = serverTick;
        List<Perf.Line> lines = Perf.report();
        if (first || lines.isEmpty()) {
            return;
        }
        LOGGER.info(
                "[perf] за {} тиков (топ {} по сумме): {}",
                WINDOW_TICKS,
                TOP,
                lines.stream().limit(TOP).map(Perf.Line::text).collect(Collectors.joining("; ")));
    }
}
