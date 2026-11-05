package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.RescuePlans;
import com.theminespirit.core.board.StuckSpirits;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class GraphRescuePost {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String RESCUE = "rescue:";

    private GraphRescuePost() {}

    static boolean rescue(GraphPost post, GraphTickets.Ticket ticket, GraphPlan.Task task) {
        BlockCoord home = SettlementHome.of(post.spirits);
        boolean posted = false;
        for (GraphFacts.Spirit fact : post.facts.spirits().values()) {
            if (!fact.stuck()) {
                continue;
            }
            SpiritEntity spirit = entity(post, fact.id());
            if (spirit == null || spirit.isRemoved()) {
                continue;
            }
            UUID id = spirit.getUUID();
            String ticketId = RESCUE + id;
            Optional<StuckSpirits.Stuck> mark = post.service.board().stuck().of(id, post.now);
            if (mark.isEmpty()) {
                posted = true;
                continue;
            }
            long since = mark.orElseThrow().since();
            RescueTicket.Escape escape = RescueTicket.escape(post.level, spirit, home, noPath(mark));
            boolean helper = helper(post, fact.id());
            String refusal = refusal(post, ticketId, escape, helper);
            RescuePlans.Assignment assignment = RescuePlans.assign(id, since, refusal, helper);
            if (assignment.mode() == RescuePlans.Mode.IMPOSSIBLE) {
                say(post, spirit, since, "невозможен — " + assignment.why());
                posted = true;
                continue;
            }
            if (post.open(ticketId)) {
                posted = true;
                continue;
            }
            TaskNode plan =
                    assignment.mode() == RescuePlans.Mode.SELF ? escape.plan() : RescueTicket.helper(spirit, home);
            BlockPos feet = spirit.blockPosition();
            BlockCoord at = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
            BoardOutcome outcome = post.submit(
                    post.ticket(
                            ticketId,
                            BoardCase.RESCUE_SPIRIT,
                            assignment.assignee(),
                            task.priority(),
                            assignment.cause(),
                            Map.of()),
                    TicketTarget.at(at).withPlan(plan));
            if (outcome == BoardOutcome.ACCEPTED) {
                say(
                        post,
                        spirit,
                        since,
                        (assignment.mode() == RescuePlans.Mode.SELF ? "назначен сам — " : "назначен помощник — ")
                                + assignment.why());
            }
            posted = true;
        }
        return posted;
    }

    private static @Nullable String refusal(
            GraphPost post, String ticketId, RescueTicket.Escape escape, boolean helper) {
        if (!escape.self()) {
            return escape.why();
        }
        if (helper && post.service.board().ticketFailures(ticketId) > 0) {
            return "выход сам не удался (" + escape.why() + ")";
        }
        return null;
    }

    private static boolean noPath(Optional<StuckSpirits.Stuck> mark) {
        return mark.map(StuckSpirits.Stuck::noPath).orElse(false);
    }

    private static boolean helper(GraphPost post, String rescuee) {
        for (SpiritEntity other : post.spirits) {
            if (other.isRemoved() || other.getUUID().toString().equals(rescuee)) {
                continue;
            }
            GraphFacts.Spirit fact = post.facts.spirits().get(other.getUUID().toString());
            if (fact == null || !fact.stuck()) {
                return true;
            }
        }
        return false;
    }

    private static void say(GraphPost post, SpiritEntity spirit, long since, String text) {
        String key = "rescue:" + spirit.getUUID() + "@" + since;
        String line = "rescue:" + spirit.getUUID() + " для " + spirit.displayName() + ": " + text;
        if (post.service.sources.due(key, line, post.now)) {
            LOGGER.info("[rescue] {}", line);
        }
    }

    private static @Nullable SpiritEntity entity(GraphPost post, String id) {
        for (SpiritEntity spirit : post.spirits) {
            if (spirit.getUUID().toString().equals(id)) {
                return spirit;
            }
        }
        return null;
    }
}
