package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.MorePlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class MoreHazards {
    static final String COBBLESTONE = "minecraft:cobblestone";
    private static final int RADIUS = 3;
    private static final int MAX_CELLS = 8;

    enum Kind {
        FIRE,
        LAVA,
        FLOW
    }

    private MoreHazards() {}

    static boolean post(GraphPost post, GraphTickets.Ticket ticket, Kind kind) {
        List<BlockCoord> cells = cells(post, kind);
        if (cells.isEmpty()) {
            return false;
        }
        BoardCase boardCase = kind == Kind.FIRE ? BoardCase.CLEAR_GROUNDS : BoardCase.FILL_CRATER;
        for (int index = 0; index < cells.size(); index++) {
            BlockCoord cell = cells.get(index);
            String id = ticket.id() + ":hazard:" + index;
            if (post.open(id) || post.done(id)) {
                continue;
            }
            var plan = kind == Kind.FIRE
                    ? MorePlans.clearCells(List.of(cell))
                    : MorePlans.fillCells(List.of(cell), COBBLESTONE);
            FarmPost.submit(
                    post,
                    id,
                    boardCase,
                    ticket.task().priority(),
                    TicketTarget.at(cell).withPlan(plan),
                    Map.of());
        }
        return true;
    }

    private static List<BlockCoord> cells(GraphPost post, Kind kind) {
        Set<BlockCoord> seen = new LinkedHashSet<>();
        for (BlockCoord center : centers(post)) {
            seen.addAll(MoreScan.around(post.level, center, RADIUS, match(kind)));
        }
        List<BlockCoord> cells = new ArrayList<>(seen);
        return cells.size() > MAX_CELLS ? cells.subList(0, MAX_CELLS) : cells;
    }

    private static List<BlockCoord> centers(GraphPost post) {
        List<BlockCoord> centers = new ArrayList<>();
        BlockCoord home = post.scan.home();
        centers.add(home);
        BlockCoord anchor = post.scan.stage().anchor();
        if (!anchor.equals(home)) {
            centers.add(anchor);
        }
        return centers;
    }

    private static Predicate<BlockState> match(Kind kind) {
        return switch (kind) {
            case FIRE -> state -> state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE);
            case LAVA -> state -> state.is(Blocks.LAVA);
            case FLOW ->
                state -> !state.getFluidState().isEmpty()
                        && !state.getFluidState().isSource();
        };
    }
}
