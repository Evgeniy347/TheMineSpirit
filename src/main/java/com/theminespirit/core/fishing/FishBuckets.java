package com.theminespirit.core.fishing;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class FishBuckets {
    private static final Map<String, String> ENTITY_TO_BUCKET = build();

    private FishBuckets() {}

    public static boolean isBucketable(@Nullable String entityType) {
        return entityType != null && ENTITY_TO_BUCKET.containsKey(entityType);
    }

    public static Optional<String> bucketItem(String entityType) {
        return Optional.ofNullable(ENTITY_TO_BUCKET.get(entityType));
    }

    public static Set<String> entityTypes() {
        return ENTITY_TO_BUCKET.keySet();
    }

    private static Map<String, String> build() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("minecraft:cod", "minecraft:cod_bucket");
        map.put("minecraft:salmon", "minecraft:salmon_bucket");
        map.put("minecraft:pufferfish", "minecraft:pufferfish_bucket");
        map.put("minecraft:tropical_fish", "minecraft:tropical_fish_bucket");
        return Map.copyOf(map);
    }
}
