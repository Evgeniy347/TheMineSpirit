package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.building.BuildingType;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SchematicMaterialsTests {
    @Test
    void count_UnknownSchematic_NeedsNothingWithoutLoadingFile() {
        Map<String, Integer> need = SchematicMaterials.count(Optional.empty(), () -> {
            throw new IllegalStateException("файл неизвестной схемы не должен читаться");
        });

        assertEquals(Map.of(), need, "неизвестная схема (ремонт без файла) не требует материалов и не читает файл");
    }

    @Test
    void count_UndergroundSchematic_NeedsNothing() {
        assertEquals(
                Map.of(),
                SchematicMaterials.count(Optional.of(BuildingType.UNDERGROUND), () -> Map.of("stone", 5)),
                "подземная схема строится из своей проходки");
    }

    @Test
    void count_SurfaceSchematic_CountsBlocks() {
        Map<String, Integer> need =
                SchematicMaterials.count(Optional.of(BuildingType.HOUSING), () -> Map.of("oak_planks", 12));

        assertEquals(Map.of("oak_planks", 12), need, "наземная схема требует свои блоки");
    }

    @Test
    void known_RepairWithoutSchematicFile_IsUnknown() {
        assertEquals(false, SchematicMaterials.known(Optional.empty()), "ремонт без файла схемы не сверяется с миром");
        assertEquals(true, SchematicMaterials.known(Optional.of(BuildingType.HOUSING)), "схема каталога сверяется");
    }
}
