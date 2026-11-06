package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.theminespirit.core.inventory.JsonResource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("export")
class RecipeExportTests {
    private static final Path OUT = Path.of("mod/src/main/resources/" + SupplyGraph.RECIPES);
    private static final int MAX_WAYS = 3;

    @Test
    void export_VanillaRecipes_WriteGraphRecipes() throws IOException {
        String text = render();
        Files.writeString(OUT, text, StandardCharsets.UTF_8);
        assertTrue(
                Files.size(OUT) > 0,
                "Импорт ванильных рецептов записан в " + OUT + ": путей "
                        + text.lines().count());
    }

    static String render() throws IOException {
        SupplyGraph manual = SupplyGraph.parse(JsonResource.readClasspath(SupplyGraph.RESOURCE), SupplyGraph.RESOURCE);
        RecipeImport.Result result =
                RecipeImport.importRecipes(MinecraftRecipeData.recipes(), MinecraftRecipeData.tags(), manual);
        List<SupplyGraph.Way> ways = RecipeImport.select(result.ways(), manual, MAX_WAYS);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("items", items(result.items()));
        root.put("groups", new TreeMap<>(result.groups()));
        root.put("buildings", buildings(result.buildings()));
        root.put("ways", ways(ways));

        String text = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .writeValueAsString(root)
                .replace("\r\n", "\n");
        return text + "\n";
    }

    private static Map<String, Object> items(Map<String, SupplyGraph.Item> items) {
        Map<String, Object> out = new TreeMap<>();
        items.forEach((id, item) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("xmin", item.xmin());
            row.put("base", item.base());
            row.put("stage", item.stage());
            out.put(id, row);
        });
        return out;
    }

    private static Map<String, Object> buildings(Map<String, SupplyGraph.Building> buildings) {
        Map<String, Object> out = new TreeMap<>();
        buildings.forEach((id, building) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("case", building.boardCase().name());
            row.put("stage", building.stage());
            row.put("base", building.base());
            row.put("optional", building.optional());
            out.put(id, row);
        });
        return out;
    }

    private static List<Map<String, Object>> ways(List<SupplyGraph.Way> ways) {
        List<Map<String, Object>> out = new ArrayList<>(ways.size());
        for (SupplyGraph.Way way : ways) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", way.id());
            row.put("case", way.boardCase().name());
            row.put("makes", way.makes());
            row.put("yield", way.yield());
            row.put("weight", way.weight());
            row.put("consumes", new TreeMap<>(way.consumes()));
            if (way.building() != null) {
                row.put("building", way.building());
            }
            out.add(row);
        }
        return out;
    }
}
