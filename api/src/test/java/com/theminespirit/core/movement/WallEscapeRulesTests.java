package com.theminespirit.core.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class WallEscapeRulesTests {
    @Test
    void nearestFree_OwnCellUsable_PicksNeighbourNotItself() {
        BlockCoord feet = new BlockCoord(-173, 80, -492);
        Set<BlockCoord> usable = Set.of(feet, new BlockCoord(-174, 80, -492));
        Optional<BlockCoord> picked = WallEscapeRules.nearestFree(feet, around(feet), usable::contains);
        assertEquals(
                Optional.of(new BlockCoord(-174, 80, -492)),
                picked,
                "Клетка, в которой дух уже стоит, не годится для побега: он останется в стене и задохнётся");
    }

    @Test
    void nearestFree_OnlyOwnCellUsable_RefusesToLoop() {
        BlockCoord feet = new BlockCoord(-173, 80, -492);
        Optional<BlockCoord> picked = WallEscapeRules.nearestFree(feet, around(feet), cell -> cell.equals(feet));
        assertTrue(
                picked.isEmpty(),
                "Если свободна только собственная клетка, побег не начинается: шаг в себя — вечный цикл "
                        + "у стены и смерть от удушья");
    }

    @Test
    void nearestFree_NearestUsable_ComesFirst() {
        BlockCoord feet = new BlockCoord(0, 70, 0);
        Set<BlockCoord> usable = Set.of(new BlockCoord(2, 70, 0), new BlockCoord(1, 70, 0), new BlockCoord(0, 70, 2));
        Optional<BlockCoord> picked = WallEscapeRules.nearestFree(feet, around(feet), usable::contains);
        assertEquals(
                Optional.of(new BlockCoord(1, 70, 0)),
                picked,
                "Побег берёт ближайшую свободную клетку: дальняя тратит время, пока дух задыхается");
    }

    private static List<BlockCoord> around(BlockCoord feet) {
        return IntStream.rangeClosed(-2, 2)
                .boxed()
                .flatMap(dx -> IntStream.rangeClosed(-1, 2)
                        .boxed()
                        .flatMap(dy -> IntStream.rangeClosed(-2, 2)
                                .mapToObj(dz -> new BlockCoord(feet.x() + dx, feet.y() + dy, feet.z() + dz))))
                .toList();
    }
}
