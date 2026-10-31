package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BuildGap;
import com.theminespirit.core.board.GapLog;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.plan.TaskNode;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class PlotTrace {
    private static final Logger LOGGER = LogUtils.getLogger();

    record Trace(Map<String, Integer> required, Map<String, Integer> placed, List<String> missing) {}

    private PlotTrace() {}

    static Trace trace(ServerLevel level, TaskNode plan, String wood) {
        BuildGap.Gap gap = BuildGap.trace(plan, wood, FirstStepsRunner.probe(level), FirstStepsRunner.SCHEMES);
        return new Trace(gap.required(), gap.placed(), gap.missing());
    }

    static void reportGap(
            ServerLevel level,
            BoardCase boardCase,
            TicketTarget target,
            String wood,
            int missing,
            int total,
            boolean rebuild) {
        Trace trace = trace(level, TicketPlans.plan(boardCase, 1, target), wood);
        if (!GapLog.changed(boardCase, trace.missing())) {
            return;
        }
        LOGGER.info(
                "[layout] {} сделан по журналу, но {} {} из {} блоков: {}; недостающие {}",
                boardCase,
                rebuild ? "нет" : "в мире нет",
                missing,
                total,
                rebuild ? "стройка заново" : "восстановление",
                trace.missing());
    }
}
