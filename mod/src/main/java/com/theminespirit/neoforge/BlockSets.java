package com.theminespirit.neoforge;

import com.theminespirit.core.observation.IdFilter;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;

public final class BlockSets {
    private BlockSets() {}

    public static Set<Block> resolve(IdFilter filter) {
        Set<Block> blocks = new HashSet<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (filter.matches(BuiltInRegistries.BLOCK.getKey(block).toString())) {
                blocks.add(block);
            }
        }
        return Set.copyOf(blocks);
    }
}
