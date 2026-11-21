package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReachPlannerTestsPart2 {

    private static BlockCoord cell(int x, int y, int z) {
        return new BlockCoord(x, y, z);
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

    @Test
    void allowed_StandBelowBase_IsRejectedAboveIsAccepted() {
        assertFalse(
                BuildStandLevel.allowed(cell(0, 66, 0), 69),
                "стоянка ниже низа постройки — это яма: дух спустится и не выйдет"
                        + " Иначе тест allowed_StandBelowBase_IsRejectedAboveIsAccepted перестанет ловить эту поломку. При смене allowed_StandBelowBase_IsRejectedAboveIsAccepted проверь это утверждение.");
        assertTrue(
                BuildStandLevel.allowed(cell(0, 69, 0), 69),
                "стоянка на уровне низа допустима"
                        + " Иначе тест allowed_StandBelowBase_IsRejectedAboveIsAccepted перестанет ловить эту поломку. При смене allowed_StandBelowBase_IsRejectedAboveIsAccepted проверь это утверждение.");
        assertTrue(
                BuildStandLevel.allowed(cell(0, 71, 0), 69),
                "стоянка выше низа допустима"
                        + " Иначе тест allowed_StandBelowBase_IsRejectedAboveIsAccepted перестанет ловить эту поломку. При смене allowed_StandBelowBase_IsRejectedAboveIsAccepted проверь это утверждение.");
    }

    @Test
    void isAction_ActionPlacedCell_IsDetected() {
        assertTrue(
                BuildStandLevel.isAction(new Schematic.PlacedEntry(cell(0, 0, 0), "minecraft:dirt_path")),
                "дорожка ставится действием лопаты: схема обязана видеть её без предмета"
                        + " Иначе тест isAction_ActionPlacedCell_IsDetected перестанет ловить эту поломку. При смене isAction_ActionPlacedCell_IsDetected проверь это утверждение.");
        assertFalse(
                BuildStandLevel.isAction(new Schematic.PlacedEntry(cell(0, 0, 0), "minecraft:stone")),
                "обычный блок ставится предметом, а не действием"
                        + " Иначе тест isAction_ActionPlacedCell_IsDetected перестанет ловить эту поломку. При смене isAction_ActionPlacedCell_IsDetected проверь это утверждение.");
        assertFalse(
                BuildStandLevel.isAction(null),
                "пустая клетка схемы не является действием"
                        + " Иначе тест isAction_ActionPlacedCell_IsDetected перестанет ловить эту поломку. При смене isAction_ActionPlacedCell_IsDetected проверь это утверждение.");
    }
}
