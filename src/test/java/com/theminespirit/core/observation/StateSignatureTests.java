package com.theminespirit.core.observation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class StateSignatureTests {
    @Test
    void air_BoundaryFullLowCritical_SwitchesAtLimits() {
        assertEquals(
                "full", StateSignature.air(100), "С 100 воздуха запас ещё полный: иначе лог шумит на каждом вдохе");
        assertEquals("low", StateSignature.air(99), "99 воздуха — уже мало: это смена состояния, её видно в логе");
        assertEquals("low", StateSignature.air(50), "50 воздуха — ещё мало, но не критично: порог критичного ниже");
        assertEquals("critical", StateSignature.air(49), "49 воздуха — критично: эту строку нельзя терять за сводкой");
    }

    @Test
    void spirit_WaterChange_ChangesSignature() {
        assertNotEquals(
                StateSignature.spirit(300, false),
                StateSignature.spirit(300, true),
                "Вход в воду — смена состояния: без строки [pos] утопление ищут по пустому логу");
    }

    @Test
    void spirit_SameWaterAndAir_KeepsSignature() {
        assertEquals(
                StateSignature.spirit(300, false),
                StateSignature.spirit(300, false),
                "Обычная ходьба, прыжки и смена скрипта не печатают [pos] каждые 5 секунд: позицию и скрипт "
                        + "берут из /v1/bots и сводки раз в окно");
    }

    @Test
    void spirit_AirThreshold_ChangesSignature() {
        assertNotEquals(
                StateSignature.spirit(100, true),
                StateSignature.spirit(99, true),
                "Запас воздуха ниже порога — смена состояния: [pos] показывает её сразу, не дожидаясь сводки");
    }
}
