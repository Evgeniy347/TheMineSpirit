package com.theminespirit.neoforge;

import java.util.Objects;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

public final class RequiredTier {
    private RequiredTier() {
    }

    public static String of(BlockState state) {
        Objects.requireNonNull(state, "Состояние блока не может быть null: по его тегам определяется инструмент");
        if (state.is(BlockTags.NEEDS_DIAMOND_TOOL)) {
            return "алмазная кирка";
        }
        if (state.is(BlockTags.NEEDS_IRON_TOOL)) {
            return "железная кирка";
        }
        if (state.is(BlockTags.NEEDS_STONE_TOOL)) {
            return "каменная кирка";
        }
        return "";
    }

    public static String kindOf(BlockState state) {
        Objects.requireNonNull(state, "Состояние блока не может быть null: по его тегам определяется вид инструмента");
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            return "кирка";
        }
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
            return "топор";
        }
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            return "лопата";
        }
        if (state.is(BlockTags.MINEABLE_WITH_HOE)) {
            return "мотыга";
        }
        return "";
    }
}