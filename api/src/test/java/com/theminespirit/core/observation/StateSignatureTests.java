package com.theminespirit.core.observation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class StateSignatureTests {
    @Test
    void air_BoundaryFullLowCritical_SwitchesAtLimits() {
        assertEquals(
                "full",
                StateSignature.air(100),
                "С 100 воздуха запас ещё полный: иначе лог шумит на каждом вдохе"
                        + " При смене air_BoundaryFullLowCritical_SwitchesAtLimits проверь это утверждение.");
        assertEquals(
                "low",
                StateSignature.air(99),
                "99 воздуха — уже мало: это смена состояния, её видно в логе"
                        + " Иначе тест air_BoundaryFullLowCritical_SwitchesAtLimits перестанет ловить эту поломку. При смене air_BoundaryFullLowCritical_SwitchesAtLimits проверь это утверждение.");
        assertEquals(
                "low",
                StateSignature.air(50),
                "50 воздуха — ещё мало, но не критично: порог критичного ниже"
                        + " Иначе тест air_BoundaryFullLowCritical_SwitchesAtLimits перестанет ловить эту поломку. При смене air_BoundaryFullLowCritical_SwitchesAtLimits проверь это утверждение.");
        assertEquals(
                "critical",
                StateSignature.air(49),
                "49 воздуха — критично: эту строку нельзя терять за сводкой"
                        + " Иначе тест air_BoundaryFullLowCritical_SwitchesAtLimits перестанет ловить эту поломку. При смене air_BoundaryFullLowCritical_SwitchesAtLimits проверь это утверждение.");
    }

    @Test
    void spirit_WaterChange_ChangesSignature() {
        assertNotEquals(
                StateSignature.spirit(300, false),
                StateSignature.spirit(300, true),
                "Вход в воду — смена состояния: без строки [pos] утопление ищут по пустому логу"
                        + " Иначе тест spirit_WaterChange_ChangesSignature перестанет ловить эту поломку. При смене spirit_WaterChange_ChangesSignature проверь это утверждение.");
    }

    @Test
    void spirit_SameWaterAndAir_KeepsSignature() {
        assertEquals(
                StateSignature.spirit(300, false),
                StateSignature.spirit(300, false),
                "Обычная ходьба, прыжки и смена скрипта не печатают [pos] каждые 5 секунд: позицию и скрипт "
                        + "берут из /v1/bots и сводки раз в окно"
                        + " Иначе тест spirit_SameWaterAndAir_KeepsSignature перестанет ловить эту поломку. При смене spirit_SameWaterAndAir_KeepsSignature проверь это утверждение.");
    }

    @Test
    void spirit_AirThreshold_ChangesSignature() {
        assertNotEquals(
                StateSignature.spirit(100, true),
                StateSignature.spirit(99, true),
                "Запас воздуха ниже порога — смена состояния: [pos] показывает её сразу, не дожидаясь сводки"
                        + " Иначе тест spirit_AirThreshold_ChangesSignature перестанет ловить эту поломку. При смене spirit_AirThreshold_ChangesSignature проверь это утверждение.");
    }
}
