package com.theminespirit.neoforge;

import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicParser;
import com.theminespirit.core.inventory.JsonResource;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class SchematicResources {
    private static final Map<String, Schematic> CACHE = new ConcurrentHashMap<>();

    private SchematicResources() {
    }

    public static Schematic load(String file) {
        Objects.requireNonNull(file, "Имя схемы не может быть null: без него неизвестно, что грузить");
        if (file.isBlank()) {
            throw new IllegalArgumentException("Имя схемы пусто: по пустому имени схему не найти");
        }
        Schematic cached = CACHE.get(file);
        if (cached != null) {
            return cached;
        }
        String path = "data/theminespirit/schematics/" + file + ".json";
        Schematic parsed = SchematicParser.parse(JsonResource.readClasspath(path), path);
        CACHE.put(file, parsed);
        return parsed;
    }

    static void clearCache() {
        CACHE.clear();
    }
}
