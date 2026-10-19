package com.theminespirit.core.fishing;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Какая рыба идёт в ведро: вид сущности → предмет ведра. Список закрыт ванилью:
 * ведром ловят только треску, лосося, иглобрюха и тропическую рыбу, остальное в ведро
 * не садится. Порядок видов задан явно — обход обязан быть детерминирован.
 */
public final class FishBuckets {
    private static final Map<String, String> ENTITY_TO_BUCKET = build();

    private FishBuckets() {}

    public static boolean isBucketable(String entityType) {
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
