package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

final class RescuePost {
    private static final String RESCUE = "rescue:";

    private RescuePost() {}

    static boolean post(GraphPost post, GraphTickets.Ticket ticket, GraphPlan.Task task) {
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
            String id = RESCUE + fact.id();
            if (post.open(id)) {
                posted = true;
                continue;
            }
            BlockPos feet = spirit.blockPosition();
            BlockCoord at = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
            post.submit(
                    post.ticket(
                            id,
                            BoardCase.RESCUE_SPIRIT,
                            Optional.of(spirit.getUUID()),
                            task.priority(),
                            ticket.id(),
                            Map.of()),
                    TicketTarget.at(at).withPlan(RescueTicket.plan(post.level, spirit, home)));
            posted = true;
        }
        return posted;
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
