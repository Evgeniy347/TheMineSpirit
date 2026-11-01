package com.theminespirit.core.chunk;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.FenceLine;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.TownStreets;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkZoneTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode live() {
        try (InputStream in = WorkZoneTests.class.getResourceAsStream("/live/observe16-forceload/positions.json")) {
            if (in == null) {
                throw new IllegalStateException("Нет фикстуры observe16: /live/observe16-forceload/positions.json");
            }
            return MAPPER.readTree(in);
        } catch (IOException error) {
            throw new IllegalStateException("Фикстура observe16 не читается", error);
        }
    }

    private static TownPlan town(JsonNode root) {
        JsonNode town = root.path("town");
        int minX = town.path("minX").asInt();
        int minZ = town.path("minZ").asInt();
        int width = town.path("width").asInt();
        int depth = town.path("depth").asInt();
        ColumnMask empty = new ColumnMask(minX, minZ, width, depth, new BitSet());
        JsonNode anchor = town.path("anchor");
        return new TownPlan(
                new BlockCoord(
                        anchor.get(0).asInt(),
                        anchor.get(1).asInt(),
                        anchor.get(2).asInt()),
                ColumnMask.rect(minX, minZ, minX + width - 1, minZ + depth - 1),
                new TownStreets(empty, empty, empty),
                List.of(),
                new FenceLine(List.of(), List.of()),
                List.of());
    }

    private static BlockCoord point(JsonNode node) {
        return new BlockCoord(
                node.get(0).asInt(), node.get(1).asInt(), node.get(2).asInt());
    }

    private static List<BlockCoord> spirits(JsonNode root) {
        List<BlockCoord> points = new ArrayList<>();
        root.path("spirits").forEach(node -> points.add(point(node)));
        return points;
    }

    private static Set<ChunkCoord> baseForceload(JsonNode root) {
        JsonNode base = root.path("baseForceloadBlocks");
        int minX = Math.floorDiv(base.path("minX").asInt(), 16);
        int maxX = Math.floorDiv(base.path("maxX").asInt(), 16);
        int minZ = Math.floorDiv(base.path("minZ").asInt(), 16);
        int maxZ = Math.floorDiv(base.path("maxZ").asInt(), 16);
        Set<ChunkCoord> chunks = new LinkedHashSet<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                chunks.add(new ChunkCoord(x, z));
            }
        }
        return chunks;
    }

    @Test
    void workZone_SpiritsOutsideBaseForceload_ChunksHeld() {
        JsonNode root = live();
        TownPlan plan = town(root);
        List<BlockCoord> spirits = spirits(root);
        int radius = root.path("radiusChunks").asInt();
        Set<BlockCoord> anchors = new LinkedHashSet<>();
        anchors.add(point(root.path("town").path("anchor")));
        Set<ChunkCoord> base = baseForceload(root);
        ChunkCoord east = new ChunkCoord(-48 >> 4, -518 >> 4);
        ChunkCoord west = new ChunkCoord(-324 >> 4, -472 >> 4);

        assertFalse(
                base.contains(east),
                "Фикстура observe16: chunk " + east + " (spirit-2, x=-48) вне базовых forceload — там дух замёрз");
        assertFalse(
                base.contains(west),
                "Фикстура observe16: chunk " + west + " (spirit-5, x=-324) вне базовых forceload — шахта/лес");

        Set<ChunkCoord> zone = SettlementChunks.workZone(plan, anchors, spirits, radius);

        assertTrue(zone.contains(east), "Зона работ обязана включать чанк духа на востоке " + east);
        assertTrue(zone.contains(west), "Зона работ обязана включать чанк духа в шахте/лесу " + west);
        assertTrue(
                zone.contains(new ChunkCoord(west.x() - radius, west.z())),
                "Радиус духа задан в чанках: чанк в " + radius + " от духа обязан держаться, иначе подход к цели "
                        + "снова уйдёт в незагруженный чанк");
        assertFalse(
                zone.contains(new ChunkCoord(west.x() - radius - 1, west.z())),
                "За пределом радиуса зона не растёт: иначе зона работ превратится в весь мир");
        assertTrue(
                zone.contains(new ChunkCoord(-11, -32)),
                "Зона работ обязана включать чанки поселения (регион плана), а не только духов");
    }
}
