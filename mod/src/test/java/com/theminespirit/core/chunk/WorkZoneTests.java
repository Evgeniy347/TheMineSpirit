package com.theminespirit.core.chunk;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    @Test
    void zone_SamePlanAnchorsAndSpiritChunks_ReusesZone() {
        SettlementZone zone = new SettlementZone();
        TownPlan plan = town(live());
        Set<BlockCoord> anchors = Set.of(new BlockCoord(-160, 70, -480));
        List<BlockCoord> spirits = List.of(new BlockCoord(200, 64, 200));

        assertTrue(zone.update(plan, anchors, spirits, 1, 8), "Первый расчёт зоны обязан её построить");
        assertFalse(
                zone.update(plan, anchors, spirits, 1, 8),
                "Повторный тик с теми же планом, якорями и чанками духов не пересобирает зону: "
                        + "пересборка каждого тика и есть всплеск BoardService");
        assertSame(
                zone.workZone(),
                zone.workZone(),
                "Кэш отдаёт тот же набор чанков, а не копию: иначе тик снова аллоцирует сотни чанков");
    }

    @Test
    void zone_SpiritMovesWithinSameChunk_NoRebuild() {
        SettlementZone zone = new SettlementZone();
        TownPlan plan = town(live());
        Set<BlockCoord> anchors = Set.of(new BlockCoord(-160, 70, -480));

        zone.update(plan, anchors, List.of(new BlockCoord(200, 64, 200)), 1, 8);
        assertFalse(
                zone.update(plan, anchors, List.of(new BlockCoord(205, 64, 207)), 1, 8),
                "Шаг духа внутри чанка не меняет зону работ: пересборка нужна только при смене чанка");
        assertTrue(
                zone.workZone().contains(new ChunkCoord(12, 12)),
                "Чанк духа остаётся в зоне, пока дух не вышел из него");
    }

    @Test
    void zone_SpiritMovesToAnotherChunk_WorkZoneFollows() {
        SettlementZone zone = new SettlementZone();
        TownPlan plan = town(live());
        Set<BlockCoord> anchors = Set.of(new BlockCoord(-160, 70, -480));
        zone.update(plan, anchors, List.of(new BlockCoord(200, 64, 200)), 1, 8);

        assertTrue(
                zone.update(plan, anchors, List.of(new BlockCoord(400, 64, 400)), 1, 8),
                "Смена чанка духа обязана пересобрать зону: иначе новый чанк не держится, и дух застревает");
        assertTrue(zone.workZone().contains(new ChunkCoord(25, 25)), "Зона работ следует за духом в новый чанк");
        assertFalse(
                zone.workZone().contains(new ChunkCoord(13, 13)),
                "Старый чанк духа снимается с зоны: за пределами радиуса чанки не держатся");
    }

    @Test
    void zone_PlanRegionChanges_SettlementFollowsPlan() {
        SettlementZone zone = new SettlementZone();
        Set<BlockCoord> anchors = Set.of(new BlockCoord(0, 70, 0));
        List<BlockCoord> spirits = List.of(new BlockCoord(0, 64, 0));
        TownPlan first = planAt(0, 0);
        TownPlan second = planAt(1000, 1000);

        zone.update(first, anchors, spirits, 1, 8);
        assertTrue(zone.settlement().contains(new ChunkCoord(0, 0)), "Участок первого плана держится");
        assertTrue(
                zone.update(second, anchors, spirits, 1, 8),
                "Новый план поселения обязан пересобрать зону: иначе стройка идёт в незагруженных чанках");
        assertTrue(
                zone.settlement().contains(new ChunkCoord(62, 62)),
                "Чанки нового участка плана попадают в зону поселения");
        assertFalse(
                zone.settlement().contains(new ChunkCoord(5, 5)),
                "Чанки прежнего участка снимаются: план один, а не сумма всех прошлых");
    }

    @Test
    void zone_ProbeRadiusChanges_SettlementCoversRing() {
        SettlementZone zone = new SettlementZone();
        TownPlan plan = planAt(0, 0);
        Set<BlockCoord> anchors = Set.of(new BlockCoord(0, 70, 0));
        List<BlockCoord> spirits = List.of(new BlockCoord(0, 64, 0));

        zone.update(plan, anchors, spirits, 1, 8);
        assertFalse(zone.settlement().contains(new ChunkCoord(2, 0)), "При обмере 8 чанк на 32 блока не нужен");
        assertTrue(
                zone.update(plan, anchors, spirits, 1, 32),
                "Смена радиуса обмера — новое состояние мира, зону поселения надо пересобрать");
        assertTrue(
                zone.settlement().contains(new ChunkCoord(2, 0)),
                "Обмер 32 блока держит чанк на расстоянии 32: иначе аудит читает незагруженный чанк");
    }

    @Test
    void zone_LiveSixSpirits_MatchesWorkZoneAndStaysCached() {
        JsonNode root = live();
        TownPlan plan = town(root);
        List<BlockCoord> spirits = spirits(root);
        Set<BlockCoord> anchors = new LinkedHashSet<>();
        anchors.add(point(root.path("town").path("anchor")));
        int radius = root.path("radiusChunks").asInt();
        SettlementZone zone = new SettlementZone();

        zone.update(plan, anchors, spirits, radius, 8);

        assertTrue(
                zone.workZone().contains(new ChunkCoord(-48 >> 4, -518 >> 4)),
                "Живой прогон observe16: чанк духа на востоке обязан держаться и после кэша");
        assertTrue(
                zone.workZone().contains(new ChunkCoord(-324 >> 4, -472 >> 4)),
                "Живой прогон observe16: чанк духа в шахте/лесу обязан держаться и после кэша");
        assertFalse(
                zone.update(plan, anchors, spirits, radius, 8),
                "Живой снимок с теми же шестью духами не пересобирает зону каждый тик");
    }

    private static TownPlan planAt(int minX, int minZ) {
        ColumnMask empty = new ColumnMask(minX, minZ, 20, 20, new BitSet());
        return new TownPlan(
                new BlockCoord(minX, 70, minZ),
                ColumnMask.rect(minX, minZ, minX + 19, minZ + 19),
                new TownStreets(empty, empty, empty),
                List.of(),
                new FenceLine(List.of(), List.of()),
                List.of());
    }
}
