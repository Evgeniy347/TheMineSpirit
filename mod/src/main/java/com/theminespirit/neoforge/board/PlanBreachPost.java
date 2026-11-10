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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;

final class PlanBreachPost {
    static final String PREFIX = "world:plan_breach:";

    private PlanBreachPost() {}

    record Repair(BoardTicket ticket, TicketTarget target) {}

    static boolean post(GraphPost post, GraphTickets.Ticket owner) {
        PlanBreachData data = PlanBreachData.get(post.level);
        int room = WorldPost.room(post.openCount(PREFIX), WorldPost.BATCH);
        List<Repair> ready = new ArrayList<>();
        boolean posted = false;
        for (Map.Entry<BlockPos, PlanBreachData.Breach> entry : data.open().entrySet()) {
            BlockPos pos = entry.getKey();
            BlockCoord at = new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
            String id = PREFIX + WorldPost.key(at) + ":" + entry.getValue().tick();
            String block = EarthCover.restoreBlock(entry.getValue().blockId());
            BoardTicket repair = post.ticket(
                    id,
                    BoardCase.REPAIR_BUILDING,
                    Optional.empty(),
                    BoardTuning.MAX_PRIORITY,
                    owner.id(),
                    Map.of(block, 1));
            TicketTarget target = TicketTarget.at(at)
                    .withPlan(new ScriptCall(
                            "place_block",
                            Map.of(
                                    "block", block,
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
                case POST -> ready.add(new Repair(repair, target));
                case REMEMBER_UNREACHABLE ->
                    post.service.sources.graphFailure(repair, target, new GraphBlock.Path(point), post.now);
            }
        }
        for (Repair repair : batch(ready, room)) {
            post.submit(repair.ticket(), repair.target());
            posted = true;
        }
        return posted;
    }

    static List<Repair> batch(List<Repair> ready, int room) {
        return ready.stream()
                .sorted(Comparator.comparing((Repair repair) -> repair.ticket().id()))
                .limit(Math.max(0, room))
                .toList();
    }
}
