package com.theminespirit.core.incident;

import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.coordinator.WorkKind;
import com.theminespirit.core.coordinator.WorkTarget;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public final class IncidentAim {
    private static final String SUPPLY_GATHER = "supply_gather:";

    private IncidentAim() {}

    public static String describe(BoardTicket ticket, @Nullable TicketTarget target, Collection<WorkTarget> reserved) {
        Objects.requireNonNull(ticket, "ticket не может быть null: инцидент называет задание");
        Objects.requireNonNull(reserved, "reserved не может быть null: без броней не видно цель добычи");
        if (!ticket.id().startsWith(SUPPLY_GATHER)) {
            return target == null ? "-" : target.target() + " часть " + target.count();
        }
        String item = target == null || target.supplyItem() == null ? "-" : target.supplyItem();
        Optional<WorkTarget> work = reserved.stream()
                .filter(candidate -> candidate.kind() == WorkKind.TREE || candidate.kind() == WorkKind.VEIN)
                .findFirst();
        return work.map(found -> "добыча " + item + " у " + found.anchor())
                .orElse("добыча " + item + ": точка ещё не выбрана, дом не цель");
    }
}
