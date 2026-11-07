package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.FillOrder;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.town.PlotLeveling;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LevelRepairScenarioTests {
    private static final BlockCoord ANCHOR = new BlockCoord(0, 64, 0);

    @Test
    void plan_HoleAndExtra_CutThenBatchedLayersBottomUp() {
        PlotLeveling leveling = LevelRepair.leveling(
                List.of(new BlockCoord(2, 63, 2), new BlockCoord(2, 62, 2)), List.of(new BlockCoord(-3, 64, 1)));
        TaskNode plan = TicketPlans.plan(
                BoardCase.REPAIR_BUILDING, 1, TicketTarget.at(ANCHOR).withLeveling(leveling));
        List<ScriptCall> calls = new ArrayList<>();
        collect(plan, calls);
        assertEquals(
                List.of("clear_area", "collect_drops", "acquire_at", "fill", "fill"),
                calls.stream()
                        .map(ScriptCall::script)
                        .filter(script -> !"wait".equals(script))
                        .toList(),
                "Восстановление выравнивания: лишний блок срезается, земля на все дыры — одной партией, дыры "
                        + "засыпаются; иначе аудит ровности не возвращает участок к плану");
        assertEquals(
                List.of(62, 63),
                calls.stream()
                        .filter(call -> "fill".equals(call.script()))
                        .map(call -> call.params().get("y1"))
                        .toList(),
                "Дыра засыпается снизу вверх: верхняя клетка без опоры под ней не встаёт");
    }

    @Test
    void plan_CraterPitAndPlotLeveling_ShareBottomUpLayers() {
        List<BlockCoord> pit = List.of(
                new BlockCoord(10, 64, 10),
                new BlockCoord(10, 65, 10),
                new BlockCoord(10, 66, 10),
                new BlockCoord(11, 64, 10),
                new BlockCoord(11, 65, 10),
                new BlockCoord(11, 66, 10));
        PlotLeveling leveling = LevelRepair.leveling(pit, List.of());
        TicketTarget target = TicketTarget.at(new BlockCoord(10, 66, 10))
                .withCraterBlocks(pit.size())
                .withLeveling(leveling);

        assertTrue(
                target.details().contains("pit=2 клеток, 6 блоков, глубина 3"),
                "На доске видно, что засыпают: число клеток, блоков и глубину ямы — " + target.details());
        TaskNode plan = TicketPlans.plan(BoardCase.FILL_CRATER, target);
        List<ScriptCall> calls = new ArrayList<>();
        collect(plan, calls);

        List<Integer> filledLayers = calls.stream()
                .filter(call -> "fill".equals(call.script()))
                .map(call -> (Integer) call.params().get("y1"))
                .toList();
        List<Integer> plotLayers = FillOrder.layers(leveling.fill()).stream()
                .map(FillOrder.Box::y1)
                .toList();
        assertEquals(
                List.of(64, 64, 65, 65, 66, 66),
                filledLayers,
                "Засыпка ямы идёт теми же слоями снизу вверх, что и выравнивание участка: сначала весь низ");
        assertEquals(plotLayers, filledLayers, "Один движок слоёв: порядок подсыпки участка и ямы совпадает");
        assertEquals(
                List.of("goto", "acquire", "fill", "fill", "fill", "fill", "fill", "fill"),
                calls.stream()
                        .map(ScriptCall::script)
                        .filter(script -> !"wait".equals(script))
                        .toList(),
                "Земля набирается до начала слоя: иначе дух встанет в яму без материала");
    }

    @Test
    void progress_LevelingRepair_CountsCutAndFillCells() {
        BlockCoord cutDone = new BlockCoord(5, 70, 5);
        BlockCoord cutStands = new BlockCoord(6, 70, 5);
        BlockCoord fillSolid = new BlockCoord(0, 69, 0);
        BlockCoord fillHole = new BlockCoord(1, 69, 0);
        PlotLeveling leveling = LevelRepair.leveling(List.of(fillSolid, fillHole), List.of(cutDone, cutStands));
        Set<BlockCoord> empty = new HashSet<>(Set.of(cutDone, fillHole));

        LevelRepair.Progress progress = LevelRepair.progress(leveling, empty::contains);

        assertEquals(
                4,
                progress.total(),
                "Ремонт выравнивания — это 2 лишних блока срезать и 2 дыры засыпать: план из 0 блоков "
                        + "ошибочно закрывал его как «нечего строить»");
        assertEquals(2, progress.done(), "Срезан один лишний блок и засыпана одна дыра: готовность по факту мира");
        assertTrue(
                StageAudit.nothingToBuild(progress.total()).isEmpty(),
                "У ремонта выравнивания есть работа — «стройка закрыта без работы» больше не срабатывает");
    }

    private static void collect(TaskNode node, List<ScriptCall> out) {
        switch (node) {
            case ScriptCall call -> out.add(call);
            case SequenceNode sequence -> sequence.steps().forEach(step -> collect(step, out));
            case FallbackNode fallback -> collect(fallback.steps().get(0), out);
            default -> {}
        }
    }
}
