package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChestYardGuardTests {
    private static final Path TARGETS = Path.of("com/theminespirit/neoforge/board/GraphTargets.java");
    private static final Path POST = Path.of("com/theminespirit/neoforge/board/ExchangeChestPost.java");

    @Test
    void sources_ChestRulesHold_NoViolations() {
        assertEquals(List.of(), violations(SourceFiles.allMain()), "Правило сундуков двора нарушено");
    }

    @Test
    void patternSource_ChestAsStation_IsReported() {
        String bad = "final class GraphTargets { BlockCoord craft() { return find(level, center, ChestBlock.class)"
                + ".orElse(center); } }";

        assertEquals(
                List.of("GraphTargets.java: сундук снова станция вместо верстака"),
                violations(Map.of(TARGETS, bad)),
                "Сторож ослеп: сундук как станция растит кучку сундуков");
    }

    @Test
    void patternSource_StationHeightAboveGround_IsReported() {
        String bad = "final class GraphTargets { private static final int STATION_HEIGHT = 4; }";

        assertEquals(
                List.of("GraphTargets.java: станция ищется выше одного блока от земли"),
                violations(Map.of(TARGETS, bad)),
                "Сторож ослеп: станция над бугром снова поднимает базовую точку");
    }

    @Test
    void patternSource_ExchangeChestCellFromCraftPoint_IsReported() {
        String bad = "final class ExchangeChestPost { Object yard() { return WarehouseLocations.yardCells(level); }"
                + " Optional<BlockCoord> cell(ServerLevel level) { return cell(level, post.craftPoint()); } }";

        assertEquals(
                List.of("ExchangeChestPost.java: клетка обменного сундука снова от точки, а не от ряда двора"),
                violations(Map.of(POST, bad)),
                "Сторож ослеп: обменный сундук вне ряда двора");
    }

    @Test
    void patternSource_ExchangeChestIgnoresYard_IsReported() {
        String bad =
                "final class ExchangeChestPost { Optional<BlockCoord> cell(ServerLevel level) { return search(level); } }";

        assertEquals(
                List.of("ExchangeChestPost.java: клетка обменного сундука берётся не из WarehouseLocations.yardCells"),
                violations(Map.of(POST, bad)),
                "Сторож ослеп: обменный сундук без ряда двора");
    }

    @Test
    void patternSource_StoreCellsInOtherFiles_IsAccepted() {
        String good =
                "final class ChestDemolition { Set<BlockCoord> cells = WarehouseLocations.storeCells(level, ledger); }";

        assertEquals(
                List.of(),
                violations(Map.of(Path.of("com/theminespirit/neoforge/board/ChestDemolition.java"), good)),
                "Сторож ложно срабатывает: storeCells в других файлах — законно");
    }

    @Test
    void warehouseYardCells_TakeFromYardScheme_IsRequired() {
        String source = SourceFiles.withoutTextBlocks(byName(SourceFiles.allMain(), "WarehouseLocations.java"));

        assertTrue(
                source != null && source.contains("YardScheme.slots") && source.contains("YardScheme.overflowSlots"),
                "WarehouseLocations.yardCells обязан брать клетки ряда двора из схемы двора (YardScheme.slots "
                        + "+ overflowSlots), а не из сканирования рельефа: ряд один и задан планом");
    }

    private static String byName(Map<Path, String> sources, String fileName) {
        return sources.entrySet().stream()
                .filter(entry -> entry.getKey().getFileName().toString().equals(fileName))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private static List<String> violations(Map<Path, String> sources) {
        List<String> found = new ArrayList<>();
        String targets = byName(sources, "GraphTargets.java");
        if (targets != null) {
            String source = SourceFiles.withoutTextBlocks(targets);
            if (source.contains("ChestBlock.class")) {
                found.add("GraphTargets.java: сундук снова станция вместо верстака");
            }
            if (source.contains("STATION_HEIGHT = 4")) {
                found.add("GraphTargets.java: станция ищется выше одного блока от земли");
            }
        }
        String post = byName(sources, "ExchangeChestPost.java");
        if (post != null) {
            String source = SourceFiles.withoutTextBlocks(post);
            if (source.contains("craftPoint()")) {
                found.add("ExchangeChestPost.java: клетка обменного сундука снова от точки, а не от ряда двора");
            }
            if (!source.contains("WarehouseLocations.yardCells")) {
                found.add(
                        "ExchangeChestPost.java: клетка обменного сундука берётся не из WarehouseLocations.yardCells");
            }
        }
        return found;
    }
}
