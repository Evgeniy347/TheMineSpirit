package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StageAuditUpgradeTests {
    @Test
    void repairBlockedByUpgrade_MineLevelOne_NotBlockedByLevelTwo() {
        assertFalse(
                StageAudit.repairBlockedByUpgrade(BoardCase.BUILD_MINE, true),
                "Ремонт шахты уровня 1 не подавляется активным mine_level_2: иначе 56 блоков не чинятся, "
                        + "а уровень 2 не может копать глубокий конец — замкнутый круг");
        assertTrue(
                StageAudit.repairBlockedByUpgrade(BoardCase.BUILD_HOUSE, true),
                "Для остальных построек активный апгрейд по-прежнему откладывает ремонт уровня 1");
        assertFalse(
                StageAudit.repairBlockedByUpgrade(BoardCase.BUILD_MINE, false),
                "Без апгрейда ремонт шахты не блокируется");
    }
}
