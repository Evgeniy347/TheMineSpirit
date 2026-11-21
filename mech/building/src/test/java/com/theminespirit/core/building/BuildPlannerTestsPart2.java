package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.api.BuildRotation;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
class BuildPlannerTestsPart2 {
    private static final PresetLibrary LIBRARY = BuildPlanSnapshotTests.library();

    private static BuildPlanner.Plan plan(String scheme, BuildPlanner.Portion portion) {
        return BuildPlanSnapshotTests.plan(LIBRARY.byId(scheme).orElseThrow(), BuildRotation.NONE, portion);
    }

    @Test
    void cursor_SkipsFinishedStands_AndShiftsToWorld() {
        BuildPlanner.Plan plan = plan("family_house", BuildPlanner.Portion.FILL);
        BlockCoord origin = new BlockCoord(100, 64, -20);
        BuildPlanCursor cursor = new BuildPlanCursor(plan, origin);
        List<BuildPlanner.Work> works = new ArrayList<>();
        plan.steps().forEach(step -> {
            if (step instanceof BuildPlanner.Work work && !work.cells().isEmpty()) {
                works.add(work);
            }
        });
        BlockCoord firstCell = works.get(0).cells().get(0);
        Set<BlockCoord> firstStand = new HashSet<>();
        works.get(0)
                .cells()
                .forEach(cell -> firstStand.add(new BlockCoord(cell.x() + 100, cell.y() + 64, cell.z() - 20)));
        BuildPlanCursor.Next next = cursor.next(cell -> !firstStand.contains(cell));
        while (next instanceof BuildPlanCursor.Fetch) {
            cursor.advance();
            next = cursor.next(cell -> !firstStand.contains(cell));
        }
        BuildPlanner.Work second = works.get(1);
        assertEquals(
                new BuildPlanCursor.StandAt(
                        new BlockCoord(
                                second.stand().x() + 100,
                                second.stand().y() + 64,
                                second.stand().z() - 20),
                        second.cells().stream()
                                .map(cell -> new BlockCoord(cell.x() + 100, cell.y() + 64, cell.z() - 20))
                                .toList()),
                next,
                "первая стоянка готова (клетка " + firstCell + ") — курсор обязан перейти ко второй"
                        + " Иначе тест cursor_SkipsFinishedStands_AndShiftsToWorld перестанет ловить эту поломку. При смене cursor_SkipsFinishedStands_AndShiftsToWorld проверь это утверждение.");
        while (!(cursor.next(cell -> false) instanceof BuildPlanCursor.Done)) {
            cursor.advance();
        }
        assertTrue(
                cursor.index() >= plan.steps().size() - 1,
                "всё стоит — план кончился"
                        + " Иначе тест cursor_SkipsFinishedStands_AndShiftsToWorld перестанет ловить эту поломку. При смене cursor_SkipsFinishedStands_AndShiftsToWorld проверь это утверждение.");
    }
}
