package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.LevelRepair;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.AccessPath;
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
                TicketTarget.at(first).withLeveling(LevelRepair.leveling(plan.fill(), plan.cut())));
        return true;
    }
}
