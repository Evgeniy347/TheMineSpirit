package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardTicket;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class GraphTicketFactory {
    record Request(
            String id,
            BoardCase boardCase,
            Optional<UUID> assignee,
            int priority,
            String cause,
            Map<String, Integer> required,
            List<String> modifiers) {}

    private GraphTicketFactory() {}

    static BoardTicket ticket(GraphPost post, Request request) {
        return new BoardTicket(
                request.id(),
                request.boardCase(),
                request.assignee(),
                Math.min(100, Math.max(0, request.priority())),
                post.now,
                1,
                request.cause(),
                List.of(),
                request.required(),
                request.modifiers());
    }

    static BoardTicket leveled(BoardTicket base, int level, List<String> dependsOn) {
        return new BoardTicket(
                base.id(),
                base.boardCase(),
                base.assignee(),
                base.priority(),
                base.createdAt(),
                level,
                base.cause(),
                dependsOn,
                base.requiredItems());
    }

    static BoardTicket dated(BoardTicket base, long createdAt) {
        return new BoardTicket(
                base.id(),
                base.boardCase(),
                base.assignee(),
                base.priority(),
                createdAt,
                base.level(),
                base.cause(),
                base.dependsOn(),
                base.requiredItems());
    }
}
