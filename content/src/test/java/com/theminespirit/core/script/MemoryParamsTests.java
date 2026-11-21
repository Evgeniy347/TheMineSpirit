package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.perception.MemoryCall;
import com.theminespirit.core.perception.MemoryType;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MemoryParamsTests {
    @Test
    void remember_NameCoordinatesAndType_BuildRememberCall() {
        MemoryCall call = MemoryParams.remember(Map.of("name", "iron", "x", 1, "y", -30, "z", 7, "type", "resource"));

        assertEquals(
                new MemoryCall.Remember("iron", MemoryType.RESOURCE, new BlockCoord(1, -30, 7)),
                call,
                "remember обязан запомнить именно ту точку и тот тип, что заданы в плане"
                        + " Иначе тест remember_NameCoordinatesAndType_BuildRememberCall перестанет ловить эту поломку. При смене remember_NameCoordinatesAndType_BuildRememberCall проверь это утверждение.");
    }

    @Test
    void recall_Name_BuildRecallCall() {
        assertEquals(
                new MemoryCall.Recall("home"),
                MemoryParams.recall(Map.of("name", "home")),
                "recall обязан вспоминать точку по имени из плана"
                        + " Иначе тест recall_Name_BuildRecallCall перестанет ловить эту поломку. При смене recall_Name_BuildRecallCall проверь это утверждение.");
    }

    @Test
    void recallAll_Type_BuildRecallAllCall() {
        assertEquals(
                new MemoryCall.RecallAll(MemoryType.DANGER),
                MemoryParams.recallAll(Map.of("type", "danger")),
                "recall_all обязан выбирать точки по типу из плана"
                        + " Иначе тест recallAll_Type_BuildRecallAllCall перестанет ловить эту поломку. При смене recallAll_Type_BuildRecallAllCall проверь это утверждение.");
    }

    @Test
    void forget_Name_BuildForgetCall() {
        assertEquals(
                new MemoryCall.Forget("home"),
                MemoryParams.forget(Map.of("name", "home")),
                "forget обязан забывать точку по имени из плана"
                        + " Иначе тест forget_Name_BuildForgetCall перестанет ловить эту поломку. При смене forget_Name_BuildForgetCall проверь это утверждение.");
    }

    @Test
    void recallAll_UppercaseType_IsRejectedWithAllowedValues() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> MemoryParams.recallAll(Map.of("type", "DANGER")),
                "тип точки принимается только строчными, как остальные перечисления каталога"
                        + " Иначе тест recallAll_UppercaseType_IsRejectedWithAllowedValues перестанет ловить эту поломку. При смене recallAll_UppercaseType_IsRejectedWithAllowedValues проверь это утверждение.");

        assertTrue(
                error.getMessage().contains("danger"),
                "сообщение обязано перечислить допустимые типы строчными, сейчас: " + error.getMessage()
                        + " Иначе тест recallAll_UppercaseType_IsRejectedWithAllowedValues перестанет ловить эту поломку. При смене recallAll_UppercaseType_IsRejectedWithAllowedValues проверь это утверждение.");
    }

    @Test
    void remember_HeightOutsideWorld_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MemoryParams.remember(Map.of("name", "sky", "x", 0, "y", 500, "z", 0, "type", "base")),
                "точка выше мира обязана отклоняться до запуска скрипта"
                        + " Иначе тест remember_HeightOutsideWorld_IsRejected перестанет ловить эту поломку. При смене remember_HeightOutsideWorld_IsRejected проверь это утверждение.");
    }

    @Test
    void remember_MissingType_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MemoryParams.remember(Map.of("name", "iron", "x", 0, "y", 0, "z", 0)),
                "точка без типа обязана отклоняться: тип нужен, чтобы recall_all отличал руду от опасности"
                        + " Иначе тест remember_MissingType_IsRejected перестанет ловить эту поломку. При смене remember_MissingType_IsRejected проверь это утверждение.");
    }
}
