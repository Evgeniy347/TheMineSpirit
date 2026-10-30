package com.theminespirit.core.incident;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.coordinator.WorkKind;
import com.theminespirit.core.coordinator.WorkTarget;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class IncidentAim {
    private static final String SUPPLY_GATHER = "supply_gather:";
    private static final Set<BoardCase> GATHER_CASES = EnumSet.of(BoardCase.GATHER_MATERIALS, BoardCase.COLLECT_DROPS);
    private static final Set<WorkKind> GATHER_KINDS =
            EnumSet.of(WorkKind.TREE, WorkKind.VEIN, WorkKind.GATHER, WorkKind.DROP);

    private IncidentAim() {}

    public static String describe(BoardTicket ticket, @Nullable TicketTarget target, Collection<WorkTarget> reserved) {
        Objects.requireNonNull(ticket, "ticket не может быть null: инцидент называет задание");
        Objects.requireNonNull(reserved, "reserved не может быть null: без броней не видно цель добычи");
        if (!ticket.id().startsWith(SUPPLY_GATHER) && !GATHER_CASES.contains(ticket.boardCase())) {
            return target == null ? "-" : target.target() + " часть " + target.count();
        }
        String item = target == null || target.supplyItem() == null ? "-" : target.supplyItem();
        Optional<WorkTarget> work = reserved.stream()
                .filter(candidate -> GATHER_KINDS.contains(candidate.kind()))
                .findFirst();
        return work.map(found -> "добыча " + item + " у " + found.anchor())
                .orElse("добыча " + item + ": точка ещё не выбрана, дом не цель");
    }
}
