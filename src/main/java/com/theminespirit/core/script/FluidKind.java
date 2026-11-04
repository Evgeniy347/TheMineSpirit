package com.theminespirit.core.script;

import java.util.List;
import java.util.Locale;

public enum FluidKind {
    WATER("water", "water_bucket", "minecraft:water"),
    LAVA("lava", "lava_bucket", "minecraft:lava");

    private final String id;
    private final String bucket;
    private final String block;

    FluidKind(String id, String bucket, String block) {
        this.id = id;
        this.bucket = bucket;
        this.block = block;
    }

    public String id() {
        return id;
    }

    public String bucketItem() {
        return bucket;
    }

    public String blockId() {
        return block;
    }

    public static List<String> ids() {
        return List.of(WATER.id, LAVA.id);
    }

    public static FluidKind parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Жидкость для ведра не задана: ожидается water или lava");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (FluidKind kind : values()) {
            if (kind.id.equals(normalized)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Неизвестная жидкость '" + value + "': ожидается water или lava");
    }
}
