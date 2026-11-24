package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StageSchemesTests {
    @Test
    void wallStub_FirstStage_IsSingleWallPost() {
        assertEquals(
                "wall_3_post",
                StageSchemes.wallStub(1),
                "Заглушка ограды — один столб стены: событие отмечает опасную колонку, а не строит кольцо 23×23"
                        + " Иначе тест wallStub_FirstStage_IsSingleWallPost перестанет ловить эту поломку. При смене wallStub_FirstStage_IsSingleWallPost проверь это утверждение.");
        assertEquals(
                "wall_3_post",
                StageSchemes.wallStub(0),
                "До первого этапа действует та же заглушка стены: иначе событие до старта этапа не построить"
                        + " При смене wallStub_FirstStage_IsSingleWallPost проверь это утверждение.");
    }

    @Test
    void wallStub_ThirdStage_IsSingleWallPost() {
        assertEquals(
                "wall_3_post",
                StageSchemes.wallStub(3),
                "С третьего этапа ограда — стена: заглушка обязана быть стенным столбом той же схемы, а не "
                        + "деревянным забором из прошлого этапа"
                        + " Иначе тест wallStub_ThirdStage_IsSingleWallPost перестанет ловить эту поломку. При смене wallStub_ThirdStage_IsSingleWallPost проверь это утверждение.");
    }
}
