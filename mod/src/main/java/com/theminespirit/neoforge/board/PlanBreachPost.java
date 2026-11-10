package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.GraphBlock;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphFailureMemory;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.building.EarthCover;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.policy.PlanBreachData;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;

final class PlanBreachPost {
    private PlanBreachPost() {}

    static boolean post(GraphPost post, GraphTickets.Ticket owner) {
        PlanBreachData data = PlanBreachData.get(post.level);
        boolean posted = false;
        for (Map.Entry<BlockPos, PlanBreachData.Breach> entry : data.open().entrySet()) {
            BlockPos pos = entry.getKey();
            BlockCoord at = new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
            String id = "world:plan_breach:" + WorldPost.key(at) + ":"
                    + entry.getValue().tick();
            BoardTicket repair = post.ticket(
                    id, BoardCase.REPAIR_BUILDING, Optional.empty(), BoardTuning.MAX_PRIORITY, owner.id(), Map.of());
            TicketTarget target = TicketTarget.at(at)
                    .withPlan(new ScriptCall(
                            "place_block",
                            Map.of(
                                    "block",
                                            EarthCover.restoreBlock(
                                                    entry.getValue().blockId()),
                                    "x", pos.getX(),
                                    "y", pos.getY(),
                                    "z", pos.getZ())));
            boolean occupied = !post.level.getBlockState(pos).isAir();
            boolean open = !occupied && post.open(id);
            boolean reachable = !occupied && !open && FloatingScan.reachable(post.level, at);
            String point = GraphNeeds.point(target);
            boolean known = !reachable
                    && post.service
                            .sources
                            .graphUnreachablePoints(post.now, GraphFailureMemory.DEFAULT_HALF_LIFE_TICKS)
                            .contains(point);
            switch (BreachPost.decide(new BreachPost.Facts(occupied, open, reachable, known))) {
                case CLEAR -> data.clear(pos);
                case SKIP -> posted = true;
                case POST -> {
                    post.submit(repair, target);
                    posted = true;
                }
                case REMEMBER_UNREACHABLE ->
                    post.service.sources.graphFailure(repair, target, new GraphBlock.Path(point), post.now);
            }
        }
        return posted;
    }
}
