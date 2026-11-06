package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class StreetWorkLevelTests {
    @Test
    void level_StageLevelFromRules_IsReturned() {
        assertEquals(
                3,
                StreetWork.level(3),
                "Уровень плиток улиц берётся из stages.json и доходит до механики поселения числом: "
                        + "поселению незачем знать правила доски");
    }

    @Test
    void level_StageWithoutStreets_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> StreetWork.level(0),
                "Нулевой уровень BUILD_STREETS: улицу не из чего класть, молчаливая плитка скрыла бы ошибку данных");
    }
}
