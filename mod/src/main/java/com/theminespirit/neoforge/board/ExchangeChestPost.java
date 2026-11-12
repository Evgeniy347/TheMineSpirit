package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.CaseMaterials;
import com.theminespirit.core.board.ChestPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.TaskNode;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

final class ExchangeChestPost {
    static final String NODE = "exchange_chest";
    private static final int REACH = 2;

    private ExchangeChestPost() {}

    static boolean building(GraphPost post, GraphTickets.Ticket ticket) {
        if (!NODE.equals(ticket.node())) {
            return false;
        }
        if (post.open(ticket.id())) {
            return true;
        }
        Optional<BlockCoord> cell = cell(post.level, post.craftPoint());
        if (cell.isEmpty()) {
            return false;
        }
        TaskNode plan = ChestPlans.placeOne(cell.orElseThrow(), post.scan.wood());
        post.submit(
                post.ticket(
                        ticket.id(),
                        BoardCase.STOCK_CHESTS,
                        Optional.empty(),
                        ticket.task().priority(),
                        ticket.id(),
                        CaseMaterials.need(plan, FirstStepsRunner.materialSource(post.level))),
                TicketTarget.at(cell.orElseThrow()).withPlan(plan));
        return true;
    }

    static Optional<BlockCoord> cell(ServerLevel level, BlockCoord table) {
        BlockPos centre = new BlockPos(table.x(), table.y(), table.z());
        for (int distance = 1; distance <= REACH; distance++) {
            for (Direction side : List.of(Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH)) {
                BlockPos at = centre.relative(side, distance);
                if (level.getBlockState(at).isAir()
                        && level.getBlockState(at.above()).isAir()
                        && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) {
                    return Optional.of(new BlockCoord(at.getX(), at.getY(), at.getZ()));
                }
            }
        }
        return Optional.empty();
    }
}
