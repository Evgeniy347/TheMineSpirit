package com.theminespirit.core.intellect;

public final class Invitation {
    private boolean opened;
    private long openedAt;
    private long deadline;
    private InvitationStage stage;
    private boolean goWithout;
    private int extendsUsed;

    public InvitationState open(long now) {
        if (now < 0) {
            throw new IllegalArgumentException("Приглашение в тике " + now + ": время не может быть отрицательным");
        }
        if (opened && (stage == InvitationStage.OPEN || stage == InvitationStage.EXTENDED)) {
            return state(now);
        }
        opened = true;
        openedAt = now;
        deadline = now + IntellectTuning.INVITE_TTL_TICKS;
        stage = InvitationStage.OPEN;
        goWithout = false;
        extendsUsed = 0;
        return state(now);
    }

    public InvitationState tick(long now) {
        requireOpened();
        if (now < openedAt) {
            throw new IllegalArgumentException("Тик приглашения " + now + " раньше открытия " + openedAt
                    + ": время идёт вперёд");
        }
        if ((stage == InvitationStage.OPEN || stage == InvitationStage.EXTENDED) && now >= deadline) {
            stage = InvitationStage.GO_WITHOUT;
            goWithout = true;
        }
        return state(now);
    }

    public InvitationState answer(InvitationAnswer answer, long now) {
        requireOpened();
        if (answer == null) {
            throw new IllegalArgumentException("Ответ не может быть null: кнопки присылают WAIT_MORE или GO_WITHOUT");
        }
        if (now < openedAt) {
            throw new IllegalArgumentException("Ответ в тике " + now + " раньше открытия " + openedAt
                    + ": время идёт вперёд");
        }
        if (stage == InvitationStage.GO_WITHOUT || stage == InvitationStage.ACCEPTED) {
            return state(now);
        }
        if (answer == InvitationAnswer.WAIT_MORE) {
            if (extendsUsed < IntellectTuning.INVITE_MAX_EXTENDS) {
                stage = InvitationStage.EXTENDED;
                deadline += IntellectTuning.INVITE_EXTEND_TICKS;
                extendsUsed++;
            }
        } else {
            stage = InvitationStage.GO_WITHOUT;
            goWithout = true;
        }
        return state(now);
    }

    private InvitationState state(long now) {
        int repeats = opened ? (int) ((now - openedAt) / IntellectTuning.INVITE_REPEAT_TICKS) : 0;
        return new InvitationState(stage == null ? InvitationStage.GO_WITHOUT : stage, deadline,
                Math.max(0, repeats), goWithout);
    }

    private void requireOpened() {
        if (!opened) {
            throw new IllegalStateException(
                    "Приглашение не открыто: сначала open, иначе сроку и повторам не от чего считаться");
        }
    }
}
