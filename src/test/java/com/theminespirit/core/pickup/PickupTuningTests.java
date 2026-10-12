package com.theminespirit.core.pickup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PickupTuningTests {
    @Test
    void defaultTuning_Constants_MatchChecklist() {
        assertEquals(new PickupTuning(10, 1.5, 2400, 40), PickupTuning.DEFAULT,
                "Настройки подбора по умолчанию: опрос раз в 10 тиков, дальность 1.5, потолок 2400 тиков, ожидание задержки 40 тиков; "
                        + "изменение этих чисел меняет нагрузку на сервер и сверяется с чеклистом 10");
    }

    @Test
    void overflowDelays_Constants_MatchChecklist() {
        assertEquals(400, PickupTuning.SELF_DROP_DELAY_TICKS,
                "Выброшенное при переполнении лежит 400 тиков, прежде чем дух сможет его подобрать: иначе дух "
                        + "тут же подбирал бы выброшенное обратно");
        assertEquals(6000, PickupTuning.OWN_DROP_SKIP_TICKS,
                "Свои выброшенные кучки сборщик пропускает 6000 тиков: иначе разгрузка превращалась бы "
                        + "в цикл выбросил-подобрал");
    }

    @Test
    void constructor_NonPositiveValues_AreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PickupTuning(0, 1.5, 2400, 40),
                "Период опроса 0 обязан отвергаться: опрос каждый тик нагружает сервер");
        assertThrows(IllegalArgumentException.class, () -> new PickupTuning(10, 0.0, 2400, 40),
                "Нулевая дальность подбора обязана отвергаться: дух не окажется достаточно близко");
        assertThrows(IllegalArgumentException.class, () -> new PickupTuning(10, 1.5, 0, 40),
                "Нулевой тайм-аут обязан отвергаться: задача провалилась бы до начала");
        assertThrows(IllegalArgumentException.class, () -> new PickupTuning(10, 1.5, 2400, -1),
                "Отрицательное ожидание задержки обязано отвергаться: кучку пропускали бы до того, как она дозреет");
    }
}
