package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.FenceRepairs;
import com.theminespirit.core.board.OrchardPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.board.graph.GraphNames;
import com.theminespirit.core.board.graph.GraphPlan;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.mining.api.LeftOreRules;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.OrchardPlot;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.MineOreLedger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

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
        String id = FenceRepairs.TICKET_ID;
        BoardStatus status = post.service.board().statusOf(id).orElse(null);
        if (status == BoardStatus.ASSIGNED) {
            return true;
        }
        if (status == BoardStatus.DONE
                && post.service
                        .targetOf(id)
                        .filter(target -> FenceRepairs.coveredBy(target, breaks))
                        .isPresent()) {
            return true;
        }
        if (status == BoardStatus.DONE) {
            post.service.board().reopen(id);
        }
        FenceRepairs.Batch batch = FenceRepairs.of(breaks);
        post.submit(
                post.ticket(
                        id,
                        BoardCase.REPAIR_FENCE,
                        Optional.empty(),
                        ticket.task().priority(),
                        FenceRepairs.CAUSE,
                        FenceRepairs.scale(
                                post.items(ticket.task()), batch.cells().size())),
                TicketTarget.at(batch.first()).withFenceCells(batch.cells(), batch.blocks()));
        return true;
    }

    boolean orchard(GraphTickets.Ticket ticket, GraphPlan.Task task) {
        boolean grown = false;
        for (BlockCoord spot : post.scan.stage().allOrchardSaplings()) {
            Optional<BlockCoord> base = OrchardHarvest.grownBase(post.level, spot);
            if (base.isEmpty()) {
                continue;
            }
            grown = true;
            String id = OrchardPlans.id(spot);
            if (post.open(id)) {
                continue;
            }
            if (post.done(id)) {
                // lib: id места постоянный, а дерево на нём вырастает снова — выполненное задание возвращаем,
                // иначе после первой рубки сад зарастает и больше не рубится.
                post.service.board().reopen(id);
            }
            post.submit(
                    post.ticket(
                            id, BoardCase.HARVEST_ORCHARD, Optional.empty(), task.priority(), ticket.id(), Map.of()),
                    TicketTarget.at(base.orElseThrow()));
        }
        return grown;
    }

    boolean replantOrchard(GraphTickets.Ticket ticket, GraphPlan.Task task) {
        StageSite stage = post.scan.stage();
        Map<String, Integer> stock = post.service.stock().available();
        if (plant(ticket, task, stage.orchardSaplings(), stage.orchardWood(), stock)) {
            return true;
        }
        for (OrchardPlot plot : stage.extraOrchards()) {
            if (plant(ticket, task, plot.saplings(), plot.wood(), stock)) {
                return true;
            }
        }
        return false;
    }

    private boolean plant(
            GraphTickets.Ticket ticket,
            GraphPlan.Task task,
            List<BlockCoord> spots,
            String wood,
            Map<String, Integer> stock) {
        String sapling = wood + "_sapling";
        if (stock.getOrDefault(sapling, 0) < 1) {
            return false;
        }
        for (BlockCoord spot : spots) {
            BlockPos pos = new BlockPos(spot.x(), spot.y(), spot.z());
            if (!post.level.isLoaded(pos) || !post.level.getBlockState(pos).isAir() || !survives(sapling, pos)) {
                continue;
            }
            post.submit(
                    post.ticket(
                            ticket.id(),
                            BoardCase.ACTIVITY_BUILD,
                            Optional.empty(),
                            task.priority(),
                            ticket.id(),
                            Map.of(sapling, 1)),
                    TicketTarget.at(spot).withSupply(sapling, 1));
            return true;
        }
        return false;
    }

    private boolean survives(String item, BlockPos pos) {
        Block block = Block.byItem(BuiltInRegistries.ITEM.get(ResourceLocation.parse(item)));
        BlockState state = block.defaultBlockState();
        return !state.isAir() && state.canSurvive(post.level, pos);
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
