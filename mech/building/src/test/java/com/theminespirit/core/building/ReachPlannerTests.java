package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReachPlannerTests {
    private static final SpiritReach REACH = new SpiritReach(3, 15, 3);

    private static BlockCoord cell(int x, int y, int z) {
        return new BlockCoord(x, y, z);
    }

    @Test
    void workableNow_ReachableReadyCells_KeepCallerOrder() {
        ReachPlanner.World world = new SetWorld(Set.of(cell(2, 3, 0), cell(0, 2, 0), cell(1, 1, 0)), Set.of());
        List<BlockCoord> targets = List.of(cell(1, 1, 0), cell(0, 2, 0), cell(2, 3, 0));

        assertEquals(
                targets,
                ReachPlanner.workableNow(REACH, cell(0, 1, 1), targets, world),
                "С места делаются все досягаемые готовые клетки в порядке, который задал вызывающий (снизу вверх "
                        + "для постановки, сверху вниз для очистки): иначе верхний блок повиснет без опоры");
    }

    @Test
    void workableNow_NotReadyOutOfReachOrInsideBody_Skipped() {
        BlockCoord feet = cell(0, 1, 0);
        List<BlockCoord> targets = List.of(cell(0, 1, 0), cell(0, 2, 0), cell(4, 1, 0), cell(1, 2, 0), cell(1, 1, 0));
        ReachPlanner.World world =
                new SetWorld(Set.of(cell(0, 1, 0), cell(0, 2, 0), cell(4, 1, 0), cell(1, 2, 0)), Set.of());

        assertEquals(
                List.of(cell(1, 2, 0)),
                ReachPlanner.workableNow(REACH, feet, targets, world),
                "С места делается только клетка вне тела, в досягаемости и готовая (с опорой): блок в теле духа, "
                        + "в четырёх шагах или без опоры ставить нельзя");
    }

    @Test
    void nextStand_WallOfSeven_CenterCoversWholeWall() {
        List<BlockCoord> wall = List.of(
                cell(0, 1, 0),
                cell(1, 1, 0),
                cell(2, 1, 0),
                cell(3, 1, 0),
                cell(4, 1, 0),
                cell(5, 1, 0),
                cell(6, 1, 0));
        ReachPlanner.World world = new SetWorld(Set.copyOf(wall), standableAround(1, 0));

        Optional<BlockCoord> stand =
                ReachPlanner.nextStand(REACH, cell(3, -10, 0), wall, world, Set.of(), Set.copyOf(wall));

        assertEquals(
                Optional.of(cell(3, 1, -1)),
                stand,
                "Стоянка обязана накрывать всю стену: центр стены в 7 клеток ставит её с места, а не за четыре "
                        + "перехода по краям");
    }

    @Test
    void nextStand_EqualCoverage_PicksNearest() {
        List<BlockCoord> wall = List.of(cell(0, 1, 0), cell(1, 1, 0), cell(2, 1, 0), cell(3, 1, 0), cell(4, 1, 0));
        ReachPlanner.World world = new SetWorld(Set.copyOf(wall), standableAround(1, 0));

        Optional<BlockCoord> stand =
                ReachPlanner.nextStand(REACH, cell(0, 1, 5), wall, world, Set.of(), Set.copyOf(wall));

        assertEquals(
                Optional.of(cell(1, 1, 3)),
                stand,
                "При равном охвате выбирается стоянка ближе к духу: иначе он бежит через всю площадку ради той же "
                        + "стройки");
    }

    @Test
    void nextStand_NearUsefulAndFarBiggerGroups_PicksNearUseful() {
        List<BlockCoord> cells = new ArrayList<>();
        for (int x = 0; x <= 5; x++) {
            cells.add(cell(x, 1, 0));
        }
        for (int x = 20; x <= 26; x++) {
            cells.add(cell(x, 1, 0));
        }
        Set<BlockCoord> standable = new HashSet<>();
        for (int x = -4; x <= 30; x++) {
            standable.add(cell(x, 1, 1));
            standable.add(cell(x, 1, 2));
            standable.add(cell(x, 1, 3));
        }
        ReachPlanner.World world = new SetWorld(Set.copyOf(cells), standable);

        Optional<BlockCoord> stand =
                ReachPlanner.nextStand(REACH, cell(0, 1, 3), cells, world, Set.of(), Set.copyOf(cells));

        assertTrue(
                stand.isPresent() && stand.get().x() <= 8,
                "С ближней стоянки ставится 6 клеток (не меньше половины лучших 7) — она и выбирается, а не дальняя "
                        + "в 20 блоках ради одной лишней клетки; фактически " + stand);
    }

    @Test
    void nextStand_SchemeCellsAndCurrentSpot_NotReturned() {
        List<BlockCoord> wall = List.of(cell(0, 1, 0), cell(1, 1, 0), cell(2, 1, 0), cell(3, 1, 0));
        BlockCoord feet = cell(1, 1, 1);
        Set<BlockCoord> standable = new HashSet<>(standableAround(1, 0));
        standable.addAll(wall);
        ReachPlanner.World world = new SetWorld(Set.copyOf(wall), standable);

        Optional<BlockCoord> stand = ReachPlanner.nextStand(REACH, feet, wall, world, Set.of(), Set.copyOf(wall));

        assertTrue(stand.isPresent(), "Рядом есть свободная земля: стоянка обязана найтись");
        assertFalse(
                wall.contains(stand.get()),
                "Стоянка не может быть клеткой схемы: дух окажется в будущем блоке, фактически " + stand);
        assertFalse(
                stand.get().equals(feet),
                "Следующая стоянка не может совпадать с текущей: иначе подход не начнётся, фактически " + stand);
    }

    @Test
    void nextStand_ExcludedSpot_SecondBestChosen() {
        List<BlockCoord> wall = List.of(cell(0, 1, 0), cell(1, 1, 0), cell(2, 1, 0), cell(3, 1, 0));
        ReachPlanner.World world = new SetWorld(Set.copyOf(wall), standableAround(1, 0));
        BlockCoord best = ReachPlanner.nextStand(REACH, cell(0, 1, 5), wall, world, Set.of(), Set.copyOf(wall))
                .orElseThrow();

        Optional<BlockCoord> second =
                ReachPlanner.nextStand(REACH, cell(0, 1, 5), wall, world, Set.of(best), Set.copyOf(wall));

        assertTrue(
                second.isPresent() && !second.get().equals(best),
                "Недостижимая стоянка исключается, и планировщик предлагает следующую: иначе дух повторяет тот же "
                        + "подход, фактически " + second);
    }

    @Test
    void nextStand_NothingReady_CoversLowestLayer() {
        List<BlockCoord> tower = List.of(cell(0, 1, 0), cell(0, 2, 0), cell(10, 5, 0));
        ReachPlanner.World world = new SetWorld(Set.of(), standableAround(1, 0));

        Optional<BlockCoord> stand =
                ReachPlanner.nextStand(REACH, cell(20, 1, 20), tower, world, Set.of(), Set.copyOf(tower));

        assertTrue(
                stand.isPresent() && REACH.reaches(stand.get(), cell(0, 1, 0)),
                "Если готовых клеток нет, стоянка накрывает нижний слой: с него стройка начнётся, когда появится "
                        + "опора; фактически " + stand);
    }

    @Test
    void nextStandFromAfar_HighCellOnlyGroundBelow_StandsOnGroundWithinReachUp() {
        List<BlockCoord> shutters = List.of(cell(0, 8, 0), cell(0, 9, 0));
        ReachPlanner.World world = new SetWorld(Set.copyOf(shutters), standableAround(0, 0));

        Optional<BlockCoord> stand =
                ReachPlanner.nextStandFromAfar(REACH, cell(20, 0, 20), shutters, world, Set.of(), Set.copyOf(shutters));

        assertTrue(
                stand.isPresent() && REACH.reaches(stand.get(), cell(0, 9, 0)),
                "Ставня второго этажа в 9 блоках над землёй ставится снаружи с земли, раз досягаемость вверх 15: "
                        + "иначе последние верхние ставни остаются «ни с одной стоянки»; фактически " + stand);
    }

    @Test
    void nextStand_HighCellOnlyGroundBelow_NearSearchOnly() {
        List<BlockCoord> shutters = List.of(cell(0, 8, 0), cell(0, 9, 0));
        ReachPlanner.World world = new SetWorld(Set.copyOf(shutters), standableAround(0, 0));

        Optional<BlockCoord> stand =
                ReachPlanner.nextStand(REACH, cell(20, 0, 20), shutters, world, Set.of(), Set.copyOf(shutters));

        assertTrue(
                stand.isEmpty(),
                "Обычный поиск стоянки — не ниже 6 под клетками: дальний поиск внутри стройки (усадьба, 20.10) "
                        + "оставлял духа на мебели, и заходы к сундукам не удавались; фактически " + stand);
    }

    static Set<BlockCoord> standableAround(int y, int z) {
        Set<BlockCoord> cells = new HashSet<>();
        for (int x = -8; x <= 14; x++) {
            for (int dz = -8; dz <= 8; dz++) {
                if (dz != 0) {
                    cells.add(cell(x, y, z + dz));
                }
            }
        }
        return cells;
    }

    @Test
    void atOrAbove_BelowBuildBase_IsNotStandable() {
        ReachPlanner.World world = BuildStandLevel.atOrAbove(
                new SetWorld(
                        Set.of(cell(0, 66, 0), cell(0, 69, 0), cell(0, 70, 0)),
                        Set.of(cell(0, 66, 0), cell(0, 69, 0), cell(0, 70, 0))),
                69);

        assertFalse(
                world.standable(cell(0, 66, 0)), "стоянка ниже низа постройки — это яма: дух спустится и не выйдет");
        assertTrue(world.standable(cell(0, 69, 0)), "стоянка на низу постройки допустима");
        assertTrue(world.standable(cell(0, 70, 0)), "стоянка выше низа постройки допустима");
        assertTrue(world.ready(cell(0, 69, 0)), "ready делегируется исходному миру");
    }

    @Test
    void atOrAbove_SchemeCells_UsesLowestSchemeLayerAsBase() {
        ReachPlanner.World world = BuildStandLevel.atOrAbove(
                new SetWorld(Set.of(cell(0, 68, 0), cell(0, 70, 0)), Set.of(cell(0, 68, 0), cell(0, 70, 0))),
                List.of(cell(0, 70, 0), cell(0, 71, 0)));

        assertFalse(world.standable(cell(0, 68, 0)), "низ схемы — её нижний слой: стоянка ниже него не годится");
        assertTrue(world.standable(cell(0, 70, 0)), "стоянка на уровне нижнего слоя проходит");
    }

    @Test
    void allowed_StandBelowBase_IsRejectedAboveIsAccepted() {
        assertFalse(
                BuildStandLevel.allowed(cell(0, 66, 0), 69),
                "стоянка ниже низа постройки — это яма: дух спустится и не выйдет");
        assertTrue(BuildStandLevel.allowed(cell(0, 69, 0), 69), "стоянка на уровне низа допустима");
        assertTrue(BuildStandLevel.allowed(cell(0, 71, 0), 69), "стоянка выше низа допустима");
    }

    @Test
    void isAction_ActionPlacedCell_IsDetected() {
        assertTrue(
                BuildStandLevel.isAction(new Schematic.PlacedEntry(cell(0, 0, 0), "minecraft:dirt_path")),
                "дорожка ставится действием лопаты: схема обязана видеть её без предмета");
        assertFalse(
                BuildStandLevel.isAction(new Schematic.PlacedEntry(cell(0, 0, 0), "minecraft:stone")),
                "обычный блок ставится предметом, а не действием");
        assertFalse(BuildStandLevel.isAction(null), "пустая клетка схемы не является действием");
    }

    record SetWorld(Set<BlockCoord> ready, Set<BlockCoord> standable) implements ReachPlanner.World {
        @Override
        public boolean ready(BlockCoord cell) {
            return ready.contains(cell);
        }

        @Override
        public boolean standable(BlockCoord feet) {
            return standable.contains(feet);
        }
    }
}
