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
import com.theminespirit.core.town.AccessPath;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class AccessPost {
    static final String NODE = "clear_access";
    private static final String PREFIX = "world:access:";

    private AccessPost() {}

    static boolean handles(String node) {
        return NODE.equals(node);
    }

    static boolean post(GraphPost post, GraphTickets.Ticket ticket) {
        if (post.openCount(PREFIX) > 0) {
            return true;
        }
        AccessPath.Plan plan = AccessScan.plan(post.level, post.scan.stage(), post.scan.home());
        if (plan.clear()) {
            return false;
        }
        BlockCoord first =
                plan.cut().isEmpty() ? plan.fill().get(0) : plan.cut().get(0);
        String id = PREFIX + first.x() + "," + first.y() + "," + first.z();
        post.submit(
                post.ticket(
                        id,
                        BoardCase.REPAIR_BUILDING,
                        Optional.empty(),
                        ticket.task().priority(),
                        ticket.id(),
                        post.items(ticket.task())),
                TicketTarget.at(first)
                        .withLeveling(LevelRepair.leveling(plan.fill(), plan.cut()))
                        .withPlan(steps(plan, first)));
        return true;
    }

    static TaskNode steps(AccessPath.Plan plan, BlockCoord first) {
        List<TaskNode> steps = new ArrayList<>();
        plan.cut().stream()
                .sorted(Comparator.comparingInt(BlockCoord::y).reversed())
                .forEach(cell -> steps.add(retried(new ScriptCall(
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
                                true)))));
        if (!plan.cut().isEmpty()) {
            steps.add(retried(new ScriptCall("collect_drops", Map.of("radius", CatalogLimits.DROP_RADIUS_MAX))));
        }
        if (!plan.fill().isEmpty()) {
            steps.add(LevelRepair.plan(
                    TicketTarget.at(first).withLeveling(LevelRepair.leveling(plan.fill(), List.of()))));
        }
        return new SequenceNode(steps);
    }

    private static TaskNode retried(ScriptCall call) {
        return new FallbackNode(List.of(call, new ScriptCall("wait", Map.of("ticks", CatalogLimits.WAIT_TICKS_MIN))));
    }
}
