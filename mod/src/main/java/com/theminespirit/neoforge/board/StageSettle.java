package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BuildGap;
import com.theminespirit.core.board.MaterialSource;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.town.PlannedBlock;
import com.theminespirit.core.town.SchemeBuild;
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
                FirstStepsRunner.partsOf(caseValue));
    }

    static TicketTarget repairTarget(BoardCase caseValue, TicketTarget target, BuildGap.Gap gap) {
        StageWork work = target.work();
        if (caseValue != BoardCase.BUILD_FENCE
                || work == null
                || gap.missingCells().isEmpty()) {
            return target;
        }
        List<PlannedBlock> blocks = work.blocks().stream()
                .filter(block -> gap.missingCells().containsKey(block.at()))
                .toList();
        List<SchemeBuild> builds = work.builds().stream()
                .filter(build -> gap.missingCells().keySet().stream()
                        .anyMatch(at -> Math.abs(at.x() - build.origin().x()) <= 4
                                && Math.abs(at.z() - build.origin().z()) <= 4
                                && Math.abs(at.y() - build.origin().y()) <= 5))
                .toList();
        if (blocks.isEmpty() && builds.isEmpty()) {
            return target;
        }
        return target.withWork(new StageWork(work.site(), blocks, builds));
    }

    static Map<String, Integer> partNeed(MaterialSource source, TicketTarget target, String wood) {
        Map<String, Integer> need = new HashMap<>();
        FirstStepsRunner.materials(source, BoardCase.BUILD_FENCE, target)
                .forEach((item, count) -> need.merge(WoodSpecies.swap(item, wood), count, Integer::sum));
        return need;
    }
}
