package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BlockedCells;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.LevelRepair;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.api.CatalogLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class AccessPost {
    static final String NODE = "clear_access";
    static final String PREFIX = "world:access:";
    static final String CHEST_PREFIX = "world:chestspace:";
    private static final int CHEST_BOOST = 5;
    private static final int BATCH = 16;

    private AccessPost() {}

    static boolean handles(String node) {
        return NODE.equals(node);
    }

    static boolean post(GraphPost post, GraphTickets.Ticket ticket) {
        AccessScan.Cut cut = AccessScan.cut(post.level, post.service, post.scan.stage(), post.scan.home());
        if (cut.cells().isEmpty()) {
            return false;
        }
        if (cut.chestBlocked()) {
            if (post.openCount(CHEST_PREFIX) > 0) {
                return true;
            }
            return submit(
                    post,
                    ticket,
                    CHEST_PREFIX,
                    cut.chest(),
                    Math.min(100, BoardRules.priorityOf(BoardCase.REPAIR_BUILDING) + CHEST_BOOST));
        }
        if (post.openCount(PREFIX) > 0) {
            return true;
        }
        return submit(post, ticket, PREFIX, cut.path(), ticket.task().priority());
    }

    private static boolean submit(
            GraphPost post, GraphTickets.Ticket ticket, String prefix, List<BlockedCells.Mark> marks, int priority) {
        List<BlockedCells.Mark> batch = marks.subList(0, Math.min(BATCH, marks.size()));
        List<BlockCoord> cells = new ArrayList<>(batch.size());
        for (BlockedCells.Mark mark : batch) {
            cells.add(mark.cell());
        }
        BlockCoord first = cells.get(0);
        BlockedCells.Mark head = batch.get(0);
        post.submit(
                post.ticket(
                        prefix + first.x() + "," + first.y() + "," + first.z() + ":" + head.since(),
                        BoardCase.REPAIR_BUILDING,
                        Optional.empty(),
                        priority,
                        ticket.id(),
                        post.items(ticket.task())),
                TicketTarget.at(first)
                        .withLeveling(LevelRepair.leveling(List.of(), cells))
                        .withPlan(steps(cells)));
        return true;
    }

    static TaskNode steps(List<BlockCoord> cut) {
        List<TaskNode> steps = new ArrayList<>();
        for (BlockCoord cell : cut) {
            steps.add(retried(new ScriptCall(
                    "clear_area",
                    Map.of(
                            "x1",
                            cell.x(),
                            "y1",
                            cell.y(),
                            "z1",
                            cell.z(),
                            "x2",
                            cell.x(),
                            "y2",
                            cell.y(),
                            "z2",
                            cell.z(),
                            "demolish",
                            true))));
        }
        steps.add(retried(new ScriptCall("collect_drops", Map.of("radius", CatalogLimits.DROP_RADIUS_MAX))));
        return new SequenceNode(steps);
    }

    private static TaskNode retried(ScriptCall call) {
        return new FallbackNode(List.of(call, new ScriptCall("wait", Map.of("ticks", CatalogLimits.WAIT_TICKS_MIN))));
    }
}
