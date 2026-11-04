package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StandPathGateTests {
    private static final BlockCoord STAND = new BlockCoord(-167, 69, -441);
    private static final BlockCoord PIT = new BlockCoord(-167, 58, -441);

    @Test
    void usable_NearStandOutsideWalkablePit_Refused() {
        Optional<Set<BlockCoord>> walkable = Optional.of(Set.of(new BlockCoord(-166, 69, -441)));
        assertFalse(
                StandPathGate.usable(true, true, walkable, PIT),
                "Стоянка в яме ниже площадки не входит в обратимый обход от духа: подходить к ней нельзя, "
                        + "иначе он уйдёт на y=58 при площадке y=69 и выхода не найдёт");
    }

    @Test
    void usable_NearStandInWalkableSet_Accepted() {
        assertTrue(
                StandPathGate.usable(true, true, Optional.of(Set.of(STAND)), STAND),
                "Стоянка, до которой дух доходит и возвращается пешком, годится");
    }

    @Test
    void usable_NearStandWithoutFloor_Refused() {
        assertFalse(
                StandPathGate.usable(false, true, Optional.of(Set.of(STAND)), STAND),
                "Стоянка без пола в живом мире (под ней воздух) не годится, даже если план её предполагал");
    }

    @Test
    void usable_FarStandNotChecked_Accepted() {
        assertTrue(
                StandPathGate.usable(true, false, Optional.of(Set.of()), STAND),
                "Издалека набор обхода вокруг площадки ничего не знает о стоянке: путь проверит подход, а не отказ");
    }

    @Test
    void usable_NearStandWalkableUnknown_Accepted() {
        assertTrue(
                StandPathGate.usable(true, true, Optional.empty(), STAND),
                "Обход не построился (предел узлов) — это «неизвестно», а не «пути нет»: стоянку не отбрасываем");
    }
}
