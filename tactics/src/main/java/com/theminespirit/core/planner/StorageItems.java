package com.theminespirit.core.planner;

import java.util.Set;

public final class StorageItems {
    private static final String NAMESPACE = "minecraft:";
    private static final Set<String> CONTAINERS = Set.of("chest", "trapped_chest", "ender_chest", "barrel");

    private StorageItems() {}

    public static boolean container(String item) {
        String id = item.startsWith(NAMESPACE) ? item.substring(NAMESPACE.length()) : item;
        return CONTAINERS.contains(id) || "shulker_box".equals(id) || id.endsWith("_shulker_box");
    }
}
