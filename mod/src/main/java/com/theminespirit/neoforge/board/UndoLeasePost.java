package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphEvent;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.board.lease.Lease;
import com.theminespirit.core.board.lease.LeaseStatus;
import com.theminespirit.core.board.lease.LeaseUndo;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.lease.LeaseData;
import java.util.Map;
import java.util.Optional;

final class UndoLeasePost {
    private UndoLeasePost() {}

    static boolean handles(String node) {
        return node.startsWith(GraphEvent.UNDO_PREFIX);
    }

    static boolean post(GraphPost post, GraphTickets.Ticket ticket) {
        String id = ticket.node().substring(GraphEvent.UNDO_PREFIX.length());
        Optional<Lease> found = LeaseData.get(post.level).book().find(id);
        if (found.isEmpty() || found.orElseThrow().status() != LeaseStatus.ORPHANED) {
            return true;
        }
        Lease lease = found.orElseThrow();
        if (lease.cells().isEmpty()) {
            return true;
        }
        BlockCoord at = LeaseUndo.order(lease).get(0).pos();
        Optional<BlockCoord> stand = GraphTargets.standNear(post.level, at);
        if (stand.isEmpty()) {
            return false;
        }
        post.submit(
                ticket,
                BoardCase.ACTIVITY_BUILD,
                TicketTarget.at(stand.orElseThrow()).withPlan(new ScriptCall("undo_lease", Map.of("id", id))),
                Map.of());
        return true;
    }
}
