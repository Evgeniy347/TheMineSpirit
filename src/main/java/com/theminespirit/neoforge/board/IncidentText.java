package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.incident.IncidentAim;
import com.theminespirit.neoforge.IncidentSnapshots;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.coordinator.WorkReserveHub;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

final class IncidentText {
    private IncidentText() {}

    static String of(SpiritEntity spirit, BoardTicket ticket, @Nullable TicketTarget target, String reason) {
        String aim = IncidentAim.describe(ticket, target, WorkReserveHub.ownedBy(spirit));
        String what = ticket.id() + " (" + ticket.boardCase() + ", цель " + aim + "): " + reason;
        return spirit.level() instanceof ServerLevel level ? IncidentSnapshots.record(level, spirit, what) : what;
    }
}
