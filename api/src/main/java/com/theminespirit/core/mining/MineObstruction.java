package com.theminespirit.core.mining;

import com.theminespirit.core.observation.BlockCoord;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class MineObstruction {
    private static final Set<String> COMMON = Set.of(
            "stone",
            "cobblestone",
            "deepslate",
            "cobbled_deepslate",
            "dirt",
            "coarse_dirt",
            "rooted_dirt",
            "grass_block",
            "podzol",
            "mycelium",
            "gravel",
            "sand",
            "red_sand",
            "tuff",
            "calcite",
            "andesite",
            "diorite",
            "granite",
            "sandstone",
            "mud",
            "clay");

    private static final Set<String> ORES = Set.of("coal_ore", "deepslate_coal_ore", "iron_ore", "deepslate_iron_ore");

    private MineObstruction() {}

    public static boolean clearable(String blockId) {
        Objects.requireNonNull(blockId, "Id блока не может быть null: по нему решается, мешает ли он спуску");
        String path = path(blockId);
        return COMMON.contains(path) || ORES.contains(path);
    }

    public static boolean underWork(@Nullable BlockCoord working, BlockCoord blocker) {
        return working != null && working.equals(blocker);
    }

    private static String path(String blockId) {
        int colon = blockId.indexOf(':');
        return colon < 0 ? blockId : blockId.substring(colon + 1);
    }
}
