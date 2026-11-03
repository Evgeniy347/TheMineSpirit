package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.town.TownPlan;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class StageSchedule {
    private static final Logger LOGGER = LogUtils.getLogger();

    private StageSchedule() {}

    static FirstStepsRunner.StageScan reopen(
            ServerLevel level,
            BoardService service,
            StockView stock,
            FirstStepsRunner.StageScan scan,
            int spirits,
            long now) {
        Objects.requireNonNull(level, "Уровень не может быть null: шаги этапа открываются в мире");
        Optional<TownPlan> town = SettlementPlotStore.get(level).town();
        StageWorldGate.apply(
                level,
                service,
                scan.stage(),
                scan.wood(),
                WorldAreaInputs.of(
                        level,
                        service,
                        scan,
                        town,
                        BuildingAuditInputs.buildings(level, service, scan, Map.of(), town)));
        for (BoardCase step :
                StageReopen.missing(service.board(), StageReopen.STEPS, BoardTuning.stageCaseLimit(spirits))) {
            Map<String, Integer> missing = FirstStepsRunner.open(
                    level,
                    service,
                    stock,
                    scan,
                    new FirstStepsRunner.StepOrder(step, BoardRules.priorityOf(step), BoardTicket.ALWAYS_CAUSE),
                    now);
            if (!missing.isEmpty()) {
                LOGGER.info("Постройка этапа {} в расписании ждёт: не хватает {}", step, missing);
            }
        }
        return scan;
    }
}
