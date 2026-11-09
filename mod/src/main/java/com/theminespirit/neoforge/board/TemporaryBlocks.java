package com.theminespirit.neoforge.board;

import java.util.Set;

final class TemporaryBlocks {
    private static final Set<String> LEGACY_MOVEMENT = Set.of(
            "minecraft:dirt",
            "minecraft:cobblestone",
            "minecraft:granite",
            "minecraft:diorite",
            "minecraft:andesite",
            "minecraft:gravel");

    private TemporaryBlocks() {}

    static boolean legacyMovement(String blockId) {
        return LEGACY_MOVEMENT.contains(blockId);
    }
}
