package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.CaseMaterials;
import com.theminespirit.core.board.ChestPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.TaskNode;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

final class ExchangeChestPost {
    static final String NODE = "exchange_chest";

    private ExchangeChestPost() {}

    static boolean building(GraphPost post, GraphTickets.Ticket ticket) {
        if (!NODE.equals(ticket.node())) {
            return false;
        }
        if (post.open(ticket.id())) {
            return true;
        }
        Optional<BlockCoord> cell = cell(post.level, WarehouseLocations.yardCells(post.level));
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

    static Optional<BlockCoord> cell(ServerLevel level, Iterable<BlockCoord> yard) {
        for (BlockCoord cell : yard) {
            BlockPos at = new BlockPos(cell.x(), cell.y(), cell.z());
            if (level.getBlockState(at).isAir()
                    && level.getBlockState(at.above()).isAir()
                    && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) {
                return Optional.of(cell);
            }
        }
        return Optional.empty();
    }
}
