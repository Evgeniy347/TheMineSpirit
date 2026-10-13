package com.theminespirit.core.intellect;

public record InvitationState(InvitationStage stage, long deadline, int repeats, boolean goWithout) {
    public InvitationState {
        if (stage == null) {
            throw new IllegalArgumentException("Состояние приглашения без стадии не читается: стадия входит в чат");
        }
        if (deadline < 0) {
            throw new IllegalArgumentException(
                    "Состояние со сроком " + deadline + ": время не может быть отрицательным");
        }
        if (repeats < 0) {
            throw new IllegalArgumentException(
                    "Состояние со счётчиком " + repeats + ": повторы не могут быть отрицательными");
        }
    }
}
