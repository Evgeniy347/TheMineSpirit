package com.theminespirit.neoforge.rest;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.RestTickets;
import com.theminespirit.core.task.TaskState;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.BoardService;
import com.theminespirit.neoforge.board.ExternalTickets;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class RestBoardSync {
    private static final Logger LOGGER = LogUtils.getLogger();

    private RestBoardSync() {}

    public static void close(ServerLevel level, String taskId, TaskState state, @Nullable String reason) {
        if (!state.isTerminal()) {
            return;
        }
        Optional<BoardService> service = BoardRegistry.peek(level);
        if (service.isEmpty()) {
            return;
        }
        String id = RestTickets.idFor(taskId);
        try {
            switch (state) {
                case SUCCEEDED -> ExternalTickets.complete(service.get(), id);
                case FAILED -> ExternalTickets.fail(service.get(), id, reason);
                default -> ExternalTickets.cancel(service.get(), id);
            }
        } catch (RuntimeException failure) {
            LOGGER.warn("[board] REST-задача {} не закрыта на доске: {}", taskId, failure.toString());
        }
    }
}
