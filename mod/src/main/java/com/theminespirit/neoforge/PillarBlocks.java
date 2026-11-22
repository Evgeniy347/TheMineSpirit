package com.theminespirit.neoforge;

import java.util.function.ToIntFunction;

final class PillarBlocks {
    private static final String[] ORDER = {
        "minecraft:dirt",
        "minecraft:cobblestone",
        "minecraft:stone",
        "minecraft:cobbled_deepslate",
        "minecraft:deepslate",
        "minecraft:gravel"
    };

    private PillarBlocks() {}

    static String cheapBlock(SpiritEntity spirit) {
        return cheapBlock(id -> BlockItems.countIn(spirit.getInventory(), id));
    }

    static String cheapBlock(ToIntFunction<String> count) {
        for (String id : ORDER) {
            if (count.applyAsInt(id) > 0) {
                return id;
            }
        }
        return null;
    }
}
