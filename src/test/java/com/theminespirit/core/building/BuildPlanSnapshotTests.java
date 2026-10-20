package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.LoadedContent;
import com.theminespirit.core.reload.TableStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

// Снапшот плана стройки (решение владельца 20.10): на каждую постройку каталога × 4 поворота — текстовый план заходов и
// стоянок. Тест краснеет только при изменении плана; обновление — флагом -Dtheminespirit.updateSnapshots=true, дифф
// снапшотов идёт в коммит и читается при ревью.
class BuildPlanSnapshotTests {
    static final Path DIR = Path.of("src/test/resources/build-plans");
    private static final String INDEX_PATH = PresetLibrary.SCHEMATICS_ROOT + PresetLibrary.INDEX_ID + ".json";
    private static final int SUPPLY_RING = 2;
    private static final int SUPPLY_SIDE_OFFSET = 3;
    private static final SpiritReach REACH = new SpiritReach(3, 15, 3);

    static PresetLibrary library() {
        List<LoadedContent> contents = new ArrayList<>();
        String indexJson = JsonResource.readClasspath(INDEX_PATH);
        contents.add(new LoadedContent(indexJson, INDEX_PATH, TableStatus.Source.MOD));
        for (String path : PresetLibrary.bodyPaths(indexJson, INDEX_PATH)) {
            contents.add(new LoadedContent(JsonResource.readClasspath(path), path, TableStatus.Source.MOD));
        }
        return PresetLibrary.parse(contents);
    }

    static List<String> schemes() {
        List<String> schemes = new ArrayList<>();
        String catalog = JsonResource.readClasspath(DemoBuildings.CATALOG);
        for (DemoBuildings.Building building : DemoBuildings.parse(catalog, DemoBuildings.CATALOG)) {
            for (DemoBuildings.Level level : building.levels()) {
                if (!schemes.contains(level.scheme())) {
                    schemes.add(level.scheme());
                }
            }
        }
        return schemes;
    }

    // Сундук — как в аудите стройки: перед дверью за крыльцом и кольцом, сбоку на SUPPLY_SIDE_OFFSET; дух — между.
    static BlockCoord supplyFeet(Preset preset, BuildRotation rotation) {
        Schematic schematic = preset.schematic();
        DemoPlacement.Door door = DemoPlacement.Door.of(preset);
        BuildRotation.Cell cell = rotation.rotate(door.x(), door.z(), schematic.sizeX(), schematic.sizeZ());
        BuildRotation.DirectionVector out =
                BuildRotation.rotateDirection(door.normalDx(), door.normalDz(), rotation.quarterTurns());
        int frontX = cell.x() + out.dx() * (door.porch() + SUPPLY_RING);
        int frontZ = cell.z() + out.dz() * (door.porch() + SUPPLY_RING);
        int sideX = -out.dz();
        int sideZ = out.dx();
        return new BlockCoord(frontX + sideX * (SUPPLY_SIDE_OFFSET - 2), 0, frontZ + sideZ * (SUPPLY_SIDE_OFFSET - 2));
    }

    static BuildPlanner.Plan plan(Preset preset, BuildRotation rotation, BuildPlanner.Portion portion) {
        return BuildPlanner.plan(preset.schematic(), rotation, supplyFeet(preset, rotation), REACH, portion);
    }

    @Test
    void plans_EveryCatalogBuilding_MatchSnapshot() throws IOException {
        boolean update = Boolean.getBoolean("theminespirit.updateSnapshots");
        PresetLibrary library = library();
        List<String> changed = new ArrayList<>();
        for (String scheme : schemes()) {
            Optional<Preset> preset = library.byId(scheme);
            assertTrue(preset.isPresent(), "постройка каталога '" + scheme + "' без схемы: план не снять");
            for (BuildRotation rotation : BuildRotation.values()) {
                Path file = DIR.resolve(scheme + "-" + rotation.id() + ".txt");
                List<String> lines = new ArrayList<>();
                lines.add(
                        "# " + scheme + " " + rotation.id()
                                + ", порции: черновые от текущей полосы (4 слоя) вверх, отделка — в конце, досягаемость 3/15/3");
                lines.addAll(
                        plan(preset.get(), rotation, BuildPlanner.Portion.FILL).lines());
                if (update) {
                    Files.createDirectories(DIR);
                    Files.writeString(file, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
                } else if (!Files.exists(file)
                        || !Files.readAllLines(file, StandardCharsets.UTF_8).equals(lines)) {
                    changed.add(file.getFileName().toString());
                }
            }
        }
        assertEquals(
                List.of(),
                changed,
                "план стройки изменился (или снапшота нет): изменение плана — изменение поведения стройки, его читают "
                        + "при ревью. Осознанно — перезапиши снапшоты флагом -Dtheminespirit.updateSnapshots=true и "
                        + "закоммить дифф build-plans/");
    }
}
