package com.theminespirit.neoforge;

import com.theminespirit.core.forestry.TreeSpecies;
import com.theminespirit.core.observation.IdFilter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

final class TreeBlocks {
    private TreeBlocks() {
    }

    static Set<Block> logs(List<TreeSpecies> species) {
        Set<Block> blocks = new LinkedHashSet<>();
        for (TreeSpecies entry : species) {
            blocks.add(BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(entry.logId()))
                    .orElseThrow(() -> new IllegalStateException("Блок брёвен '" + entry.logId() + "' породы '"
                            + entry.id() + "' отсутствует в реестре: сторож treeSpecies_AllIds_ExistInRegistries "
                            + "обязан был поймать опечатку до запуска рубки")));
        }
        return blocks;
    }

    static IdFilter filter(List<TreeSpecies> species) {
        return IdFilter.of(species.stream().map(TreeSpecies::logId).toList());
    }
}
