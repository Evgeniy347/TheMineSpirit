package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicTextParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DefensePerimeterGuardTests {
    private static final Path ROOT = Path.of("src/main/resources/" + PresetLibrary.SCHEMATICS_ROOT);

    static List<String> lowBlocks(Schematic schematic) {
        List<String> problems = new ArrayList<>();
        for (Schematic.Entry entry : schematic.blocks()) {
            boolean perimeter = entry.dx() == 0
                    || entry.dz() == 0
                    || entry.dx() == schematic.sizeX() - 1
                    || entry.dz() == schematic.sizeZ() - 1;
            if (entry.dy() == 0 && perimeter && !tall(entry.block())) {
                problems.add(entry.block() + "@" + entry.dx() + "," + entry.dz());
            }
        }
        return problems;
    }

    private static boolean tall(String block) {
        return block.endsWith("_fence") || block.endsWith("_fence_gate") || block.endsWith("_wall");
    }

    @Test
    void defenseSchemes_PerimeterLine_OnlyFenceHigh() throws IOException {
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.list(ROOT)) {
            for (Path file : files.filter(path -> path.toString().endsWith(SchematicTextParser.EXTENSION))
                    .sorted()
                    .toList()) {
                String text = Files.readString(file);
                if (!text.lines().anyMatch(line -> "type: defense".equals(line.strip()))) {
                    continue;
                }
                Schematic schematic =
                        SchematicTextParser.parse(text, file.toString()).schematic();
                lowBlocks(schematic).forEach(problem -> problems.add(file.getFileName() + ": " + problem));
            }
        }

        assertEquals(
                List.of(),
                problems,
                "Оборонная схема: в линии периметра на земле — только забор, калитка или стена (1,5 блока). Бревно "
                        + "высотой 1 перепрыгивается, и ограда проходима (INC-53). Как надо: столб — забор, факел "
                        + "держится на заборе");
    }

    @Test
    void lowBlocks_LogInLine_Reported() {
        Schematic schematic = SchematicTextParser.parse("""
                                type: defense
                                size: 3x1x1
                                legend:
                                  'F': oak_fence
                                  'L': oak_log[axis=y]
                                layers:
                                  - y0: |
                                      FLF
                                """, "test").schematic();

        assertEquals(
                List.of("minecraft:oak_log@1,0"),
                lowBlocks(schematic),
                "Шаблон сторожа: бревно в линии забора обязано попасть в отчёт, иначе сторож молчит о проходе");
    }
}
