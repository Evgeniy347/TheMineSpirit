package com.theminespirit.neoforge;

import com.theminespirit.core.forestry.Tree;
import com.theminespirit.core.forestry.TreeSpecies;
import com.theminespirit.core.observation.BlockCoord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;

final class ChopSpecies {
    private ChopSpecies() {}

    static TreeSpecies of(Level level, Tree found) {
        BlockCoord base = found.base();
        BlockPos pos = new BlockPos(base.x(), base.y(), base.z());
        String blockId = BuiltInRegistries.BLOCK
                .getKey(level.getBlockState(pos).getBlock())
                .toString();
        return TreeSpecies.ofLog(blockId)
                .orElseThrow(() -> new IllegalStateException("Основание дерева " + base + " — блок '" + blockId
                        + "', а не бревно известной породы: поиск брал только брёвна из TreeSpecies"));
    }
}
