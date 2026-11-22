package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StageSchemesTests {
    @Test
    void fenceStub_FirstStage_IsSingleFencePost() {
        assertEquals(
                "fence_post",
                StageSchemes.fenceStub(1),
                "Заглушка ограды этапа 1 — один столб: событие отмечает опасную колонку, а не строит кольцо 23×23"
                        + " Иначе тест fenceStub_FirstStage_IsSingleFencePost перестанет ловить эту поломку. При смене fenceStub_FirstStage_IsSingleFencePost проверь это утверждение.");
        assertEquals(
                "fence_post",
                StageSchemes.fenceStub(0),
                "До первого этапа действует та же заглушка этапа 1: иначе событие до старта этапа не построить"
                        + " При смене fenceStub_FirstStage_IsSingleFencePost проверь это утверждение.");
    }

    @Test
    void fenceStub_ThirdStage_IsSingleWallPost() {
        assertEquals(
                "wall_3_post",
                StageSchemes.fenceStub(3),
                "С третьего этапа ограда — стена: заглушка обязана быть стенным столбом той же схемы, а не "
                        + "деревянным забором из прошлого этапа"
                        + " Иначе тест fenceStub_ThirdStage_IsSingleWallPost перестанет ловить эту поломку. При смене fenceStub_ThirdStage_IsSingleWallPost проверь это утверждение.");
    }
}
