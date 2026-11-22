package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.ActivityTickets;
import com.theminespirit.core.board.BoardActivities;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.TaskNode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public final class ExternalTickets {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ExternalTickets() {}

    public static boolean attach(
            BoardService service,
            String id,
            @Nullable String title,
            UUID spiritId,
            BlockCoord spiritAt,
            TaskNode plan,
            long generation,
            long now) {
        String script = BoardActivities.scriptOf(plan);
        String targetLabel = BoardActivities.targetOf(plan);
        BlockCoord at = point(targetLabel, spiritAt);
        ActivityTickets.Request request = new ActivityTickets.Request(
                new ActivityTickets.Identity(id, BoardCase.ACTIVITY_EXTERNAL, BoardTicket.ALWAYS_CAUSE),
                new ActivityTickets.Crew(Optional.of(spiritId), List.of()),
                BoardRules.priorityOf(BoardCase.ACTIVITY_EXTERNAL),
                now,
                new ActivityTickets.Plan(
                        script == null ? "" : script, targetLabel == null ? "" : targetLabel, 0, plan));
        BoardTicket ticket = ActivityTickets.ticket(request).withTitle(title);
        TicketTarget goal = TicketTarget.at(at).withPlan(plan);
        BoardOutcome outcome = service.submit(ticket, goal);
        if (outcome == BoardOutcome.BACKOFF || outcome == BoardOutcome.STAGE_LOCKED) {
            LOGGER.warn("[board] REST-задача {} не принята доской: {}", id, outcome);
            return false;
        }
        if (outcome == BoardOutcome.DUPLICATE) {
            service.board().updatePending(ticket);
            service.targets.put(id, goal);
        }
        service.board().holds().release(id);
        BoardOutcome claimed = service.board().claim(id, spiritId, now);
        if (claimed != BoardOutcome.CLAIMED) {
            LOGGER.warn("[board] REST-задача {} не закреплена за духом: {}", id, claimed);
            return false;
        }
        service.dispatch().activityIssued(id, generation);
        service.persistBoard();
        return true;
    }

    public static boolean complete(BoardService service, String id) {
        boolean done = service.board().complete(id);
        if (done) {
            service.persistBoard();
        }
        return done;
    }

    public static boolean fail(BoardService service, String id, @Nullable String reason) {
        boolean failed = service.board().fail(id, service.stock(), reason);
        if (failed) {
            service.persistBoard();
        }
        return failed;
    }

    public static boolean cancel(BoardService service, String id) {
        boolean cancelled = service.board().cancel(id);
        if (cancelled) {
            service.persistBoard();
        }
        return cancelled;
    }

    private static BlockCoord point(@Nullable String label, BlockCoord fallback) {
        if (label != null) {
            String[] parts = label.split(",", -1);
            if (parts.length == 3) {
                try {
                    return new BlockCoord(
                            Integer.parseInt(parts[0].trim()),
                            Integer.parseInt(parts[1].trim()),
                            Integer.parseInt(parts[2].trim()));
                } catch (NumberFormatException notPoint) {
                    return fallback;
                }
            }
        }
        return fallback;
    }
}
