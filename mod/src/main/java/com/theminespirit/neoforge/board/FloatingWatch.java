package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class FloatingWatch {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static String lastError = "";

    private FloatingWatch() {}

    static void tick(ServerLevel level) {
        try {
            FloatingScanCycle.advance(level);
            lastError = "";
        } catch (RuntimeException error) {
            String message = String.valueOf(error.getMessage());
            if (!message.equals(lastError)) {
                lastError = message;
                LOGGER.warn("[graph] обход висящего не продвинулся: {}", message, error);
            }
        }
    }
}
