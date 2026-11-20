package com.theminespirit.core.farming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.farming.api.CropKind;
import com.theminespirit.core.mining.api.Cell;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FarmPlannerTests {
    static final BlockCoord FROM = new BlockCoord(-2, 1, -2);
    static final FarmTile MATURE_WHEAT =
            new FarmTile(Ground.FARMLAND, Above.CROP, Optional.of(new CropState(CropKind.WHEAT, 7)));
    static final FarmTile YOUNG_WHEAT =
            new FarmTile(Ground.FARMLAND, Above.CROP, Optional.of(new CropState(CropKind.WHEAT, 3)));
    static final FarmTile DIRT_AIR = new FarmTile(Ground.DIRT, Above.AIR, Optional.empty());

    static MapFarmView wheatBed() {
        MapFarmView farm = new MapFarmView();
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                farm.set(x, 0, z, (x + z) % 2 == 0 ? MATURE_WHEAT : YOUNG_WHEAT);
            }
        }
        return farm;
    }

    static CountingSafety openField() {
        return new CountingSafety().fillFloor(0, -6, -6, 10, 10);
    }

    static Hydration channelWater() {
        Set<BlockCoord> water = new HashSet<>();
        for (int x = 0; x < 5; x++) {
            water.add(new BlockCoord(x, 0, 6));
        }
        return Hydration.of(water);
    }

    static List<BlockCoord> bedCells() {
        List<BlockCoord> cells = new ArrayList<>();
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                cells.add(new BlockCoord(x, 0, z));
            }
        }
        return cells;
    }

    private static long distanceSquared(BlockCoord from, BlockCoord to) {
        long dx = (long) to.x() - from.x();
        long dy = (long) to.y() - from.y();
        long dz = (long) to.z() - from.z();
        return dx * dx + dy * dy + dz * dz;
    }

    @Test
    void next_HarvestOnMixedBed_PicksOnlyMatureCrop() {
        MapFarmView farm = wheatBed();

        FarmPlanner.Outcome outcome =
                FarmPlanner.next(FarmJob.HARVEST, CropKind.ANY, FROM, bedCells(), farm, openField(), channelWater());

        assertTrue(
                outcome instanceof FarmPlanner.Next next
                        && farm.peek(next.target()).crop().orElseThrow().isMature(),
                "HARVEST берёт только зрелую клетку рядом с недозрелой: недозрелое не трогается, иначе дух снимает "
                        + "зелёный колос и урожай теряется. При смене FarmPlanner.next проверь "
                        + "FarmJobHarvestTests.eligible_JobTileTable_MatchesContract. Итог: " + outcome);
    }

    @Test
    void next_HarvestOnBed_StandPrefersDirtOverCloserFarmland() {
        MapFarmView farm = new MapFarmView().set(0, 0, 0, MATURE_WHEAT);
        BlockCoord from = new BlockCoord(5, 1, 0);
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                for (int y = 0; y <= 1; y++) {

                    long distance = distanceSquared(from, new BlockCoord(x, y + 1, z));
                    if ((x != 0 || z != 0) && distance < 16 && !(x == 1 && y == 0 && z == 0)) {
                        farm.set(x, y, z, new FarmTile(Ground.FARMLAND, Above.AIR, Optional.empty()));
                    }
                }
            }
        }
        farm.set(1, 0, 0, DIRT_AIR);

        FarmPlanner.Outcome outcome = FarmPlanner.next(
                FarmJob.HARVEST,
                CropKind.ANY,
                from,
                List.of(new BlockCoord(0, 0, 0)),
                farm,
                openField(),
                channelWater());

        assertEquals(
                new FarmPlanner.Next(new BlockCoord(0, 0, 0), new BlockCoord(1, 1, 0)),
                outcome,
                "Стоянка предпочитает землю пашне даже ценой пути (квадрат 16 против 4): дух не топчет farmland, "
                        + "а выбор стоянки не зависит от точки духа, иначе грядка вытаптывается. При смене выбора "
                        + "стоянки проверь FarmStandSpotsTests.of_DirtStand_IsPreferredOverStrictlyCloserFarmland. "
                        + "Итог: " + outcome);
    }

    @Test
    void next_TillNearChannel_FindsWateredTarget() {
        MapFarmView farm = new MapFarmView().fillBed(0, 0, 0, 4, 1, DIRT_AIR).fillBed(0, 0, 3, 4, 4, DIRT_AIR);
        Set<BlockCoord> water = new HashSet<>();
        for (int x = 0; x < 5; x++) {
            water.add(new BlockCoord(x, 0, 2));
        }
        List<BlockCoord> candidates = new ArrayList<>();
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                if (z != 2) {
                    candidates.add(new BlockCoord(x, 0, z));
                }
            }
        }

        FarmPlanner.Outcome outcome =
                FarmPlanner.next(FarmJob.TILL, CropKind.ANY, FROM, candidates, farm, openField(), Hydration.of(water));

        assertTrue(
                outcome instanceof FarmPlanner.Next,
                "Грядка с водным каналом посередине обязана пахаться: вода в 2 клетках достаёт каждый ряд, иначе "
                        + "вспашка не начинается и поле пустое. При смене Hydration проверь "
                        + "HydrationTests.watered_WaterAtDistanceFourSameLevel_IsWatered. Итог: " + outcome);
    }

    @Test
    void next_StandAtMaxOffsetBeyondThree_BeatsCloserTarget() {
        MapFarmView farm = new MapFarmView().set(2, 0, 5, DIRT_AIR).set(1, 0, 5, DIRT_AIR);
        CountingSafety safety = new CountingSafety().set(4, 0, 6, Cell.SOLID);
        BlockCoord from = new BlockCoord(6, 1, 7);

        FarmPlanner.Outcome outcome = FarmPlanner.next(
                FarmJob.TILL,
                CropKind.ANY,
                from,
                List.of(
                        new BlockCoord(2, 0, 5),
                        new BlockCoord(1, 0, 5),
                        new BlockCoord(4, 0, 2),
                        new BlockCoord(-2, 0, -3)),
                farm,
                safety,
                Hydration.of(Set.of(new BlockCoord(2, 0, 5))));

        assertEquals(
                new FarmPlanner.Next(new BlockCoord(1, 0, 5), new BlockCoord(4, 1, 6)),
                outcome,
                "Дальняя цель со стоянкой на смещении √11 обязана побеждать: ранний выход вычитает наибольшее "
                        + "смещение стоянки √11 ≈ 3.32, а не радиус перебора 3, иначе ближняя цель выигрывает и "
                        + "дальняя не пашется. При смене раннего выхода проверь "
                        + "FarmPlannerProperties.next_DenseTillWorld_MatchesFullScanWithoutEarlyExit. Итог: "
                        + outcome);
    }

    @Test
    void next_TieOnOneLine_FarTargetWithSameStandDistanceWins() {
        BlockCoord from = new BlockCoord(0, 5, 0);
        BlockCoord close = new BlockCoord(9, 0, 3);
        BlockCoord far = new BlockCoord(5, 0, 15);

        BlockCoord farStand = new BlockCoord(4, 1, 12);
        MapFarmView farm = new MapFarmView().set(9, 0, 3, DIRT_AIR).set(5, 0, 15, DIRT_AIR);
        CountingSafety safety = new CountingSafety().set(12, 0, 4, Cell.SOLID).set(4, 0, 12, Cell.SOLID);
        Hydration hydration = Hydration.of(Set.of(new BlockCoord(9, 0, 3), new BlockCoord(5, 0, 15)));

        FarmPlanner.Outcome outcome =
                FarmPlanner.next(FarmJob.TILL, CropKind.ANY, from, List.of(far, close), farm, safety, hydration);

        assertEquals(
                new FarmPlanner.Next(far, farStand),
                outcome,
                "Ничья на одной прямой обязана достаться дальней цели (5,0,15) со стоянкой (4,1,12): dist²=275, "
                        + "best=176, gap² отличается на 5.7e-14 < допуска 1e-9, строгий знак больше пропускает "
                        + "цель и иначе оставляет ближнюю (9,0,3), а дальняя клетка теряется. При смене сравнения "
                        + "в FarmPlanner.next проверь FarmPlannerProperties.next_RandomInput_IndependentOfCandidateOrder. "
                        + "Итог: " + outcome);
    }

    @Test
    void next_LineOf256Targets_ReadsStayWithinEightPerTarget() {
        MapFarmView farm = new MapFarmView();
        CountingSafety safety = new CountingSafety().fillFloor(2, -5, -5, 260, 5);
        Set<BlockCoord> water = new HashSet<>();
        List<BlockCoord> targets = new ArrayList<>(256);
        for (int x = 0; x < 256; x++) {
            farm.set(x, 2, 0, DIRT_AIR);
            water.add(new BlockCoord(x, 2, 0));
            targets.add(new BlockCoord(x, 2, 0));
        }

        FarmPlanner.Outcome outcome = FarmPlanner.next(
                FarmJob.TILL, CropKind.ANY, new BlockCoord(0, 3, 0), targets, farm, safety, Hydration.of(water));

        assertTrue(
                outcome instanceof FarmPlanner.Next,
                "Каждая цель линии даёт стоянку с пола: планирование обязано выбрать цель, а не выйти Exhausted, "
                        + "иначе линия поля не пашется. При смене раннего выхода проверь бюджет ниже в тесте");
        assertTrue(
                safety.reads() <= 324 * 8L,
                "Ранний выход обязан читать целиком не больше 8 целей: стоянка дальней цели не ближе "
                        + "(dist(from,target) − √11), и дальше перебирать незачем, иначе перебор 256 целей кладёт "
                        + "серверный тик. При смене бюджета проверь "
                        + "FarmPlannerProperties.next_RandomInput_SafetyReadsBoundedByTargetCount. Чтений: "
                        + safety.reads() + ", предел: " + 324 * 8L);
    }
}
