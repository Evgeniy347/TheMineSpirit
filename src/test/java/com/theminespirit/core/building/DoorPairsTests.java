package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DoorPairsTests {
    @Test
    void problem_TwoAdjacentDoorsWithCorrectHinges_IsAccepted() {
        Optional<String> problem =
                DoorPairs.problem(schematic(door(0, 0, 0, "south", "right"), door(1, 0, 0, "south", "left")));

        assertFalse(
                problem.isPresent(),
                "Две створки с петлями по краям проёма — это правильная двойная дверь, сторож не должен ругаться: "
                        + problem);
    }

    @Test
    void problem_TwoAdjacentDoorsLookOppositeWays_IsReported() {
        Optional<String> problem =
                DoorPairs.problem(schematic(door(0, 0, 0, "south", "right"), door(1, 0, 0, "east", "left")));

        assertTrue(problem.isPresent(), "Две двери рядом обязаны смотреть в одну сторону, иначе это не дверная пара");
    }

    @Test
    void problem_ThreeDoorsInARow_IsReported() {
        Optional<String> problem = DoorPairs.problem(schematic(
                door(0, 0, 0, "south", "right"), door(1, 0, 0, "south", "right"), door(2, 0, 0, "south", "left")));

        assertTrue(
                problem.isPresent(),
                "Ряд из трёх створок не делится на пары: у последней двери нет пары, сторож обязан это назвать");
    }

    private static Schematic schematic(Schematic.Entry... doors) {
        List<Schematic.Entry> blocks = new ArrayList<>(List.of(doors));
        return new Schematic(3, 1, 1, blocks);
    }

    private static Schematic.Entry door(int x, int y, int z, String facing, String hinge) {
        return new Schematic.Entry(
                x, y, z, "minecraft:oak_door", Map.of("facing", facing, "hinge", hinge, "half", "lower"), "");
    }
}
