package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WalkableStandTests {
    private static final BlockCoord CHEST = new BlockCoord(-168, 74, -488);

    @Test
    void near_ChestInsideBuilding_PicksFloorInsideNotGroundOutside() {
        BlockCoord inside = new BlockCoord(-169, 73, -487);
        BlockCoord outside = new BlockCoord(-168, 70, -490);

        assertEquals(
                Optional.of(inside),
                WalkableStand.near(CHEST, Set.of(inside, outside)),
                "сундук внутри склада: стоянка на полу внутри, а не на земле за стеной"
                        + " Иначе тест near_ChestInsideBuilding_PicksFloorInsideNotGroundOutside перестанет ловить эту поломку. При смене near_ChestInsideBuilding_PicksFloorInsideNotGroundOutside проверь это утверждение.");
    }

    @Test
    void near_NothingInReach_IsEmpty() {
        assertTrue(
                WalkableStand.near(CHEST, Set.of(new BlockCoord(-168, 60, -488)))
                        .isEmpty(),
                "слишком далеко"
                        + " Иначе тест near_NothingInReach_IsEmpty перестанет ловить эту поломку. При смене near_NothingInReach_IsEmpty проверь это утверждение.");
    }
}
