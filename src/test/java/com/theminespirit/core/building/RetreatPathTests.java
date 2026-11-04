package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.Cell;
import com.theminespirit.core.mining.SafetyView;
import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class RetreatPathTests {
    private static final BlockCoord NOOK = cell(1, 0, 1);
    private static final BlockCoord OPENING_LOW = cell(1, 0, 0);
    private static final BlockCoord OPENING_HIGH = cell(1, 1, 0);
    private static final Predicate<BlockCoord> OUTSIDE =
            feet -> feet.x() < 0 || feet.x() > 2 || feet.z() < 0 || feet.z() > 2;

    private static BlockCoord cell(int x, int y, int z) {
        return new BlockCoord(x, y, z);
    }

    private static SafetyView nook(Set<BlockCoord> extra) {
        Set<BlockCoord> solid = new HashSet<>(extra);
        for (int y = 0; y <= 1; y++) {
            for (int z = 0; z <= 2; z++) {
                solid.add(cell(0, y, z));
                solid.add(cell(2, y, z));
            }
            solid.add(cell(1, y, 2));
        }
        return coord -> coord.y() < 0 || solid.contains(coord) ? Cell.SOLID : Cell.PASSABLE;
    }

    @Test
    void allows_PlugSealsNookFromInside_Refuses() {
        assertFalse(
                RetreatPath.allows(NOOK, Set.of(OPENING_LOW, OPENING_HIGH), nook(Set.of()), OUTSIDE),
                "Дух в нише не ставит пробку проёма изнутри: после неё к выходу не пройти — замуровал бы себя");
    }

    @Test
    void allows_TopOfOpeningLeavesOneHighGap_Refuses() {
        assertFalse(
                RetreatPath.allows(NOOK, Set.of(OPENING_HIGH), nook(Set.of()), OUTSIDE),
                "Верх проёма оставляет щель в один блок: тело высотой 2 в неё не пройдёт — путь отхода перекрыт");
    }

    @Test
    void allows_BottomOfOpeningLeavesStepUp_Allows() {
        assertTrue(
                RetreatPath.allows(NOOK, Set.of(OPENING_LOW), nook(Set.of()), OUTSIDE),
                "Низ проёма — ступенька: на неё шагнуть и выйти верхом проёма, путь отхода остаётся");
    }

    @Test
    void allows_LastBlockFromOutside_Allows() {
        assertTrue(
                RetreatPath.allows(cell(1, 0, -1), Set.of(OPENING_LOW, OPENING_HIGH), nook(Set.of()), OUTSIDE),
                "Снаружи пробку ставить можно: дух уже за выходом");
    }

    @Test
    void allows_AlreadyWalledIn_DoesNotBlockWork() {
        SafetyView sealed = nook(Set.of(OPENING_HIGH));
        assertTrue(
                RetreatPath.allows(NOOK, Set.of(OPENING_LOW), sealed, OUTSIDE),
                "Выхода нет и до установки: блок ничего не меняет, правило не останавливает стройку");
    }

    @Test
    void walkable_PitDeeperThanStep_NotOffered() {
        Set<BlockCoord> solid = new HashSet<>();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                solid.add(cell(x, -1, z));
            }
        }
        for (int y = -1; y >= -3; y--) {
            for (int x = 2; x <= 3; x++) {
                for (int z = -1; z <= 1; z++) {
                    solid.remove(cell(x, y, z));
                }
            }
        }
        solid.add(cell(2, -4, -1));
        solid.add(cell(2, -4, 0));
        solid.add(cell(2, -4, 1));
        solid.add(cell(3, -4, 1));
        SafetyView view = coord -> solid.contains(coord) || coord.y() < -4 ? Cell.SOLID : Cell.PASSABLE;
        Predicate<BlockCoord> near = cell -> Math.abs(cell.x()) <= 4 && Math.abs(cell.z()) <= 4;

        Set<BlockCoord> open = RetreatPath.walkable(cell(0, 0, 0), view, near).orElseThrow();

        assertFalse(
                open.contains(cell(2, -3, 0)),
                "Спуск в яму глубже одного блока — ловушка: со дна нет обратного шага, стоянка не годится");
    }

    @Test
    void walkable_StepDownAndBackUp_Offered() {
        Set<BlockCoord> solid = new HashSet<>();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                solid.add(cell(x, -1, z));
                solid.add(cell(x, -2, z));
            }
        }
        solid.remove(cell(1, -1, 0));
        SafetyView view = coord -> solid.contains(coord) || coord.y() < -2 ? Cell.SOLID : Cell.PASSABLE;
        Predicate<BlockCoord> near = cell -> Math.abs(cell.x()) <= 4 && Math.abs(cell.z()) <= 4;

        Set<BlockCoord> open = RetreatPath.walkable(cell(0, 0, 0), view, near).orElseThrow();

        assertTrue(
                open.contains(cell(1, -1, 0)),
                "Ступенька вниз на один блок проходима в обе стороны: со стоянки есть выход обратно");
    }

    @Test
    void walkable_RoofOrSealedCell_NotOffered() {
        Predicate<BlockCoord> near = cell -> Math.abs(cell.x()) <= 4 && Math.abs(cell.z()) <= 4;
        Set<BlockCoord> open = RetreatPath.walkable(NOOK, nook(Set.of()), near).orElseThrow();
        assertTrue(open.contains(cell(1, 0, -2)), "Из открытой ниши дух доходит наружу: стоянки снаружи доступны");
        assertFalse(
                open.contains(cell(0, 2, 1)),
                "Верх стены (как скат крыши) не стоянка: на него не зайти, подход к нему — срыв");
        Set<BlockCoord> sealed = RetreatPath.walkable(NOOK, nook(Set.of(OPENING_LOW, OPENING_HIGH)), near)
                .orElseThrow();
        assertFalse(sealed.contains(cell(1, 0, -2)), "Из запертой ниши наружу не дойти: внешние стоянки не годятся");
    }
}
