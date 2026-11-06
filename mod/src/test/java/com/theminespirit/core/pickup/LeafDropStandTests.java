package com.theminespirit.core.pickup;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.board.WorkStand;
import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LeafDropStandTests {
    private static final int NO_GROUND = Integer.MIN_VALUE;
    private static final BlockCoord LEAF = new BlockCoord(5, 10, 7);

    @Test
    void of_LeafAboveGround_TargetIsGroundUnderLeaf() {
        WorkStand.Ground ground = ground(0, Set.of());

        assertEquals(
                Optional.of(new BlockCoord(5, 0, 7)),
                LeafDropStand.of(LEAF, ground),
                "Цель сбора саженцев под гниющей листвой — стоянка на земле под ней, а не клетка листвы: goto в полог "
                        + "невыполним, дух застревает в петле переносов и задание уходит в BLOCKED");
    }

    @Test
    void of_LeafColumnOccupied_NearestGroundColumn() {
        WorkStand.Ground ground = ground(0, Set.of(new BlockCoord(5, 0, 7)));

        assertEquals(
                Optional.of(new BlockCoord(4, 0, 7)),
                LeafDropStand.of(LEAF, ground),
                "Если под листвой стоит ствол или куст, цель — ближайшая свободная стоянка рядом: дух не встаёт в блок");
    }

    @Test
    void of_NoGroundWithinRadius_Empty() {
        WorkStand.Ground ground = ground(NO_GROUND, Set.of());

        assertEquals(
                Optional.empty(),
                LeafDropStand.of(LEAF, ground),
                "Нет земли в радиусе — цели нет, событие не ставит задание: иначе снова появляется недостижимый "
                        + "goto в полог и петля переносов");
    }

    private static WorkStand.Ground ground(int feet, Set<BlockCoord> occupied) {
        return new WorkStand.Ground() {
            @Override
            public int feet(int x, int z) {
                return feet;
            }

            @Override
            public boolean free(BlockCoord at) {
                return at.y() != NO_GROUND && !occupied.contains(at);
            }
        };
    }
}
