package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.LevelRepair;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CatalogLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class AccessPost {
    static final String NODE = "clear_access";
    private static final String PREFIX = "world:access:";
    private static final int BATCH = 16;

    private AccessPost() {}

    static boolean handles(String node) {
        return NODE.equals(node);
    }

    static boolean post(GraphPost post, GraphTickets.Ticket ticket) {
        if (post.openCount(PREFIX) > 0) {
            return true;
        }
        List<BlockCoord> cut = AccessScan.cut(post.level, post.service, post.scan.stage(), post.scan.home());
        if (cut.isEmpty()) {
            return false;
        }
        List<BlockCoord> batch = cut.subList(0, Math.min(BATCH, cut.size()));
        BlockCoord first = batch.get(0);
        post.submit(
                post.ticket(
                        PREFIX + first.x() + "," + first.y() + "," + first.z(),
                        BoardCase.REPAIR_BUILDING,
                        Optional.empty(),
                        ticket.task().priority(),
                        ticket.id(),
                        post.items(ticket.task())),
                TicketTarget.at(first)
                        .withLeveling(LevelRepair.leveling(List.of(), batch))
                        .withPlan(steps(batch)));
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
