package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.tactics.TacticsAction;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TacticsOccupationTests {
    @Test
    void caseOf_NoEnemyReturnOrGuard_NotCombat() {
        assertEquals(
                Optional.of(BoardCase.ACTIVITY_RETURN),
                BoardActivities.caseOf(TacticsAction.RETURN),
                "Возврат к якорю без врага — не «бой» (владелец: в мирном режиме доска писала «Бой»); подпись "
                        + "занятия берётся из фактического действия тактики");
        assertEquals(
                Optional.of(BoardCase.ACTIVITY_GUARD),
                BoardActivities.caseOf(TacticsAction.GUARD),
                "Возврат в зону охраны без врага — «охрана зоны», не «бой»");
        assertEquals(
                Optional.of(BoardCase.ACTIVITY_RETREAT),
                BoardActivities.caseOf(TacticsAction.RETREAT),
                "Отход — своя подпись, не «бой»");
    }

    @Test
    void caseOf_FightAndIdle_CombatOnlyForFight() {
        assertEquals(
                Optional.of(BoardCase.ACTIVITY_COMBAT),
                BoardActivities.caseOf(TacticsAction.FIGHT),
                "«Бой» — только когда тактика бьёт цель-врага");
        assertEquals(
                Optional.empty(),
                BoardActivities.caseOf(TacticsAction.IDLE),
                "Тактика ничего не делает — занятие берётся не из тактики");
    }
}
