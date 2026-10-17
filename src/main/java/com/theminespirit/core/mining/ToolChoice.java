package com.theminespirit.core.mining;

import java.util.List;
import java.util.Objects;

public final class ToolChoice {
    public static final int HAND = -1;
    public static final int MAIN_HAND = -2;
    public static final double HAND_SPEED = 1.0;
    public static final int UNWEARING = 0;

    private ToolChoice() {
    }

    public record ToolOption(int slot, double speed, boolean correctForDrops, int remainingUses, int maxUses,
            boolean enchanted) {
        public static ToolOption of(int slot, double speed, boolean correctForDrops) {
            return new ToolOption(slot, speed, correctForDrops, UNWEARING, UNWEARING, false);
        }

        public boolean spent(ToolReserve reserve) {
            return reserve.spent(remainingUses, maxUses, enchanted);
        }
    }

    public record Choice(int slot, double speed, boolean dropsAllowed) {
    }

    public static Choice best(List<ToolOption> options) {
        return best(options, ToolReserve.defaults());
    }

    public static Choice best(List<ToolOption> options, ToolReserve reserve) {
        Objects.requireNonNull(options, "options не может быть null: пустой набор инструментов задаётся пустым списком");
        Objects.requireNonNull(reserve, "reserve не может быть null: по нему решается, износился ли инструмент");
        ToolOption best = null;
        boolean anyCorrect = false;
        for (ToolOption option : options) {
            if (!option.correctForDrops()) {
                continue;
            }
            anyCorrect = true;
            if (option.spent(reserve)) {
                continue;
            }
            if (best == null || option.speed() > best.speed()
                    || (option.speed() == best.speed() && option.slot() < best.slot())) {
                best = option;
            }
        }
        if (!anyCorrect || best == null) {
            return new Choice(HAND, HAND_SPEED, false);
        }
        if (best.speed() <= HAND_SPEED) {
            return new Choice(HAND, HAND_SPEED, true);
        }
        return new Choice(best.slot(), best.speed(), true);
    }
}
