package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StageCatalogOriginTests {

    @Test
    void wheatField_OriginOnGroundTop_NotInAir() {
        assertEquals(
                68,
                StageCatalog.originY("wheat_field", 69),
                "Грядка ставится по верхнему блоку земли: пашню делает тяпка по земле, а не по воздуху. "
                        + "Уровень площадки 69 — это воздух над землёй 68");
    }

    @Test
    void house_OriginOnPlotLevel_KeepsFloor() {
        assertEquals(
                69,
                StageCatalog.originY("family_house_a", 69),
                "У дома нижний слой — пол: он остаётся на уровне площадки, иначе дом уйдёт в землю");
    }
}
