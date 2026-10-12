package com.theminespirit.core.perception;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class CellReport {
    public static final String SCRIPT = "look_at";
    private static final Set<String> AIR_IDS = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air");

    private CellReport() {
    }

    public static Finding describe(BlockCoord pos, Optional<LoadedCell> cell) {
        Objects.requireNonNull(pos, "pos не может быть null: описывается конкретная клетка");
        Objects.requireNonNull(cell, "cell не может быть null: незагруженная клетка — Optional.empty()");
        if (cell.isEmpty()) {
            return new Finding(SCRIPT, List.of(new Sighting(Sighting.UNLOADED, "chunk_not_loaded", pos,
                    "чанк не загружен, клетка пропущена")));
        }
        LoadedCell loaded = cell.get();
        List<Sighting> sightings = new ArrayList<>();
        String blockKind = AIR_IDS.contains(loaded.blockId()) ? Sighting.AIR : Sighting.BLOCK;
        sightings.add(new Sighting(blockKind, loaded.blockId(), pos, ""));
        if (loaded.container()) {
            sightings.add(new Sighting(Sighting.CONTAINER, loaded.blockId(), pos, ""));
        }
        for (String type : loaded.entityTypes()) {
            sightings.add(new Sighting(Sighting.ENTITY, type, pos, ""));
        }
        return new Finding(SCRIPT, sightings);
    }
}
