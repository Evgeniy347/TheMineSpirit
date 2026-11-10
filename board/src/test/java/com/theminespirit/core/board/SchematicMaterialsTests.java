package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.theminespirit.core.building.BuildingType;
import com.theminespirit.core.building.Preset;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
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
    void of_KnownPreset_ReusesMaterialsWithoutCountingBlocksAgain() {
        Preset preset = preset(BuildingType.HOUSING, Map.of("oak_planks", 12));

        Map<String, Integer> need = SchematicMaterials.of(Optional.of(preset), () -> {
            throw new IllegalStateException("у пресета материалы уже посчитаны при разборе — блоки не пересчитываем");
        });

        assertSame(
                preset.materials(),
                need,
                "Материалы известного пресета берутся готовыми: пересчёт блоков схемы на каждый провал "
                        + "ремонта — заметная работа на больших схемах, а материалы уже лежат в пресете");
    }

    @Test
    void of_UndergroundPreset_NeedsNothingWithoutCounting() {
        Preset preset = preset(BuildingType.UNDERGROUND, Map.of("stone", 5));

        assertEquals(
                Map.of(),
                SchematicMaterials.of(Optional.of(preset), () -> {
                    throw new IllegalStateException("подземная схема не читает блоки");
                }),
                "Подземная схема строится своей проходкой: её материалы не запрашиваются, даже если пресет их помнит");
    }

    @Test
    void of_UnknownFile_CountsBlocksOnce() {
        assertEquals(
                Map.of("oak_fence", 32),
                SchematicMaterials.of(Optional.empty(), () -> Map.of("oak_fence", 32)),
                "Файл без пресета считается из схемы: иначе ремонт не узнает, что докупить");
    }

    @Test
    void known_RepairWithoutSchematicFile_IsUnknown() {
        assertEquals(false, SchematicMaterials.known(Optional.empty()), "ремонт без файла схемы не сверяется с миром");
        assertEquals(true, SchematicMaterials.known(Optional.of(BuildingType.HOUSING)), "схема каталога сверяется");
    }

    private static Preset preset(BuildingType type, Map<String, Integer> materials) {
        Schematic schematic =
                new Schematic(1, 1, 1, List.of(new Schematic.Entry(0, 0, 0, "minecraft:oak_planks")), List.of());
        return new Preset(
                "fence",
                "Забор",
                1,
                type,
                Preset.LEVEL_NONE,
                Optional.empty(),
                schematic,
                new BlockCoord(0, 0, 0),
                materials);
    }
}
