package com.theminespirit.core.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.tactics.CombatMode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SetCombatModeParamsTests {
    @Test
    void modeIds_TaskModes_AreExactlyFour() {
        assertEquals(
                List.of("passive", "defensive", "protect", "aggressive"),
                SetCombatModeParams.MODE_IDS,
                "set_combat_mode обязан принимать ровно четыре режима задания: guard_area включается зоной охраны, "
                        + "а не строкой режима"
                        + " Иначе тест modeIds_TaskModes_AreExactlyFour перестанет ловить эту поломку. При смене modeIds_TaskModes_AreExactlyFour проверь это утверждение.");
    }

    @Test
    void from_EachAllowedMode_ReturnsMatchingCombatMode() {
        for (String id : SetCombatModeParams.MODE_IDS) {
            SetCombatModeParams params = SetCombatModeParams.from(Map.of("mode", id));

            assertEquals(
                    CombatMode.valueOf(id.toUpperCase(Locale.ROOT)),
                    params.mode(),
                    "Режим '" + id + "' обязан разбираться в соответствующий CombatMode: иначе дух включит не тот режим"
                            + " При смене from_EachAllowedMode_ReturnsMatchingCombatMode проверь это утверждение.");
        }
    }

    @Test
    void from_UppercaseMode_IsAccepted() {
        SetCombatModeParams params = SetCombatModeParams.from(Map.of("mode", "PASSIVE"));

        assertEquals(
                CombatMode.PASSIVE,
                params.mode(),
                "Регистр режима не должен мешать: стратег может прислать строку в разном регистре"
                        + " Иначе тест from_UppercaseMode_IsAccepted перестанет ловить эту поломку. При смене from_UppercaseMode_IsAccepted проверь это утверждение.");
    }

    @Test
    void from_GuardArea_IsRejected() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SetCombatModeParams.from(Map.of("mode", "guard_area")),
                "guard_area нельзя включить одним режимом: он требует зоны охраны, поэтому режим отвергается"
                        + " Иначе тест from_GuardArea_IsRejected перестанет ловить эту поломку. При смене from_GuardArea_IsRejected проверь это утверждение.");

        assertEquals(
                true,
                error.getMessage().contains("guard_area"),
                "Сообщение обязано назвать guard_area: по нему понятно, почему режим не принят. Текст: "
                        + error.getMessage()
                        + " Иначе тест from_GuardArea_IsRejected перестанет ловить эту поломку. При смене from_GuardArea_IsRejected проверь это утверждение.");
    }

    @Test
    void from_UnknownMode_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SetCombatModeParams.from(Map.of("mode", "berserk")),
                "Неизвестный режим обязан отвергаться до старта задачи, а не подменяться молчаливым умолчанием"
                        + " Иначе тест from_UnknownMode_IsRejected перестанет ловить эту поломку. При смене from_UnknownMode_IsRejected проверь это утверждение.");
    }

    @Test
    void from_MissingMode_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SetCombatModeParams.from(Map.of()),
                "Без параметра mode непонятно, какой режим включать, — задача обязана отказывать"
                        + " Иначе тест from_MissingMode_IsRejected перестанет ловить эту поломку. При смене from_MissingMode_IsRejected проверь это утверждение.");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> SetCombatModeParams.from(null),
                "from(null) обязан отказывать: без карты параметров режим не прочитать"
                        + " Иначе тест from_NullParams_IsRejected перестанет ловить эту поломку. При смене from_NullParams_IsRejected проверь это утверждение.");
    }
}
