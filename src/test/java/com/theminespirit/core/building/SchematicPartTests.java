package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SchematicPartTests {
    private static Schematic ring() {
        List<Schematic.Entry> blocks = new ArrayList<>();
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                boolean edge = x == 0 || z == 0 || x == 8 || z == 8;
                if (!edge) {
                    continue;
                }
                boolean corner = (x == 0 || x == 8) && (z == 0 || z == 8);
                blocks.add(new Schematic.Entry(x, 0, z, corner ? "oak_log" : "oak_fence"));
                if (corner) {
                    blocks.add(new Schematic.Entry(x, 1, z, "torch"));
                }
            }
        }
        return new Schematic(9, 2, 9, blocks);
    }

    @Test
    void of_FourParts_CoverWholeWithoutOverlap() {
        Schematic whole = ring();
        Set<String> seen = new HashSet<>();
        int total = 0;
        for (int part = 1; part <= 4; part++) {
            List<Schematic.Entry> blocks = SchematicPart.of(whole, part, 4).blocks();
            assertTrue(blocks.size() >= 8, "кусок " + part + " почти пуст: " + blocks.size());
            for (Schematic.Entry entry : blocks) {
                assertTrue(seen.add(entry.dx() + "," + entry.dy() + "," + entry.dz()), "клетка в двух кусках");
            }
            total += blocks.size();
        }
        assertEquals(whole.blocks().size(), total, "куски вместе — вся схема");
    }

    @Test
    void of_TorchOnPost_StaysInPostPart() {
        Schematic whole = ring();
        for (int part = 1; part <= 4; part++) {
            Set<String> posts = new HashSet<>();
            List<Schematic.Entry> blocks = SchematicPart.of(whole, part, 4).blocks();
            blocks.stream().filter(e -> e.dy() == 0).forEach(e -> posts.add(e.dx() + "," + e.dz()));
            blocks.stream()
                    .filter(e -> e.dy() == 1)
                    .forEach(e -> assertTrue(posts.contains(e.dx() + "," + e.dz()), "факел без своего столба"));
        }
    }

    @Test
    void parse_PartName_RoundTrips() {
        String name = SchematicPart.name("fence", 3, 4);
        assertEquals(Optional.of(new SchematicPart.Ref("fence", 3, 4)), SchematicPart.parse(name), "имя куска");
        assertEquals(Optional.empty(), SchematicPart.parse("fence"), "целая схема");
        assertEquals(Optional.empty(), SchematicPart.parse("fence#9/4"), "кусок вне числа кусков");
    }
}
