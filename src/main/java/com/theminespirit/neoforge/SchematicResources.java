package com.theminespirit.neoforge;

import com.theminespirit.core.building.Preset;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicTextParser;
import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.reload.TableHolder;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class SchematicResources {
    private static final Map<String, Schematic> CACHE = new ConcurrentHashMap<>();

    private SchematicResources() {}

    public static Schematic load(String file) {
        Objects.requireNonNull(file, "Имя схемы не может быть null: без него неизвестно, что грузить");
        if (file.isBlank()) {
            throw new IllegalArgumentException("Имя схемы пусто: по пустому имени схему не найти");
        }
        Optional<TableHolder<Schematic>> live = DataTables.INSTANCE.schematic(file);
        if (live.isPresent()) {
            return live.get().current();
        }
        Optional<Preset> preset = DataTables.INSTANCE.presets().current().byId(file);
        if (preset.isPresent()) {
            return preset.get().schematic();
        }
        Schematic cached = CACHE.get(file);
        if (cached != null) {
            return cached;
        }
        String path = PresetLibrary.SCHEMATICS_ROOT + file + SchematicTextParser.EXTENSION;
        Schematic parsed = SchematicTextParser.parse(JsonResource.readClasspath(path), path)
                .schematic();
        CACHE.put(file, parsed);
        return parsed;
    }
}
