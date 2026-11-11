package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.BreachAreas;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;

final class PlanBreachPost {
    static final String PREFIX = "world:plan_breach:";
    static final int AREA_LIMIT = 8;
    private static final String FILL = "fill";

    private PlanBreachPost() {}

    record Repair(BoardTicket ticket, TicketTarget target) {}

    static boolean post(GraphPost post, GraphTickets.Ticket owner) {
        PlanBreachData data = PlanBreachData.get(post.level);
        int room = WorldPost.room(post.openCount(PREFIX), WorldPost.BATCH);
        Map<BlockCoord, PlanBreachData.Breach> reachable = new LinkedHashMap<>();
        boolean posted = false;
        for (Map.Entry<BlockPos, PlanBreachData.Breach> entry : data.open().entrySet()) {
            BlockPos pos = entry.getKey();
            BlockCoord at = new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
            PlanBreachData.Breach breach = entry.getValue();
            String id = PREFIX + WorldPost.key(at) + ":" + breach.tick();
            boolean occupied = !post.level.getBlockState(pos).isAir();
            boolean open = !occupied && post.open(id);
            boolean canReach = !occupied && !open && FloatingScan.reachable(post.level, at);
            boolean known = !canReach && !occupied && !open && remembered(post, at);
            switch (BreachPost.decide(new BreachPost.Facts(occupied, open, canReach, known))) {
                case CLEAR -> data.clear(pos);
                case SKIP -> posted = true;
                case POST -> reachable.put(at, breach);
                case REMEMBER_UNREACHABLE -> rememberUnreachable(post, owner, at, breach, id);
            }
        }
        List<BreachAreas.Area> areas = BreachAreas.group(
                reachable.entrySet().stream()
                        .map(entry -> new BreachAreas.Cell(
                                entry.getKey(),
                                EarthCover.restoreBlock(entry.getValue().blockId())))
                        .toList(),
                AREA_LIMIT);
        List<Repair> ready = new ArrayList<>(areas.size());
        for (BreachAreas.Area area : areas) {
            PlanBreachData.Breach breach = reachable.get(area.min());
            String id = PREFIX + WorldPost.key(area.min()) + ":" + breach.tick();
            ready.add(new Repair(
                    post.ticket(
                            id,
                            BoardCase.REPAIR_BUILDING,
                            Optional.empty(),
                            BoardTuning.MAX_PRIORITY,
                            owner.id(),
                            Map.of(area.blockId(), area.cells())),
                    target(area)));
        }
        for (Repair repair : batch(ready, room)) {
            post.submit(repair.ticket(), repair.target());
            posted = true;
        }
        return posted;
    }

    private static boolean remembered(GraphPost post, BlockCoord at) {
        return post.service
                .sources
                .graphUnreachablePoints(post.now, GraphFailureMemory.DEFAULT_HALF_LIFE_TICKS)
                .contains(GraphNeeds.point(TicketTarget.at(at)));
    }

    private static void rememberUnreachable(
            GraphPost post, GraphTickets.Ticket owner, BlockCoord at, PlanBreachData.Breach breach, String id) {
        String block = EarthCover.restoreBlock(breach.blockId());
        BoardTicket repair = post.ticket(
                id,
                BoardCase.REPAIR_BUILDING,
                Optional.empty(),
                BoardTuning.MAX_PRIORITY,
                owner.id(),
                Map.of(block, 1));
        TicketTarget target = TicketTarget.at(at).withPlan(place(block, at));
        post.service.sources.graphFailure(repair, target, new GraphBlock.Path(GraphNeeds.point(target)), post.now);
    }

    static TicketTarget target(BreachAreas.Area area) {
        return TicketTarget.at(area.min())
                .withPlan(new ScriptCall(
                        FILL,
                        Map.of(
                                "block", area.blockId(),
                                "x1", area.x1(),
                                "y1", area.y1(),
                                "z1", area.z1(),
                                "x2", area.x2(),
                                "y2", area.y2(),
                                "z2", area.z2())));
    }

    private static ScriptCall place(String block, BlockCoord at) {
        return new ScriptCall("place_block", Map.of("block", block, "x", at.x(), "y", at.y(), "z", at.z()));
    }

    static List<Repair> batch(List<Repair> ready, int room) {
        return ready.stream()
                .sorted(Comparator.comparing((Repair repair) -> repair.ticket().id()))
                .limit(Math.max(0, room))
                .toList();
    }
}
