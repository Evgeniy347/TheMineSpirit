package com.theminespirit.core.ui;

import java.util.Objects;

public final class SettlementTreeStyle {
    public static final int NODE = 28;
    public static final int STEP_X = 96;
    public static final int STEP_Y = 46;
    public static final int PAD = 8;
    public static final int GATE_STUB = STEP_X + NODE + 22;

    public static final int TEXT = 0xFFFFFFFF;
    public static final int MUTED = 0xFFA0A0A0;
    public static final int READY_BORDER = 0xFFFFD34D;
    public static final int READY_FILL = 0xFF5C4A12;
    public static final int PROGRESS_BORDER = 0xFF6FA548;
    public static final int PROGRESS_FILL = 0xFF2E4020;
    public static final int STARTED_BORDER = 0xFF6B665F;
    public static final int STARTED_FILL = 0xFF3A3833;
    public static final int LOCKED_BORDER = 0xFF3F3D39;
    public static final int LOCKED_FILL = 0xFF262522;
    public static final int UNKNOWN_BORDER = 0xFF4A4A4A;
    public static final int UNKNOWN_FILL = 0xFF2B2B2B;
    public static final int LINK = 0xFF8A857C;
    public static final int LINK_READY = 0xFFFFD34D;
    public static final int LINK_LOCKED = 0xFF4A4742;

    private SettlementTreeStyle() {}

    public static int border(SettlementTree.State state) {
        Objects.requireNonNull(state, "Состояние узла не может быть null: от него зависит цвет рамки");
        return switch (state) {
            case READY -> READY_BORDER;
            case IN_PROGRESS -> PROGRESS_BORDER;
            case NOT_STARTED -> STARTED_BORDER;
            case LOCKED -> LOCKED_BORDER;
            case UNKNOWN -> UNKNOWN_BORDER;
        };
    }

    public static int fill(SettlementTree.State state) {
        Objects.requireNonNull(state, "Состояние узла не может быть null: от него зависит заливка рамки");
        return switch (state) {
            case READY -> READY_FILL;
            case IN_PROGRESS -> PROGRESS_FILL;
            case NOT_STARTED -> STARTED_FILL;
            case LOCKED -> LOCKED_FILL;
            case UNKNOWN -> UNKNOWN_FILL;
        };
    }

    public static int link(SettlementTree.State from, SettlementTree.State to) {
        if (from == SettlementTree.State.READY && to == SettlementTree.State.READY) {
            return LINK_READY;
        }
        if (from == SettlementTree.State.LOCKED || to == SettlementTree.State.LOCKED) {
            return LINK_LOCKED;
        }
        return LINK;
    }
}
