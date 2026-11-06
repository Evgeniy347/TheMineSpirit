package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.inventory.JsonResource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class RecipeImportTests {
    private static final SupplyGraph MANUAL =
            SupplyGraph.parse(JsonResource.readClasspath(SupplyGraph.RESOURCE), SupplyGraph.RESOURCE);
    private static final RecipeImport.Result IMPORT =
            RecipeImport.importRecipes(MinecraftRecipeData.recipes(), MinecraftRecipeData.tags(), MANUAL);

    @Test
    void import_ManualCraftingAndSmeltingWays_AreReproduced() {
        Map<String, List<SupplyGraph.Way>> imported = new TreeMap<>();
        for (SupplyGraph.Way way : IMPORT.ways()) {
            imported.computeIfAbsent(way.makes(), key -> new ArrayList<>()).add(way);
        }
        List<String> discrepancies = new ArrayList<>();
        List<String> manualOnly = new ArrayList<>();
        for (SupplyGraph.Way manual : MANUAL.ways()) {
            if (!craftOrSmelt(manual)) {
                continue;
            }
            List<SupplyGraph.Way> candidates = imported.getOrDefault(manual.makes(), List.of());
            if (candidates.isEmpty()) {
                manualOnly.add(manual.id());
                continue;
            }
            boolean matched = candidates.stream().anyMatch(candidate -> matches(manual, candidate));
            if (!matched) {
                discrepancies.add(manual.id() + " " + manual.makes() + " " + manual.consumes()
                        + " yield " + manual.yield() + " → "
                        + candidates.stream()
                                .map(way -> way.id() + " " + way.consumes() + " yield " + way.yield())
                                .toList());
            }
        }
        assertEquals(
                List.of(),
                discrepancies,
                "Импорт не воспроизвёл ручные пути крафта и переплавки: makes, входы (с раскрытием групп) "
                        + "и yield расходятся. Ручные пути без ванильного рецепта: " + manualOnly);
    }

    @Test
    void import_Sticks_UsePlanksGroup() {
        SupplyGraph.Way way = way("stick");
        assertEquals(
                Map.of("#planks", 2),
                way.consumes(),
                "Палки делаются из группы #planks, а не из отдельной породы дерева");
        assertEquals(4, way.yield(), "Стойка палок даёт 4 палки");
        assertTrue(
                IMPORT.groups().get("#planks").contains("oak_planks"),
                "Группа #planks обязана содержать дубовые доски");
    }

    @Test
    void import_Torch_UsesCoalsGroupFromAlternatives() {
        SupplyGraph.Way way = way("torch");
        assertEquals(
                Map.of("stick", 1, "#coals", 1),
                way.consumes(),
                "Список альтернатив «уголь или древесный уголь» — это группа #coals, а не отдельная группа");
        assertEquals(
                List.of("coal", "charcoal"),
                IMPORT.groups().get("#coals"),
                "Группа #coals обязана совпадать с ванильным тегом coals");
    }

    @Test
    void import_StonePickaxe_UsesStoneToolMaterialsGroup() {
        SupplyGraph.Way way = way("stone_pickaxe");
        assertEquals(
                Map.of("stick", 2, "#stone_tool_materials", 3),
                way.consumes(),
                "Каменная кирка делается из ванильного тега stone_tool_materials");
        assertTrue(
                IMPORT.groups()
                        .get("#stone_tool_materials")
                        .containsAll(List.of("cobblestone", "cobbled_deepslate", "blackstone")),
                "В группу stone_tool_materials входят булыжник, глубинный булыжник и чёрный камень");
    }

    @Test
    void import_SmeltingBlastingSmoking_AddCoalsFuel() {
        List<String> missing = IMPORT.ways().stream()
                .filter(way -> way.building() != null
                        && List.of("furnace", "blast_furnace", "smoker").contains(way.building()))
                .filter(way -> !Integer.valueOf(1).equals(way.consumes().get("#coals")))
                .map(way -> way.id() + " " + way.consumes())
                .toList();
        assertEquals(
                List.of(), missing, "Печь, плавильня и коптильня расходуют топливо: в путях обязателен вход #coals 1");
    }

    @Test
    void import_BlockAndIngotRoundTrip_BothWeightsAreLow() {
        assertTrue(
                hasWay("iron_block", "iron_ingot", RecipeImport.REVERSE_WEIGHT),
                "Путь слиток → блок обязан иметь низкий вес: пара «туда-обратно» не должна зацикливать граф");
        assertTrue(
                hasWay("iron_ingot", "iron_block", RecipeImport.REVERSE_WEIGHT),
                "Путь блок → слиток обязан иметь низкий вес: пара «туда-обратно» не должна зацикливать граф");
        assertTrue(
                hasWay("iron_nugget", "iron_ingot", RecipeImport.REVERSE_WEIGHT),
                "Путь слиток → самородок обязан иметь низкий вес");
        assertTrue(
                hasWay("iron_ingot", "iron_nugget", RecipeImport.REVERSE_WEIGHT),
                "Путь самородок → слиток обязан иметь низкий вес");
    }

    @Test
    void select_ManualMakes_AreNotGenerated() {
        Set<String> manualMakes = new HashSet<>();
        MANUAL.ways().forEach(way -> manualMakes.add(way.makes()));
        List<SupplyGraph.Way> selected = RecipeImport.select(IMPORT.ways(), MANUAL, 3);
        List<String> dead = selected.stream()
                .filter(way -> manualMakes.contains(way.makes()))
                .map(SupplyGraph.Way::id)
                .toList();
        assertEquals(
                List.of(),
                dead,
                "Сгенерированный путь для предмета с ручным способом мёртв: слияние графа отбрасывает его по makes. "
                        + "Файл graph-recipes.json копит пути, которых нет в графе; пропускай makes ручного графа");
        assertTrue(
                selected.stream().anyMatch(way -> !manualMakes.contains(way.makes())),
                "Отбор обязан оставить сгенерированные пути для предметов без ручного способа");
    }

    @Test
    void select_MoreThanThreeGeneratedWays_AreCut() {
        List<SupplyGraph.Way> selected = RecipeImport.select(IMPORT.ways(), MANUAL, 3);
        Map<String, Integer> counts = new TreeMap<>();
        for (SupplyGraph.Way way : selected) {
            counts.merge(way.makes(), 1, Integer::sum);
        }
        List<String> crowded = counts.entrySet().stream()
                .filter(entry -> entry.getValue() > 3)
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .toList();
        assertEquals(
                List.of(), crowded, "На предмет остаётся не больше трёх путей: иначе граф уходит в перебор рецептов");
    }

    private static SupplyGraph.Way way(String makes) {
        return IMPORT.ways().stream()
                .filter(way -> way.makes().equals(makes))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Импорт не дал пути для " + makes));
    }

    private static boolean hasWay(String makes, String input, double weight) {
        return IMPORT.ways().stream()
                .anyMatch(way -> way.makes().equals(makes)
                        && way.consumes().containsKey(input)
                        && Math.abs(way.weight() - weight) < 1e-9);
    }

    private static boolean craftOrSmelt(SupplyGraph.Way way) {
        String name = way.boardCase().name();
        return name.startsWith("CRAFT_") || name.startsWith("SMELT_");
    }

    private static boolean matches(SupplyGraph.Way manual, SupplyGraph.Way candidate) {
        if (manual.yield() != candidate.yield()) {
            return false;
        }
        Map<String, Integer> imported = new LinkedHashMap<>(candidate.consumes());
        if (!manual.consumes().containsKey("#coals") && fuelled(candidate.building())) {
            imported.remove("#coals");
        }
        return expand(manual.consumes()).equals(expand(imported));
    }

    private static boolean fuelled(String building) {
        return "furnace".equals(building) || "blast_furnace".equals(building) || "smoker".equals(building);
    }

    private static Map<String, Integer> expand(Map<String, Integer> consumes) {
        Map<String, Integer> out = new TreeMap<>();
        consumes.forEach((node, count) -> {
            List<String> members = node.startsWith("#") ? group(node) : List.of(node);
            for (String member : members) {
                if (MANUAL.items().containsKey(member) || MANUAL.isTool(member)) {
                    out.merge(member, count, Integer::sum);
                }
            }
        });
        return out;
    }

    private static List<String> group(String node) {
        List<String> members = IMPORT.groups().get(node);
        if (members == null) {
            members = MANUAL.groups().get(node);
        }
        return members == null ? List.of(node) : members;
    }
}
