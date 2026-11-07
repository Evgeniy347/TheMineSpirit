package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.RepairPlan;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.town.StageWork;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

final class RepairGoalDone {
    private RepairGoalDone() {}

    static boolean of(ServerLevel level, BoardTicket ticket, @Nullable TicketTarget target) {
        if (ticket.boardCase() != BoardCase.REPAIR_BUILDING || !ticket.id().startsWith(RepairPlan.PREFIX)) {
            return false;
        }
        StageWork work = target == null ? null : target.work();
        String wood = target == null ? null : target.wood();
        if (work == null || work.repairs().isEmpty() || wood == null) {
            return false;
        }
        FirstStepsRunner.Placed placed =
                FirstStepsRunner.audit(level, BoardCase.valueOf(work.repairs()), work.site(), wood);
        return placed.confident() && placed.total() > 0 && placed.done() >= placed.total();
    }
}
