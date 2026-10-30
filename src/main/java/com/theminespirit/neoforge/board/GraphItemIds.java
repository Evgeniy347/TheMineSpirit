package com.theminespirit.neoforge.board;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

final class GraphItemIds {
    private GraphItemIds() {}

    static boolean known(String item) {
        String name = item.startsWith("minecraft:") ? item.substring("minecraft:".length()) : item;
        return BuiltInRegistries.ITEM.containsKey(ResourceLocation.withDefaultNamespace(name));
    }
}
