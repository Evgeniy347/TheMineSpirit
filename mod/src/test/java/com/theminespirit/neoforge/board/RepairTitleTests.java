package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.board.api.BoardCase;
import org.junit.jupiter.api.Test;

class RepairTitleTests {
    @Test
    void of_KnownTownBuilding_ReturnsRussianName() {
        assertEquals(
                "Хижина",
                RepairTitle.of("hut", BoardCase.BUILD_HOUSE),
                "Карточка ремонта называет объект русским именем из town.json, а не техническим id"
                        + " Иначе тест of_KnownTownBuilding_ReturnsRussianName перестанет ловить эту поломку. При смене of_KnownTownBuilding_ReturnsRussianName проверь это утверждение.");
    }

    @Test
    void of_StageBuildingId_FallsBackToStageLabel() {
        assertEquals(
                "Сад",
                RepairTitle.of("build_orchard", BoardCase.BUILD_ORCHARD),
                "Id задачи этапа не совпадает с id участка: подпись берётся из ярлыка шага"
                        + " Иначе тест of_StageBuildingId_FallsBackToStageLabel перестанет ловить эту поломку. При смене of_StageBuildingId_FallsBackToStageLabel проверь это утверждение.");
    }
}
