package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StageSchemesTests {
    @Test
    void fenceStub_FirstStage_IsSingleFencePost() {
        assertEquals(
                "fence_post",
                StageSchemes.fenceStub(1),
                "Заглушка ограды этапа 1 — один столб: событие отмечает опасную колонку, а не строит кольцо 23×23");
        assertEquals(
                "fence_post",
                StageSchemes.fenceStub(0),
                "До первого этапа действует та же заглушка этапа 1: иначе событие до старта этапа не построить");
    }

    @Test
    void fenceStub_SecondStage_IsSingleWallPost() {
        assertEquals(
                "wall_3_post",
                StageSchemes.fenceStub(2),
                "Со второго этапа ограда — стена: заглушка обязана быть стенным столбом той же схемы, а не "
                        + "деревянным забором из прошлого этапа");
    }
}
