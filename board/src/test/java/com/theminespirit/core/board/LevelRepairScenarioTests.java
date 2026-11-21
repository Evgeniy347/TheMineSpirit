package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
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
                        + "засыпаются; иначе аудит ровности не возвращает участок к плану"
                        + " При смене plan_HoleAndExtra_CutThenBatchedLayersBottomUp проверь это утверждение.");
        assertEquals(
                List.of(62, 63),
                calls.stream()
                        .filter(call -> "fill".equals(call.script()))
                        .map(call -> call.params().get("y1"))
                        .toList(),
                "Дыра засыпается снизу вверх: верхняя клетка без опоры под ней не встаёт"
                        + " Иначе тест plan_HoleAndExtra_CutThenBatchedLayersBottomUp перестанет ловить эту поломку. При смене plan_HoleAndExtra_CutThenBatchedLayersBottomUp проверь это утверждение.");
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
                "На доске видно, что засыпают: число клеток, блоков и глубину ямы — " + target.details()
                        + " Иначе тест plan_CraterPitAndPlotLeveling_ShareBottomUpLayers перестанет ловить эту поломку. При смене plan_CraterPitAndPlotLeveling_ShareBottomUpLayers проверь это утверждение.");
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
                "Засыпка ямы идёт теми же слоями снизу вверх, что и выравнивание участка: сначала весь низ"
                        + " Иначе тест plan_CraterPitAndPlotLeveling_ShareBottomUpLayers перестанет ловить эту поломку. При смене plan_CraterPitAndPlotLeveling_ShareBottomUpLayers проверь это утверждение.");
        assertEquals(
                plotLayers,
                filledLayers,
                "Один движок слоёв: порядок подсыпки участка и ямы совпадает"
                        + " Иначе тест plan_CraterPitAndPlotLeveling_ShareBottomUpLayers перестанет ловить эту поломку. При смене plan_CraterPitAndPlotLeveling_ShareBottomUpLayers проверь это утверждение.");
        assertEquals(
                List.of("goto", "acquire", "fill", "fill", "fill", "fill", "fill", "fill"),
                calls.stream()
                        .map(ScriptCall::script)
                        .filter(script -> !"wait".equals(script))
                        .toList(),
                "Земля набирается до начала слоя: иначе дух встанет в яму без материала"
                        + " При смене plan_CraterPitAndPlotLeveling_ShareBottomUpLayers проверь это утверждение.");
    }

    @Test
    void plan_ExtrasInOneColumn_OneClearAreaTopDown() {
        PlotLeveling leveling = LevelRepair.leveling(
                List.of(), List.of(new BlockCoord(3, 70, 3), new BlockCoord(3, 71, 3), new BlockCoord(3, 72, 3)));
        assertEquals(
                1,
                leveling.cut().size(),
                "Клетки одной колонки — одна работа: иначе дух строит подмости на "
                        + "каждую клетку и они сами становятся столбами (419 → 717 в зоне 10.11)"
                        + " При смене plan_ExtrasInOneColumn_OneClearAreaTopDown проверь это утверждение.");
        PlotLeveling.Box column = leveling.cut().get(0);
        assertEquals(
                70,
                column.y1(),
                "Разбор столба начинается снизу ящика, а подъём считается от верхней цели"
                        + " Иначе тест plan_ExtrasInOneColumn_OneClearAreaTopDown перестанет ловить эту поломку. При смене plan_ExtrasInOneColumn_OneClearAreaTopDown проверь это утверждение.");
        assertEquals(
                72,
                column.y2(),
                "Верхняя клетка столба — цель подъёма"
                        + " Иначе тест plan_ExtrasInOneColumn_OneClearAreaTopDown перестанет ловить эту поломку. При смене plan_ExtrasInOneColumn_OneClearAreaTopDown проверь это утверждение.");
        TaskNode plan = TicketPlans.plan(
                BoardCase.REPAIR_BUILDING, 1, TicketTarget.at(ANCHOR).withLeveling(leveling));
        List<ScriptCall> clears = new ArrayList<>();
        collect(plan, clears);
        List<ScriptCall> cut = clears.stream()
                .filter(call -> "clear_area".equals(call.script()))
                .toList();
        assertEquals(
                1,
                cut.size(),
                "Один clear_area на высокий столб, а не на каждую клетку"
                        + " Иначе тест plan_ExtrasInOneColumn_OneClearAreaTopDown перестанет ловить эту поломку. При смене plan_ExtrasInOneColumn_OneClearAreaTopDown проверь это утверждение.");
        assertEquals(
                72,
                cut.get(0).params().get("y2"),
                "Верх ящика равен верхней цели: подъём доводит духа до неё"
                        + " Иначе тест plan_ExtrasInOneColumn_OneClearAreaTopDown перестанет ловить эту поломку. При смене plan_ExtrasInOneColumn_OneClearAreaTopDown проверь это утверждение.");
    }

    @Test
    void plan_SlabCut_SplitIntoColumnsForOneClimbEach() {
        PlotLeveling leveling = new PlotLeveling(
                List.of(new PlotLeveling.Box(3, 70, 3, 4, 70, 3), new PlotLeveling.Box(3, 71, 3, 4, 71, 3)),
                List.of(),
                PlotLeveling.DEFAULT_BLOCK);
        TaskNode plan = TicketPlans.plan(
                BoardCase.REPAIR_BUILDING, 1, TicketTarget.at(ANCHOR).withLeveling(leveling));
        List<ScriptCall> clears = new ArrayList<>();
        collect(plan, clears);
        List<ScriptCall> cut = clears.stream()
                .filter(call -> "clear_area".equals(call.script()))
                .toList();
        assertEquals(
                2,
                cut.size(),
                "Плита из двух колонок — две работы: дух поднимается к своей колонке, а не пытается достать "
                        + "дальние клетки плиты с одной стоянки («нет подхода» к y=97 в живом журнале)"
                        + " Иначе тест plan_SlabCut_SplitIntoColumnsForOneClimbEach перестанет ловить эту поломку. При смене plan_SlabCut_SplitIntoColumnsForOneClimbEach проверь это утверждение.");
        assertEquals(
                70,
                cut.get(0).params().get("y1"),
                "Колонка режется от нижней клетки плиты"
                        + " Иначе тест plan_SlabCut_SplitIntoColumnsForOneClimbEach перестанет ловить эту поломку. При смене plan_SlabCut_SplitIntoColumnsForOneClimbEach проверь это утверждение.");
        assertEquals(
                71,
                cut.get(0).params().get("y2"),
                "и до верхней: один подъём на всю колонку"
                        + " Иначе тест plan_SlabCut_SplitIntoColumnsForOneClimbEach перестанет ловить эту поломку. При смене plan_SlabCut_SplitIntoColumnsForOneClimbEach проверь это утверждение.");
    }

    @Test
    void plan_GapInColumn_NotCutAsOneBox() {
        PlotLeveling leveling =
                LevelRepair.leveling(List.of(), List.of(new BlockCoord(3, 70, 3), new BlockCoord(3, 75, 3)));

        assertEquals(
                List.of(new PlotLeveling.Box(3, 75, 3, 3, 75, 3), new PlotLeveling.Box(3, 70, 3, 3, 70, 3)),
                leveling.cut(),
                "Клетки колонки через разрыв (сундук или постройка) — два бокса: разрыв в срез не попадает"
                        + " Иначе тест plan_GapInColumn_NotCutAsOneBox перестанет ловить эту поломку. При смене plan_GapInColumn_NotCutAsOneBox проверь это утверждение.");
    }

    @Test
    void progress_CutColumn_CountsEachCutCell() {
        PlotLeveling leveling = LevelRepair.leveling(
                List.of(), List.of(new BlockCoord(5, 70, 5), new BlockCoord(5, 71, 5), new BlockCoord(5, 72, 5)));
        Set<BlockCoord> empty = new HashSet<>(Set.of(new BlockCoord(5, 72, 5), new BlockCoord(5, 71, 5)));
        LevelRepair.Progress progress = LevelRepair.progress(leveling, empty::contains);
        assertEquals(
                2,
                progress.done(),
                "Срезаны две верхние клетки — прогресс обязан это показать: одна защищённая клетка внизу "
                        + "не держит весь столб на нуле"
                        + " Иначе тест progress_CutColumn_CountsEachCutCell перестанет ловить эту поломку. При смене progress_CutColumn_CountsEachCutCell проверь это утверждение.");
        assertEquals(
                3,
                progress.total(),
                "Колонка из трёх клеток — три клетки работы выравнивания"
                        + " Иначе тест progress_CutColumn_CountsEachCutCell перестанет ловить эту поломку. При смене progress_CutColumn_CountsEachCutCell проверь это утверждение.");
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
                        + "ошибочно закрывал его как «нечего строить»"
                        + " Иначе тест progress_LevelingRepair_CountsCutAndFillCells перестанет ловить эту поломку. При смене progress_LevelingRepair_CountsCutAndFillCells проверь это утверждение.");
        assertEquals(
                2,
                progress.done(),
                "Срезан один лишний блок и засыпана одна дыра: готовность по факту мира"
                        + " Иначе тест progress_LevelingRepair_CountsCutAndFillCells перестанет ловить эту поломку. При смене progress_LevelingRepair_CountsCutAndFillCells проверь это утверждение.");
        assertTrue(
                StageAudit.nothingToBuild(progress.total()).isEmpty(),
                "У ремонта выравнивания есть работа — «стройка закрыта без работы» больше не срабатывает"
                        + " Иначе тест progress_LevelingRepair_CountsCutAndFillCells перестанет ловить эту поломку. При смене progress_LevelingRepair_CountsCutAndFillCells проверь это утверждение.");
    }

    @Test
    void leveling_PlannedCells_ExcludedFromCutAndFill() {
        BlockCoord house = new BlockCoord(-180, 71, -505);
        BlockCoord mound = new BlockCoord(4, 70, 4);
        BlockCoord hole = new BlockCoord(7, 69, 7);

        PlotLeveling leveling =
                LevelRepair.leveling(List.of(hole, house), List.of(house, mound), at -> house.equals(at));

        assertEquals(
                List.of(new PlotLeveling.Box(4, 70, 4, 4, 70, 4)),
                leveling.cut(),
                "Срезка выравнивания не берёт клетку дома: защита участка откажет в ломке (160 отказов "
                        + "repair:clear_grounds у дома 18.11), а задание повторяется каждые 10 секунд"
                        + " Иначе тест leveling_PlannedCells_ExcludedFromCutAndFill перестанет ловить эту поломку. При смене leveling_PlannedCells_ExcludedFromCutAndFill проверь это утверждение.");
        assertEquals(
                List.of(new PlotLeveling.Box(7, 69, 7, 7, 69, 7)),
                leveling.fill(),
                "Подсыпка в клетку дома тоже не планируется: план постройки ставит свои блоки сам"
                        + " Иначе тест leveling_PlannedCells_ExcludedFromCutAndFill перестанет ловить эту поломку. При смене leveling_PlannedCells_ExcludedFromCutAndFill проверь это утверждение.");
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
