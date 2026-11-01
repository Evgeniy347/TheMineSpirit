package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.MaterialSource;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageWork;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;

final class StageSettle {
    static final Set<BoardCase> AUDIT_STEPS = EnumSet.of(
            BoardCase.LIGHTING,
            BoardCase.BUILD_FENCE,
            BoardCase.BUILD_ORCHARD,
            BoardCase.BUILD_HOUSE,
            BoardCase.BUILD_STREETS,
            BoardCase.CLEAR_GROUNDS,
            BoardCase.STOCK_CHESTS,
            BoardCase.CRAFT_FURNACE,
            BoardCase.BUILD_MINE);

    private StageSettle() {}

    public static void refresh(
            ServerLevel level,
            BoardService service,
            StockView stock,
            FirstStepsRunner.StageScan scan,
            BoardCase caseValue,
            long now) {
        StageSite stage = scan.stage();
        String wood = BuildingWoodChoice.stored(level, caseValue, stage, scan.wood());
        TicketTarget target = FirstStepsRunner.targetFor(level, caseValue, stage, wood);
        Map<String, Integer> need = FirstStepsRunner.need(
                level, stock, new FirstStepsRunner.OpenStep(caseValue, target, Map.of(), wood), false);
        int parts = FirstStepsRunner.partsOf(caseValue);
        if (AUDIT_STEPS.contains(caseValue)
                && FirstStepsRunner.reopenDone(
                        level, service, new FirstStepsRunner.OpenStep(caseValue, target, need, wood), parts, now)) {
            return;
        }
        MaterialSource source = FirstStepsRunner.materialSource(level);
        StageWork work = target.work();
        List<StageBuilds.FencePart> fenceParts = caseValue == BoardCase.BUILD_FENCE && work != null
                ? FenceParts.of(level, work.site(), parts)
                : List.of();
        for (int part = 1; part <= parts; part++) {
            TicketTarget partTarget = parts == 1 ? target : target.withCount(part);
            Map<String, Integer> reserve = part == 1 ? need : Map.of();
            if (caseValue == BoardCase.BUILD_FENCE && work != null) {
                partTarget = partTarget.withWork(fenceParts.get(part - 1).work());
                reserve = new HashMap<>(FirstStepsRunner.partNeed(source, partTarget, wood));
                reserve.keySet().retainAll(need.keySet());
            }
            service.board().require(FirstStepsRunner.partId(caseValue, part), reserve);
        }
    }

    public static boolean settle(
            ServerLevel level,
            BoardService service,
            StockView stock,
            FirstStepsRunner.StageScan scan,
            BoardCase caseValue,
            long now) {
        if (!AUDIT_STEPS.contains(caseValue)) {
            return false;
        }
        StageSite stage = scan.stage();
        String wood = BuildingWoodChoice.stored(level, caseValue, stage, scan.wood());
        TicketTarget target = FirstStepsRunner.targetFor(level, caseValue, stage, wood);
        Map<String, Integer> need = FirstStepsRunner.need(
                level, stock, new FirstStepsRunner.OpenStep(caseValue, target, Map.of(), wood), false);
        return FirstStepsRunner.reopenDone(
                level,
                service,
                new FirstStepsRunner.OpenStep(caseValue, target, need, wood),
                FirstStepsRunner.partsOf(caseValue),
                now);
    }
}
