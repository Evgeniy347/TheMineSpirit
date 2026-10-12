package com.theminespirit.core.mining;

import java.util.List;
import java.util.Objects;

public final class ToolChoice {
    public static final int HAND = -1;
    public static final int MAIN_HAND = -2;
    public static final double HAND_SPEED = 1.0;

    private ToolChoice() {
    }

    public record ToolOption(int slot, double speed, boolean correctForDrops) {
    }

    public record Choice(int slot, double speed, boolean dropsAllowed) {
    }

    public static Choice best(List<ToolOption> options) {
        Objects.requireNonNull(options, "options не может быть null: пустой набор инструментов задаётся пустым списком");
        ToolOption best = null;
        boolean anyCorrect = false;
        for (ToolOption option : options) {
            if (!option.correctForDrops()) {
                continue;
            }
            anyCorrect = true;
            if (best == null || option.speed() > best.speed()
                    || (option.speed() == best.speed() && option.slot() < best.slot())) {
                best = option;
            }
        }
        if (!anyCorrect) {
            return new Choice(HAND, HAND_SPEED, false);
        }
        if (best.speed() <= HAND_SPEED) {
            return new Choice(HAND, HAND_SPEED, true);
        }
        return new Choice(best.slot(), best.speed(), true);
    }
}
