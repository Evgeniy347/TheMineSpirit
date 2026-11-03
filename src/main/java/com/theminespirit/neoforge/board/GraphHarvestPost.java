package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.OrchardPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphNames;
import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.mining.LeftOreRules;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.MineOreLedger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;

final class GraphHarvestPost {
    private final GraphPost post;

    GraphHarvestPost(GraphPost post) {
        this.post = post;
    }

    boolean craters(GraphTickets.Ticket ticket) {
        List<BlockCoord> centers = CraterWatch.centers(post.service);
        if (centers.isEmpty()) {
            return false;
        }
        for (BlockCoord center : centers) {
            BlockPos pos = new BlockPos(center.x(), center.y(), center.z());
            String id = CraterWatch.craterId(pos);
            if (post.done(id) || post.open(id)) {
                continue;
            }
            int cells =
                    post.service.craters().profile(CraterWatch.craterCause(pos)).size();
            post.submit(
                    post.ticket(
                            id,
                            BoardCase.FILL_CRATER,
                            Optional.empty(),
                            ticket.task().priority(),
                            CraterWatch.craterCause(pos),
                            post.items(ticket.task())),
                    TicketTarget.at(center).withCraterBlocks(Math.max(1, cells)));
        }
        return true;
    }

    boolean fences(GraphTickets.Ticket ticket) {
        Map<BlockCoord, String> breaks = FenceWatch.liveBreaks(post.level);
        if (breaks.isEmpty()) {
            return false;
        }
        for (Map.Entry<BlockCoord, String> broken : breaks.entrySet()) {
            BlockPos pos = FenceWatch.pos(broken.getKey());
            String id = FenceWatch.fenceId(pos);
            if (post.done(id) || post.open(id)) {
                continue;
            }
            post.submit(
                    post.ticket(
                            id,
                            BoardCase.REPAIR_FENCE,
                            Optional.empty(),
                            ticket.task().priority(),
                            FenceWatch.fenceCause(pos),
                            post.items(ticket.task())),
                    TicketTarget.at(broken.getKey()).withFenceBlock(broken.getValue()));
        }
        return true;
    }

    boolean orchard(GraphTickets.Ticket ticket, GraphPlan.Task task) {
        boolean mature = false;
        for (BlockCoord spot : post.scan.stage().allOrchardSaplings()) {
            Optional<BlockCoord> base = OrchardHarvest.matureBase(post.level, post.scan.stage(), spot);
            if (base.isEmpty()) {
                continue;
            }
            mature = true;
            String id = OrchardPlans.id(spot);
            if (post.done(id) || post.open(id)) {
                continue;
            }
            post.submit(
                    post.ticket(
                            id, BoardCase.HARVEST_ORCHARD, Optional.empty(), task.priority(), ticket.id(), Map.of()),
                    TicketTarget.at(base.orElseThrow()));
        }
        return mature;
    }

    boolean leftOre(GraphTickets.Ticket ticket, GraphPlan.Task task) {
        String item = GraphNames.item(post.graph, task.makes(), post.scan.wood());
        Map<BlockCoord, MineOreLedger.Left> ores = LeftOreWatch.find(post.level, item);
        if (ores.isEmpty()) {
            return false;
        }
        for (Map.Entry<BlockCoord, MineOreLedger.Left> ore : ores.entrySet()) {
            String id = LeftOreWatch.id(ore.getKey());
            if (post.done(id) || post.open(id)) {
                continue;
            }
            MineOreLedger.Left left = ore.getValue();
            String tool = LeftOreRules.requiredTool(left.block());
            post.submit(
                    post.ticket(
                            id,
                            BoardCase.MINE_LEFT_ORE,
                            Optional.empty(),
                            ticket.task().priority(),
                            id,
                            Map.of(tool, 1)),
                    TicketTarget.at(left.stand()).withSupply(left.block(), 1));
        }
        return true;
    }
}
