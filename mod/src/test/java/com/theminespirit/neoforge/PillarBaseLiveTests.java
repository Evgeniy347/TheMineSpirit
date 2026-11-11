package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.building.PillarBase;
import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PillarBaseLiveTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String FILE = "src/test/resources/live/undo-base-172-69-516.json";
    private static final int X = -172;
    private static final int Z = -516;

    @Test
    void first_FeetInTheAir_StartsPillarOnTheNearestSupportBelow() throws IOException {
        World world = World.load(FILE);

        int start = PillarBase.first(69, 0, 4, y -> world.supported(X, y, Z));

        assertEquals(
                68,
                start,
                "Дух прыгнул (ноги y=69), под ногами воздух: первый блок столба ставят на опору y=68, а не отказывают");
        assertFalse(world.solid(X, 68, Z), "Клетка 68 свободна: опора под ней — земля y=67");
        assertTrue(world.solid(X, 67, Z), "Земля под первым блоком обязана быть");
    }

    @Test
    void first_FeetOnGround_KeepsTheColumnBase() throws IOException {
        World world = World.load(FILE);

        int start = PillarBase.first(68, 0, 4, y -> world.supported(X, y, Z));

        assertEquals(68, start, "Дух стоит на земле: столб начинают с его ног, а не ищут опору ниже");
    }

    @Test
    void first_NoSupportWithinDrop_KeepsBaseForTheCallersRefusal() {
        assertEquals(
                70,
                PillarBase.first(70, 0, 4, y -> false),
                "Опора дальше предела спуска: столб не начинают в воздухе, отказ остаётся за местом постановки");
    }

    @Test
    void first_SupportBelowWorldFloor_KeepsBase() {
        assertEquals(2, PillarBase.first(2, 3, 4, y -> true), "Ниже дна мира опору не ищут: столб не уходит под мир");
    }

    private record World(Set<BlockCoord> solid) {
        static World load(String file) throws IOException {
            JsonNode root = MAPPER.readTree(Files.readString(Path.of(file)));
            Set<BlockCoord> solid = new HashSet<>();
            for (JsonNode block : root.path("blocks")) {
                solid.add(new BlockCoord(
                        block.path("x").asInt(),
                        block.path("y").asInt(),
                        block.path("z").asInt()));
            }
            return new World(Set.copyOf(solid));
        }

        boolean solid(int x, int y, int z) {
            return solid.contains(new BlockCoord(x, y, z));
        }

        boolean supported(int x, int y, int z) {
            return solid(x, y - 1, z)
                    || solid(x - 1, y, z)
                    || solid(x + 1, y, z)
                    || solid(x, y, z - 1)
                    || solid(x, y, z + 1);
        }
    }
}
