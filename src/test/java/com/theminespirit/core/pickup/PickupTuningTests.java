package com.theminespirit.core.pickup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PickupTuningTests {
    @Test
    void defaultTuning_Constants_MatchChecklist() {
        assertEquals(new PickupTuning(10, 1.5, 2400), PickupTuning.DEFAULT,
                "Настройки подбора по умолчанию: опрос раз в 10 тиков, дальность 1.5, потолок 2400 тиков; "
                        + "изменение этих чисел меняет нагрузку на сервер и сверяется с чеклистом 10");
    }

    @Test
    void constructor_NonPositiveValues_AreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PickupTuning(0, 1.5, 2400),
                "Период опроса 0 обязан отвергаться: опрос каждый тик нагружает сервер");
        assertThrows(IllegalArgumentException.class, () -> new PickupTuning(10, 0.0, 2400),
                "Нулевая дальность подбора обязана отвергаться: дух не окажется достаточно близко");
        assertThrows(IllegalArgumentException.class, () -> new PickupTuning(10, 1.5, 0),
                "Нулевой тайм-аут обязан отвергаться: задача провалилась бы до начала");
    }
}
