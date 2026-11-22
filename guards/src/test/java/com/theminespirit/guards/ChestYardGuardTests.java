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

    @Test
    void sources_ChestRulesHold_NoViolations() {
        assertEquals(
                List.of(),
                violations(SourceFiles.allMain()),
                "Правило сундуков двора нарушено"
                        + " Иначе тест sources_ChestRulesHold_NoViolations перестанет ловить эту поломку. При смене sources_ChestRulesHold_NoViolations проверь это утверждение.");
    }

    @Test
    void patternSource_ChestAsStation_IsReported() {
        String bad = "final class GraphTargets { BlockCoord craft() { return find(level, center, ChestBlock.class)"
                + ".orElse(center); } }";

        assertEquals(
                List.of("GraphTargets.java: сундук снова станция вместо верстака"),
                violations(Map.of(TARGETS, bad)),
                "Сторож ослеп: сундук как станция растит кучку сундуков"
                        + " Иначе тест patternSource_ChestAsStation_IsReported перестанет ловить эту поломку. При смене patternSource_ChestAsStation_IsReported проверь это утверждение.");
    }

    @Test
    void patternSource_StationHeightAboveGround_IsReported() {
        String bad = "final class GraphTargets { private static final int STATION_HEIGHT = 4; }";

        assertEquals(
                List.of("GraphTargets.java: станция ищется выше одного блока от земли"),
                violations(Map.of(TARGETS, bad)),
                "Сторож ослеп: станция над бугром снова поднимает базовую точку"
                        + " Иначе тест patternSource_StationHeightAboveGround_IsReported перестанет ловить эту поломку. При смене patternSource_StationHeightAboveGround_IsReported проверь это утверждение.");
    }

    @Test
    void patternSource_StoreCellsInOtherFiles_IsAccepted() {
        String good =
                "final class ChestDemolition { Set<BlockCoord> cells = WarehouseLocations.storeCells(level, ledger); }";

        assertEquals(
                List.of(),
                violations(Map.of(Path.of("com/theminespirit/neoforge/board/ChestDemolition.java"), good)),
                "Сторож ложно срабатывает: storeCells в других файлах — законно"
                        + " Иначе тест patternSource_StoreCellsInOtherFiles_IsAccepted перестанет ловить эту поломку. При смене patternSource_StoreCellsInOtherFiles_IsAccepted проверь это утверждение.");
    }

    @Test
    void warehouseChestCells_TakeFromBuiltSchemes_IsRequired() {
        String source = SourceFiles.withoutTextBlocks(byName(SourceFiles.allMain(), "WarehouseLocations.java"));

        assertTrue(
                source != null && source.contains("WarehouseChests.cells") && !source.contains("YardScheme"),
                "WarehouseLocations берёт клетки сундуков только из схем построенных складов: двор и заказы "
                        + "больше не дают мест под сундуки"
                        + " Иначе тест warehouseChestCells_TakeFromBuiltSchemes_IsRequired перестанет ловить эту поломку. При смене warehouseChestCells_TakeFromBuiltSchemes_IsRequired проверь это утверждение.");
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
        return found;
    }
}
