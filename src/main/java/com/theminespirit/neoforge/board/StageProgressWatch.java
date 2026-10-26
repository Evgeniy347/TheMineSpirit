package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.StageAudit;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.building.WoodSpecies;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

final class StageProgressWatch {
    private static final Set<BoardCase> BUILDS = EnumSet.of(
            BoardCase.BUILD_FENCE,
            BoardCase.BUILD_HOUSE,
            BoardCase.LIGHTING,
            BoardCase.BUILD_ORCHARD,
            BoardCase.REPAIR_BUILDING);
    private final Map<String, Integer> placedAtIssue = new HashMap<>();

    void issued(ServerLevel level, BoardTicket ticket, @Nullable TicketTarget target) {
        placedAtIssue.remove(ticket.id());
        progress(level, ticket, target).ifPresent(placed -> placedAtIssue.put(ticket.id(), placed.done()));
    }

    @Nullable
    String emptyResult(ServerLevel level, BoardTicket ticket, @Nullable TicketTarget target) {
        Integer before = placedAtIssue.remove(ticket.id());
        if (before == null) {
            return null;
        }
        Optional<FirstStepsRunner.Placed> after = progress(level, ticket, target);
        if (after.isEmpty()
                || StageAudit.progressed(before, after.get().done(), after.get().total())) {
            return null;
        }
        return "стройка закрыта без результата: поставлено " + after.get().done() + " из "
                + after.get().total()
                + ", как и при выдаче — нет материала или подхода, задание не считается сделанным";
    }

    private static Optional<FirstStepsRunner.Placed> progress(
            ServerLevel level, BoardTicket ticket, @Nullable TicketTarget target) {
        if (target == null || target.work() == null || !BUILDS.contains(ticket.boardCase())) {
            return Optional.empty();
        }
        String wood = target.wood() == null ? WoodSpecies.OAK : target.wood();
        FirstStepsRunner.Placed placed = FirstStepsRunner.placedBlocks(
                level, TicketPlans.plan(ticket.boardCase(), ticket.level(), target), wood);
        return placed.total() == 0 ? Optional.empty() : Optional.of(placed);
    }
}
