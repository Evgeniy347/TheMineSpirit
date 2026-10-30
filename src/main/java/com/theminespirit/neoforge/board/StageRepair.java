package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BuildingLevels;
import com.theminespirit.core.board.ConfigStamp;
import com.theminespirit.core.board.RepairPlan;
import com.theminespirit.core.board.RepairReopen;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.StageSites;
import com.theminespirit.core.town.StageWork;
import com.theminespirit.core.town.TownEntry;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.slf4j.Logger;

final class StageRepair {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int PERCENT = 100;

    private StageRepair() {}

    record Gap(int missing, int total, Map<String, Integer> need) {}

    static BoardOutcome submit(BoardService service, BoardCase step, TicketTarget target, Gap gap, long now) {
        int missing = gap.missing();
        int total = gap.total();
        StageWork work = target.work();
        if (work == null) {
            throw new IllegalArgumentException("Восстановление " + step + " без места этапа: план не передан");
        }
        if (upgrading(service, step)) {
            return BoardOutcome.DUPLICATE;
        }
        String id = RepairPlan.PREFIX + step.name().toLowerCase(Locale.ROOT);
        int percent = total == 0 ? 0 : missing * PERCENT / total;
        if (service.board().statusOf(id).orElse(null) == BoardStatus.DONE) {
            Map<String, Integer> before =
                    service.board().find(id).map(BoardTicket::requiredItems).orElse(Map.of());
            if (!RepairReopen.shouldReopen(before, gap.need())) {
                return BoardOutcome.DUPLICATE;
            }
            service.reopen(id);
        }
        BoardOutcome outcome = service.submit(
                new BoardTicket(
                        id,
                        BoardCase.REPAIR_BUILDING,
                        Optional.empty(),
                        BoardRules.repairPriorityOf(step),
                        now,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        gap.need()),
                target.withWork(work.repairing(step.name())));
        if (outcome == BoardOutcome.ACCEPTED) {
            String name = label(step);
            LOGGER.info(
                    "[layout] восстановление «{}»: нет {} из {} блоков ({}%), задание {}",
                    name, missing, total, percent, id);
            service.journal()
                    .recordEntry(BoardLog.note(
                            id,
                            BoardCase.REPAIR_BUILDING,
                            now,
                            List.of("building=" + name, "missing=" + missing, "total=" + total, "percent=" + percent),
                            null,
                            ConfigStamp.current()));
        }
        return outcome;
    }

    private static boolean upgrading(BoardService service, BoardCase step) {
        OptionalInt next = BuildingLevels.nextLevel(step, service.board().ledger());
        if (next.isEmpty() || next.getAsInt() < 2) {
            return false;
        }
        int level = next.getAsInt();
        if (BuildingLevels.state(step, level, service.board().ledger()) != BuildingLevels.State.UPGRADING) {
            return false;
        }
        for (BoardTicket ticket : service.board().all()) {
            if (ticket.boardCase() != step || ticket.level() != level) {
                continue;
            }
            BoardStatus status = service.board().statusOf(ticket.id()).orElse(null);
            if (status == BoardStatus.PENDING
                    || status == BoardStatus.WAITING_RESOURCES
                    || status == BoardStatus.ASSIGNED) {
                return true;
            }
        }
        return false;
    }

    static String label(BoardCase step) {
        String id = switch (step) {
            case BUILD_HOUSE -> StageSites.HUT;
            case STOCK_CHESTS, CRAFT_FURNACE -> StageSites.YARD;
            case BUILD_MINE -> StageSites.MINE;
            case BUILD_ORCHARD -> StageSites.ORCHARD;
            case BUILD_FENCE -> "Ограда";
            case LIGHTING -> "Факелы";
            case BUILD_STREETS -> "Улицы";
            default -> step.name().toLowerCase(Locale.ROOT);
        };
        return DataTables.INSTANCE
                .town()
                .current()
                .entry(id)
                .map(TownEntry::name)
                .orElse(id);
    }
}
