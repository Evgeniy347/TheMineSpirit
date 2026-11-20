package com.theminespirit.core.farming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.farming.api.CropKind;
import com.theminespirit.core.mining.api.Cell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FarmPlannerExhaustTests {
    private static final BlockCoord FROM = FarmPlannerTests.FROM;

    static final class CountingLookups extends AbstractSet<BlockCoord> {
        int lookups;
        private final Set<BlockCoord> cells;

        CountingLookups(Set<BlockCoord> cells) {
            this.cells = cells;
        }

        @Override
        public boolean contains(Object cell) {
            lookups++;
            return cells.contains(cell);
        }

        @Override
        public Iterator<BlockCoord> iterator() {
            return cells.iterator();
        }

        @Override
        public int size() {
            return cells.size();
        }
    }

    @Test
    void maxSafetyReadsPerTarget_SearchBoxVolume_Is324() {
        assertEquals(
                324,
                FarmPlanner.MAX_SAFETY_READS_PER_TARGET,
                "Предел чтений на цель обязан равняться 324 (ящик стоянки 9×4×9): поднятая константа тихо ослабит "
                        + "тесты масштаба, иначе поле кладёт тик. При смене FarmPlanner.MAX_SAFETY_READS_PER_TARGET "
                        + "проверь next_LineOf256Targets_ReadsStayWithinEightPerTarget");
    }

    @Test
    void next_TillIneligibleTiles_WaterNeverChecked() {
        MapFarmView farm = new MapFarmView().fillBed(0, 0, 0, 4, 4, FarmPlannerTests.MATURE_WHEAT);
        CountingLookups water = new CountingLookups(Set.of(new BlockCoord(0, 0, 0)));

        FarmPlanner.Outcome outcome = FarmPlanner.next(
                FarmJob.TILL,
                CropKind.ANY,
                FROM,
                FarmPlannerTests.bedCells(),
                farm,
                FarmPlannerTests.openField(),
                Hydration.of(water));

        assertEquals(
                new FarmPlanner.Exhausted(25, 0, 0),
                outcome,
                "Грядка с посевами не пашется: все 25 клеток считаются в ineligible, иначе стратег не поймёт, "
                        + "почему вспашка встала. При смене учёта в FarmPlanner.next проверь "
                        + "next_DryDirtBed_ReturnsDryNotIneligible");
        assertEquals(
                0,
                water.lookups,
                "Непригодная без воды клетка отсеивается до проверки увлажнения: иначе каждая такая клетка стоит "
                        + "162 обращения к множеству воды и тик страдает. При смене порядка проверок проверь "
                        + "FarmPlannerTests.next_TillNearChannel_FindsWateredTarget. Обращений: " + water.lookups);
    }

    @Test
    void next_OverlappingTargets_EachCellReadOnce() {
        MapFarmView farm =
                new MapFarmView().set(0, 0, 0, FarmPlannerTests.DIRT_AIR).set(1, 0, 0, FarmPlannerTests.DIRT_AIR);
        CountingSafety safety = FarmPlannerTests.openField();
        Set<BlockCoord> water = Set.of(new BlockCoord(0, 0, 0), new BlockCoord(1, 0, 0));

        FarmPlanner.Outcome outcome = FarmPlanner.next(
                FarmJob.TILL,
                CropKind.ANY,
                FROM,
                List.of(new BlockCoord(0, 0, 0), new BlockCoord(1, 0, 0)),
                farm,
                safety,
                Hydration.of(water));

        assertTrue(
                outcome instanceof FarmPlanner.Next,
                "Соседние цели обязаны давать стоянку: иначе нечего проверять на пересекающихся окрестностях и "
                        + "тест масштаба лжёт. При смене FarmPlanner.next проверь строку ниже в этом тесте");
        assertEquals(
                1,
                safety.maxReadsPerCell(),
                "Каждая клетка читается не более раза на весь перебор целей: окрестности соседних целей "
                        + "пересекаются, иначе повторные чтения кладут серверный тик. При смене кэша проверь "
                        + "FarmStandSpotsTests.of_CachedView_ReadsStayWithinBound. Всего чтений: " + safety.reads());
    }

    @Test
    void next_TargetSurroundedByLava_ReturnsNoStand() {
        MapFarmView farm = new MapFarmView().fillBed(0, -5, -5, 5, 5, FarmPlannerTests.DIRT_AIR);
        CountingSafety safety = FarmPlannerTests.openField().fill(1, -4, -4, 4, 4, Cell.LAVA);

        FarmPlanner.Outcome outcome = FarmPlanner.next(
                FarmJob.TILL,
                CropKind.ANY,
                FROM,
                List.of(new BlockCoord(0, 0, 0)),
                farm,
                safety,
                Hydration.of(Set.of(new BlockCoord(1, 0, 1))));

        assertEquals(
                new FarmPlanner.Exhausted(0, 1, 0),
                outcome,
                "Цель в лавовом кольце обязана считаться в noStand: стоянки рядом с лавой отвергаются все, "
                        + "иначе дух погибнет на подходе. При смене проверки опасностей проверь "
                        + "FarmStandSpotsTests.of_HazardNeighbor_RejectsAdjacentStand");
    }

    @Test
    void next_WalledTarget_ReturnsNoStand() {
        MapFarmView farm = new MapFarmView().set(0, 0, 0, FarmPlannerTests.DIRT_AIR);
        CountingSafety safety =
                FarmPlannerTests.openField().fill(1, -4, -4, 4, 4, Cell.SOLID).fill(2, -4, -4, 4, 4, Cell.SOLID);

        FarmPlanner.Outcome outcome = FarmPlanner.next(
                FarmJob.TILL,
                CropKind.ANY,
                FROM,
                List.of(new BlockCoord(0, 0, 0)),
                farm,
                safety,
                Hydration.of(Set.of(new BlockCoord(1, 0, 1))));

        assertEquals(
                new FarmPlanner.Exhausted(0, 1, 0),
                outcome,
                "Замурованная цель обязана считаться в noStand: без числа непонятно, почему работа встала, иначе "
                        + "стратег повторяет вызов вслепую. При смене FarmPlanner.next проверь "
                        + "FarmStandSpotsTests.of_WalledTarget_ReturnsEmpty");
    }

    @Test
    void next_DryDirtBed_ReturnsDryNotIneligible() {
        MapFarmView farm = new MapFarmView().fillBed(0, 0, 0, 4, 4, FarmPlannerTests.DIRT_AIR);

        FarmPlanner.Outcome outcome = FarmPlanner.next(
                FarmJob.TILL,
                CropKind.ANY,
                FROM,
                FarmPlannerTests.bedCells(),
                farm,
                FarmPlannerTests.openField(),
                Hydration.of(Set.of()));

        assertEquals(
                new FarmPlanner.Exhausted(0, 0, 25),
                outcome,
                "Сухая земля не пашется: все 25 клеток считаются в dry, а не в ineligible, иначе стратег понесёт "
                        + "не тот ресурс. При смене учёта проверь HydrationTests.watered_NoWater_IsDry");
    }
}
