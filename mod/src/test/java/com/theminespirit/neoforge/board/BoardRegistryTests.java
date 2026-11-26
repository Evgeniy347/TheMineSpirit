package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BoardRegistryTests {
    @Test
    void stageBootstrap_Removed_StartupAuditClassIsAbsent() {
        assertThrows(
                ClassNotFoundException.class,
                () -> Class.forName("com.theminespirit.neoforge.board.StageBootstrap"),
                "Стартового класса полного аудита быть не должно: иначе он снова попадёт в BoardRegistry.get "
                        + "и задержит первый тик на 3–4 секунды. При смене BoardRegistryTests проверь "
                        + "BoardRegistry.get, GraphSource.finish и StartupAuditGuardTests.");
    }
}
