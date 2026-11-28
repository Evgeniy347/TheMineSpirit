package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.town.Placement;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BuildingAuditInputsTests {
    @Test
    void buildingAuditInputs_PlacementScheme_UsesStageLevel() {
        Placement place = new Placement("storage", "Склад", 2, 0, 0, 1, 1, Placement.Facing.NORTH);

        assertEquals(
                Optional.of("storage_v2"),
                BuildingAuditInputs.scheme(place),
                "Сводка склада второго этапа обязана брать storage_v2: иначе она сравнивает мир с storage_v1"
                        + " и /v1/world показывает unknown. При смене уровней перепроверь buildings.json и"
                        + " BuildingAuditInputs.scheme.");
    }
}
