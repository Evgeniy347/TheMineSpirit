package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BuildProgressNote;
import com.theminespirit.core.board.DemolishAudit;
import com.theminespirit.core.board.PlotLeveling;
import com.theminespirit.core.board.StageAudit;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.PlannedBlock;
import com.theminespirit.core.town.StageSite;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class StageProgressWatch {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<BoardCase> BUILDS = EnumSet.of(
            BoardCase.BUILD_FENCE,
            BoardCase.BUILD_HOUSE,
            BoardCase.LIGHTING,
            BoardCase.BUILD_ORCHARD,
            BoardCase.BUILD_STREETS,
            BoardCase.STOCK_CHESTS,
            BoardCase.CRAFT_FURNACE,
            BoardCase.REPAIR_BUILDING);
    private final Map<String, Integer> placedAtIssue = new HashMap<>();
    private final Map<String, FirstStepsRunner.Placed> overallAtIssue = new HashMap<>();

    void issued(ServerLevel level, BoardTicket ticket, @Nullable TicketTarget target) {
        placedAtIssue.remove(ticket.id());
        Optional<FirstStepsRunner.Placed> placed = progress(level, ticket, target);
        if (placed.isEmpty()) {
            return;
        }
        FirstStepsRunner.Placed now = placed.get();
        placedAtIssue.put(ticket.id(), now.done());
        overall(level, ticket, target).ifPresent(overall -> overallAtIssue.put(ticket.id(), overall));
        PlotLeveling leveling = target == null ? null : target.leveling();
        LOGGER.info(BuildProgressNote.line(
                ticket.boardCase(),
                ticket.level(),
                target == null || target.work() == null
                        ? ticket.level()
                        : target.work().site().anchor().y(),
                now.done(),
                now.total(),
                leveling == null ? 0 : leveling.cutVolume(),
                leveling == null ? 0 : leveling.fillVolume()));
        if (target == null || target.work() == null) {
            return;
        }
        StageSite site = target.work().site();
        String wood = target.wood() == null ? WoodSpecies.OAK : target.wood();
        PlotTrace.Trace trace =
                PlotTrace.trace(level, TicketPlans.plan(ticket.boardCase(), ticket.level(), target), wood);
        LOGGER.info(
                "[plot] {}: план от участка {}, дом {}, сад {}, поворот {}; требуется {} — поставлено {}",
                ticket.id(),
                site.anchor(),
                site.houseOrigin(),
                site.orchardOrigin(),
                target.work().builds().isEmpty()
                        ? "-"
                        : target.work().builds().get(0).rotation(),
                trace.required(),
                trace.placed());
    }

    @Nullable
    String emptyResult(ServerLevel level, BoardTicket ticket, @Nullable TicketTarget target) {
        if (ticket.boardCase() == BoardCase.DISMANTLE_SHELTER) {
            return demolishResult(level, target);
        }
        Integer before = placedAtIssue.remove(ticket.id());
        logOverall(level, ticket, target);
        if (before == null) {
            return null;
        }
        Optional<FirstStepsRunner.Placed> after = progress(level, ticket, target);
        if (after.isEmpty()) {
            return null;
        }
        Optional<String> empty = StageAudit.nothingToBuild(after.get().total());
        if (empty.isPresent()) {
            return empty.get();
        }
        if (StageAudit.progressed(before, after.get().done(), after.get().total())) {
            return null;
        }
        return "стройка закрыта без результата: поставлено " + after.get().done() + " из "
                + after.get().total()
                + ", как и при выдаче — нет материала или подхода, задание не считается сделанным";
    }

    private static @Nullable String demolishResult(ServerLevel level, @Nullable TicketTarget target) {
        if (target == null || target.work() == null) {
            return "разбор закрыт без факта мира: у задания нет списка блоков для сверки";
        }
        List<PlannedBlock> planned = target.work().blocks();
        List<BlockCoord> standing = new ArrayList<>();
        for (PlannedBlock block : planned) {
            BlockCoord at = block.at();
            BlockState state = level.getBlockState(new BlockPos(at.x(), at.y(), at.z()));
            if (!state.isAir() && state.getFluidState().isEmpty()) {
                standing.add(at);
            }
        }
        return DemolishAudit.stillStanding(planned.size(), standing).orElse(null);
    }

    private void logOverall(ServerLevel level, BoardTicket ticket, @Nullable TicketTarget target) {
        FirstStepsRunner.Placed before = overallAtIssue.remove(ticket.id());
        Optional<FirstStepsRunner.Placed> after = overall(level, ticket, target);
        if (before == null || after.isEmpty() || target == null) {
            return;
        }
        FirstStepsRunner.Placed now = after.get();
        LOGGER.info(
                "[plot] {}: сегмент {} из {}, было {}% ({}/{}), стало {}% ({}/{})",
                ticket.id(),
                target.count(),
                TicketPlans.FENCE_PARTS,
                percent(before),
                before.done(),
                before.total(),
                percent(now),
                now.done(),
                now.total());
    }

    private static int percent(FirstStepsRunner.Placed placed) {
        return placed.total() == 0 ? 0 : placed.done() * 100 / placed.total();
    }

    private static Optional<FirstStepsRunner.Placed> overall(
            ServerLevel level, BoardTicket ticket, @Nullable TicketTarget target) {
        if (ticket.boardCase() != BoardCase.BUILD_FENCE || target == null || target.work() == null) {
            return Optional.empty();
        }
        String wood = target.wood() == null ? WoodSpecies.OAK : target.wood();
        TicketTarget whole = FirstStepsRunner.targetFor(
                level, ticket.boardCase(), target.work().site(), wood);
        return Optional.of(FirstStepsRunner.placedBlocks(
                level, TicketPlans.plan(ticket.boardCase(), ticket.level(), whole), wood));
    }

    private static Optional<FirstStepsRunner.Placed> progress(
            ServerLevel level, BoardTicket ticket, @Nullable TicketTarget target) {
        if (target == null || target.work() == null || !BUILDS.contains(ticket.boardCase())) {
            return Optional.empty();
        }
        String wood = target.wood() == null ? WoodSpecies.OAK : target.wood();
        FirstStepsRunner.Placed placed = FirstStepsRunner.placedBlocks(
                level, TicketPlans.plan(ticket.boardCase(), ticket.level(), target), wood);
        return Optional.of(placed);
    }
}
