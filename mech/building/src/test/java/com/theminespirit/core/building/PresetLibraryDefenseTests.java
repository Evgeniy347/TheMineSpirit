package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.reload.LoadedContent;
import com.theminespirit.core.reload.TableStatus;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PresetLibraryDefenseTests {
    private static final String INDEX_PATH = PresetLibrary.SCHEMATICS_ROOT + PresetLibrary.INDEX_ID + ".json";

    @Test
    void byType_Defense_ReturnsPalisadeSection() {
        PresetLibrary library = loadReal();

        assertEquals(
                List.of(
                        "palisade_section",
                        "fence_long",
                        "fence_straight",
                        "fence_post",
                        "fence_corner",
                        "fence_diagonal",
                        "fence_corner45_left",
                        "fence_corner45_right",
                        "fence_wicket",
                        "fence_gate",
                        "wall_3",
                        "wall_3_straight",
                        "wall_3_post",
                        "wall_3_corner",
                        "gate_3"),
                library.byType(BuildingType.DEFENSE).stream().map(Preset::id).toList(),
                "Поиск по типу «оборона» обязан находить палисад-секцию, фрагменты забора и каменной стены: по нему "
                        + "стратег выбирает ограждение");
    }

    private static PresetLibrary loadReal() {
        List<LoadedContent> contents = new ArrayList<>();
        String indexJson = JsonResource.readClasspath(INDEX_PATH);
        contents.add(new LoadedContent(indexJson, INDEX_PATH, TableStatus.Source.MOD));
        for (String path : PresetLibrary.bodyPaths(indexJson, INDEX_PATH)) {
            contents.add(new LoadedContent(JsonResource.readClasspath(path), path, TableStatus.Source.MOD));
        }
        return PresetLibrary.parse(contents);
    }
}
