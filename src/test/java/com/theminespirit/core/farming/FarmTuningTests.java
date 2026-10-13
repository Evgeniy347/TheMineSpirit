package com.theminespirit.core.farming;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FarmTuningTests {
    @Test
    void reach_ValueCheck_EqualsFour() {
        assertEquals(4.0, FarmTuning.REACH,
                "Досягаемость фермы — 4.0 от глаз духа до центра цели: одно число для планировщика стоянок "
                        + "и проверки исполнителя, заниженное сломает приём R4, завышенное уведёт стоянки "
                        + "за досягаемость ломки BlockBreaker.REACH");
    }
}
