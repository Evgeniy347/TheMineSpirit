package com.theminespirit.neoforge;

import com.theminespirit.core.mining.BreakSkipTally;
import com.theminespirit.core.observation.BlockCoord;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class SkipLog {
    private SkipLog() {}

    static void skipped(Logger logger, SpiritEntity spirit, String tag, BreakSkipTally.Sample sample) {
        logger.info(
                "[{}] {}: блок {} пропущен — {}; кандидат стоянки {}",
                tag,
                spirit.getName().getString(),
                coord(sample.target()),
                sample.reason().title(),
                coord(sample.stand()));
    }

    private static String coord(@Nullable BlockCoord coord) {
        return coord == null ? "(неизвестна)" : "(" + coord.x() + ", " + coord.y() + ", " + coord.z() + ")";
    }
}
