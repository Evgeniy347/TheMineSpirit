package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CombatTuningTests {
    private static final ModeParams DEFENSIVE = TacticsConfig.loadDefault().modes().of(CombatMode.DEFENSIVE);

    @Test
    void combatTuning_DangerRadius_MatchAgreedValue() {
        assertEquals(8.0, CombatTuning.DANGER_RADIUS,
                "Радиус опасности равен 8: не дальше зоны, до которой враги преследуют оборонительного духа "
                        + "(modes.json: defensive.pursuitRadius), иначе flee ждал бы спасения там, "
                        + "где его ещё достают");
    }

    @Test
    void combatTuning_DangerRadius_MatchesDefensivePursuitRadius() {
        assertEquals(DEFENSIVE.pursuit(0), CombatTuning.DANGER_RADIUS,
                "Радиус опасности обязан совпадать с преследованием оборонительного режима из modes.json: "
                        + "это один и тот же рубеж «дальше — не догоняют», и расхождение сделало бы бегство "
                        + "ложным (враги в зоне, а дух считает себя спасшимся)");
    }

    @Test
    void combatTuning_DangerRadius_IsPositiveAndFinite() {
        assertTrue(CombatTuning.DANGER_RADIUS > 0 && Double.isFinite(CombatTuning.DANGER_RADIUS),
                "Радиус опасности обязан быть положительным и конечным: ноль объявил бы духа спасшимся "
                        + "сразу, бесконечность — никогда. Фактическое: " + CombatTuning.DANGER_RADIUS);
    }

    @Test
    void combatTuning_DangerRadius_IsNotZero() {
        assertNotEquals(0.0, CombatTuning.DANGER_RADIUS,
                "Нулевой радиус опасности запрещено: flee завершился бы на месте, не отойдя ни на шаг");
    }
}
