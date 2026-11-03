package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.StateChangeLog;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.FloatingBlocks;
import com.theminespirit.core.town.PlannedBlock;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageWork;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;

final class DemolitionTickets {
    record Placement(BlockCoord stand, BlockCoord top, BlockCoord lowest) {}

    record Batch(List<BlockCoord> stray, List<PlannedBlock> blocks, Placement placement) {}

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final StateChangeLog CHANGES = new StateChangeLog();

    private DemolitionTickets() {}

    static void warnExcluded(String world, int excluded, List<String> notes, long now) {
        if (excluded == 0) {
            return;
        }
        String signature = excluded + ":" + notes;
        if (!CHANGES.changed("tidy-excluded:" + world, signature)) {
            return;
        }
        LOGGER.warn(
                "[tidy] участок не расчищается в тик {}: исключено недостижимых {} из висящих; {}",
                now,
                excluded,
                notes);
    }

    static String idFor(List<BlockCoord> stray) {
        long hash = 17L;
        for (BlockCoord at : stray) {
            hash = hash * 31L + at.hashCode();
        }
        return PlanDemolition.PREFIX + Long.toUnsignedString(hash, 36);
    }

    static boolean submit(GraphPost post, StageSite site, List<PlannedBlock> blocks, String id, Placement placement) {
        BoardOutcome outcome = post.submit(
                post.ticket(
                        id,
                        BoardCase.DISMANTLE_SHELTER,
                        Optional.empty(),
                        BoardRules.priorityOf(BoardCase.DISMANTLE_SHELTER),
                        BoardTicket.ALWAYS_CAUSE,
                        Map.of()),
                target(site, blocks, placement));
        return outcome == BoardOutcome.ACCEPTED;
    }

    static void restore(
            GraphPost post, BoardTicket ticket, StageSite site, List<PlannedBlock> blocks, Placement placement) {
        post.submit(ticket, target(site, blocks, placement));
    }

    static Placement treePlacement(BlockCoord stand, FloatingBlocks.Finding finding) {
        int highest = finding.cells().stream()
                .mapToInt(BlockCoord::y)
                .max()
                .orElse(finding.at().y());
        int lowest = finding.cells().stream()
                .mapToInt(BlockCoord::y)
                .min()
                .orElse(finding.at().y());
        return new Placement(
                stand, new BlockCoord(stand.x(), highest, stand.z()), new BlockCoord(stand.x(), lowest, stand.z()));
    }

    static TicketTarget target(StageSite site, List<PlannedBlock> blocks, Placement placement) {
        TicketTarget target = placement.stand() == null
                ? TicketTarget.at(blocks.get(0).at())
                : TicketTarget.at(placement.stand()).withRelocate(placement.lowest(), placement.top());
        return target.withWork(new StageWork(site, blocks));
    }
}
