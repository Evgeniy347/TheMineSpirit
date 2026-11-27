package com.theminespirit.core.board.audit;

import com.theminespirit.core.board.api.BoardCase;
import java.util.Set;

public final class AuditGraphMapping {
    public static final Set<String> UNMAPPED = Set.of();

    private AuditGraphMapping() {}

    public static BoardCase caseOf(Group.Kind kind) {
        return switch (kind) {
            case BUILDING -> BoardCase.REPAIR_BUILDING;
            case STREET -> BoardCase.BUILD_STREETS;
            case WALL -> BoardCase.BUILD_FENCE;
            case GROUND -> BoardCase.CLEAR_GROUNDS;
            case MINE -> BoardCase.BUILD_MINE;
        };
    }
}
