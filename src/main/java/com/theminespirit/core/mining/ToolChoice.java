package com.theminespirit.core.mining;

import com.theminespirit.core.board.ToolGate;
import com.theminespirit.core.board.ToolsConfig;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class ToolChoice {
    public static final int HAND = -1;
    public static final int MAIN_HAND = -2;
    public static final double HAND_SPEED = 1.0;
    public static final int UNWEARING = 0;

    private ToolChoice() {}

    public record ToolOption(
            int slot,
            double speed,
            boolean correctForDrops,
            int remainingUses,
            int maxUses,
            boolean enchanted,
            String itemId) {
        public ToolOption {
            Objects.requireNonNull(itemId, "Предмет не может быть null: уровень износа читается по его материалу");
        }

        public static ToolOption of(int slot, double speed, boolean correctForDrops) {
            return new ToolOption(slot, speed, correctForDrops, UNWEARING, UNWEARING, false, "");
        }

        public boolean spent(ToolReserve reserve) {
            return reserve.spent(remainingUses, maxUses, enchanted);
        }

        public boolean spent(ToolReserve reserve, Collection<String> repairTiers) {
            return reserve.spent(remainingUses, maxUses, enchanted, itemId, repairTiers);
        }
    }

    public record Choice(int slot, double speed, boolean dropsAllowed) {}

    public static Choice best(List<ToolOption> options) {
        return best(options, ToolReserve.defaults(), ToolsConfig.defaults().repairTiers());
    }

    public static boolean shouldRefuseForLackOfTool(Choice choice, List<ToolOption> options) {
        Objects.requireNonNull(choice, "Выбор не может быть null: по нему решается, нужен ли отказ");
        Objects.requireNonNull(options, "Набор инструментов не может быть null: по нему видно, есть ли правильный");
        return !choice.dropsAllowed() && options.stream().anyMatch(ToolOption::correctForDrops);
    }

    public static boolean refusesHandDig(
            Choice choice, List<ToolOption> options, boolean pickaxeBlock, boolean escaping) {
        Objects.requireNonNull(choice, "Выбор не может быть null: по нему решается, копает ли дух руками");
        Objects.requireNonNull(options, "Набор инструментов не может быть null: по нему видно, есть ли кирка");
        return pickaxeBlock
                && !escaping
                && !choice.dropsAllowed()
                && options.stream().noneMatch(option -> option.itemId().endsWith(ToolGate.PICKAXE));
    }

    public static Choice best(List<ToolOption> options, ToolReserve reserve, Collection<String> repairTiers) {
        Objects.requireNonNull(
                options, "options не может быть null: пустой набор инструментов задаётся пустым списком");
        Objects.requireNonNull(reserve, "reserve не может быть null: по нему решается, износился ли инструмент");
        Objects.requireNonNull(
                repairTiers, "repairTiers не может быть null: без уровней ремонта неизвестно, что беречь");
        ToolOption best = null;
        boolean anyCorrect = false;
        for (ToolOption option : options) {
            if (!option.correctForDrops()) {
                continue;
            }
            anyCorrect = true;
            if (option.spent(reserve, repairTiers)) {
                continue;
            }
            if (best == null
                    || option.speed() > best.speed()
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
