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
    void spirit_ScriptChange_ChangesSignature() {
        assertNotEquals(
                StateSignature.spirit("chop_tree", 300, false, true),
                StateSignature.spirit("acquire", 300, false, true),
                "Смена скрипта — смена состояния: [pos] обязан показать её сразу, а не через окно сводки");
    }

    @Test
    void spirit_WaterOrGroundChange_ChangesSignature() {
        assertNotEquals(
                StateSignature.spirit("goto", 300, false, true),
                StateSignature.spirit("goto", 300, true, true),
                "Вход в воду — смена состояния: без строки [pos] утопление ищут по пустому логу");
        assertNotEquals(
                StateSignature.spirit("goto", 300, false, true),
                StateSignature.spirit("goto", 300, false, false),
                "Отрыв от земли — смена состояния: падение и прыжок обязаны попадать в лог");
    }

    @Test
    void spirit_SameStateInNewCell_KeepsSignature() {
        assertEquals(
                StateSignature.spirit("chop_tree", 300, false, true),
                StateSignature.spirit("chop_tree", 300, false, true),
                "Обычная ходьба без смены скрипта, воды и опоры не должна печатать [pos] каждые 5 секунд");
    }

    @Test
    void spirit_BlankScript_IsNamedDash() {
        assertEquals(
                "-|false|true|full",
                StateSignature.spirit("  ", 300, false, true),
                "Пустой скрипт называется «-»: строка без имени скрипта не должна менять состояние на пустое");
    }
}
