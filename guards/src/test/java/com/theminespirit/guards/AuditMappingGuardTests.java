package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.board.audit.AuditGraphMapping;
import com.theminespirit.core.board.audit.Group;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuditMappingGuardTests {
    @Test
    void auditMapping_EveryGroupKind_HasBoardCase() {
        assertEquals(
                List.of(
                        BoardCase.REPAIR_BUILDING,
                        BoardCase.BUILD_STREETS,
                        BoardCase.BUILD_FENCE,
                        BoardCase.CLEAR_GROUNDS,
                        BoardCase.BUILD_MINE),
                List.of(
                        AuditGraphMapping.caseOf(Group.Kind.BUILDING),
                        AuditGraphMapping.caseOf(Group.Kind.STREET),
                        AuditGraphMapping.caseOf(Group.Kind.WALL),
                        AuditGraphMapping.caseOf(Group.Kind.GROUND),
                        AuditGraphMapping.caseOf(Group.Kind.MINE)),
                "Каждый предмет группы обязан маппиться в BoardCase: без этого граф выдаст узел без действия,"
                        + " а тактика не сможет его выполнить. При смене видов групп перепроверь AuditGraphMapping."
                        + " Иначе тест auditMapping_EveryGroupKind_HasBoardCase перестанет ловить эту поломку.");
    }
}
