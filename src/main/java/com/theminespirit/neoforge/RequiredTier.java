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
}