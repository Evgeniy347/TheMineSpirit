package com.theminespirit.core.board.audit;

import java.util.Objects;

public final class AuditFactCheck {
    public enum Decision {
        EXECUTE,
        ALREADY_DONE,
        FOREIGN_BLOCK
    }

    private AuditFactCheck() {}

    public static Decision of(DiffItem item, String actual) {
        Objects.requireNonNull(item, "Предмет группы не может быть null: проверять нечего");
        Objects.requireNonNull(actual, "Факт мира не может быть null: пустота передаётся как air");
        if (item.satisfiedBy(actual)) {
            return Decision.ALREADY_DONE;
        }
        return actual.equals(item.found()) ? Decision.EXECUTE : Decision.FOREIGN_BLOCK;
    }
}
