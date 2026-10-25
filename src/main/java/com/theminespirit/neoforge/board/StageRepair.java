package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.ConfigStamp;
import com.theminespirit.core.board.RepairPlan;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.StageSites;
import com.theminespirit.core.town.StageWork;
import com.theminespirit.core.town.TownEntry;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;

final class StageRepair {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int PERCENT = 100;

    private StageRepair() {}

    static BoardOutcome submit(
            BoardService service, BoardCase step, TicketTarget target, int missing, int total, long now) {
        StageWork work = target.work();
        if (work == null) {
            throw new IllegalArgumentException("Восстановление " + step + " без места этапа: план не передан");
        }
        String id = RepairPlan.PREFIX + step.name().toLowerCase(Locale.ROOT);
        int percent = total == 0 ? 0 : missing * PERCENT / total;
        BoardOutcome outcome = service.submit(
                new BoardTicket(
                        id,
                        BoardCase.REPAIR_BUILDING,
                        Optional.empty(),
                        BoardRules.priorityOf(BoardCase.REPAIR_BUILDING),
                        now,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()),
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

    static String label(BoardCase step) {
        String id = switch (step) {
            case BUILD_HOUSE -> StageSites.HUT;
            case STOCK_CHESTS, CRAFT_FURNACE -> StageSites.YARD;
            case BUILD_MINE -> StageSites.MINE;
            case PLANT_GROVE -> StageSites.GROVE;
            case BUILD_FENCE -> "Ограда";
            case LIGHTING -> "Факелы";
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
