package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.audit.AuditGraphMapping;
import com.theminespirit.core.board.audit.Group;
import com.theminespirit.core.board.audit.GroupRef;
import com.theminespirit.core.board.audit.GroupRegistry;
import com.theminespirit.core.board.graph.AuditGraphFacts;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.plan.ScriptCall;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class AuditPost {
    private AuditPost() {}

    static boolean post(GraphPost post, GraphTickets.Ticket source) {
        if (post.open(source.id()) || post.done(source.id())) {
            return true;
        }
        GroupRegistry registry = TownAuditWatch.peekGroups(post.level);
        if (registry == null) {
            return false;
        }
        String key = "group:" + source.task().makes();
        Group group = registry.group(key);
        if (group == null || group.items().isEmpty()) {
            return false;
        }
        GroupRef ref = new GroupRef(group.key(), group.revision());
        Group snapshot = registry.snapshot(ref);
        if (snapshot == null) {
            return false;
        }
        Map<String, Integer> required = AuditGraphFacts.materials(snapshot);
        TicketTarget target = TicketTarget.at(snapshot.items().get(0).cell())
                .withGroup(ref)
                .withPlan(new ScriptCall("audit_group", Map.of("group", ref.id(), "revision", ref.revision())));
        Optional<UUID> owner = source.task().spirit().map(UUID::fromString);
        BoardTicket ticket = post.ticket(
                source.id(),
                AuditGraphMapping.caseOf(snapshot.kind()),
                owner,
                source.task().priority(),
                source.id(),
                required);
        BoardOutcome outcome = post.submit(ticket, target);
        if (outcome == BoardOutcome.ACCEPTED) {
            registry.issue(ticket.id(), snapshot.key());
        }
        return outcome == BoardOutcome.ACCEPTED;
    }
}
