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
        assertEquals(List.of("passive", "defensive", "protect", "aggressive"), SetCombatModeParams.MODE_IDS,
                "set_combat_mode обязан принимать ровно четыре режима задания: guard_area включается зоной охраны, "
                        + "а не строкой режима");
    }

    @Test
    void from_EachAllowedMode_ReturnsMatchingCombatMode() {
        for (String id : SetCombatModeParams.MODE_IDS) {
            SetCombatModeParams params = SetCombatModeParams.from(Map.of("mode", id));

            assertEquals(CombatMode.valueOf(id.toUpperCase(Locale.ROOT)), params.mode(),
                    "Режим '" + id + "' обязан разбираться в соответствующий CombatMode: иначе дух включит не тот режим");
        }
    }
    @Test
    void from_UppercaseMode_IsAccepted() {
        SetCombatModeParams params = SetCombatModeParams.from(Map.of("mode", "PASSIVE"));

        assertEquals(CombatMode.PASSIVE, params.mode(),
                "Регистр режима не должен мешать: стратег может прислать строку в разном регистре");
    }

    @Test
    void from_GuardArea_IsRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> SetCombatModeParams.from(Map.of("mode", "guard_area")),
                "guard_area нельзя включить одним режимом: он требует зоны охраны, поэтому режим отвергается");

        assertEquals(true, error.getMessage().contains("guard_area"),
                "Сообщение обязано назвать guard_area: по нему понятно, почему режим не принят. Текст: "
                        + error.getMessage());
    }

    @Test
    void from_UnknownMode_IsRejected() {
        assertThrows(IllegalArgumentException.class, () -> SetCombatModeParams.from(Map.of("mode", "berserk")),
                "Неизвестный режим обязан отвергаться до старта задачи, а не подменяться молчаливым умолчанием");
    }

    @Test
    void from_MissingMode_IsRejected() {
        assertThrows(IllegalArgumentException.class, () -> SetCombatModeParams.from(Map.of()),
                "Без параметра mode непонятно, какой режим включать, — задача обязана отказывать");
    }

    @Test
    void from_NullParams_IsRejected() {
        assertThrows(NullPointerException.class, () -> SetCombatModeParams.from(null),
                "from(null) обязан отказывать: без карты параметров режим не прочитать");
    }
}
