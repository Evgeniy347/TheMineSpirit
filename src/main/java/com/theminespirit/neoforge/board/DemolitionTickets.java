package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.PlannedBlock;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageWork;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class DemolitionTickets {
    record Placement(BlockCoord stand, BlockCoord top, BlockCoord lowest) {}

    private DemolitionTickets() {}

    static String idFor(List<BlockCoord> stray) {
        long hash = 17L;
        for (BlockCoord at : stray) {
            hash = hash * 31L + at.hashCode();
        }
        return PlanDemolition.PREFIX + Long.toUnsignedString(hash, 36);
    }

    static boolean submit(
            BoardService service, StageSite site, List<PlannedBlock> blocks, long now, String id, Placement placement) {
        TicketTarget target = placement.stand() == null
                ? TicketTarget.at(blocks.get(0).at())
                : TicketTarget.at(placement.stand()).withRelocate(placement.lowest(), placement.top());
        BoardOutcome outcome = service.submit(
                new BoardTicket(
                        id,
                        BoardCase.DISMANTLE_SHELTER,
                        Optional.empty(),
                        BoardRules.priorityOf(BoardCase.DISMANTLE_SHELTER),
                        now,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()),
                target.withWork(new StageWork(site, blocks)));
        return outcome == BoardOutcome.ACCEPTED;
    }
}
