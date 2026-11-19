package com.theminespirit.core.planner;

import com.theminespirit.core.base.NamespacedId;
import java.util.Set;

public final class StorageItems {
    private static final Set<String> CONTAINERS = Set.of("chest", "trapped_chest", "ender_chest", "barrel");

    private StorageItems() {}

    public static boolean container(String item) {
        String id = item.startsWith(NamespacedId.MINECRAFT) ? item.substring(NamespacedId.MINECRAFT.length()) : item;
        return CONTAINERS.contains(id) || "shulker_box".equals(id) || id.endsWith("_shulker_box");
    }
}
