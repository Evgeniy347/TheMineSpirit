package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.building.SchematicTextParser;
import com.theminespirit.core.inventory.JsonResource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class GraphSchematicClosureTests {
    private static final Path ROOT = Path.of("mod/src/main/resources/" + PresetLibrary.SCHEMATICS_ROOT);

    @Test
    void schematics_EveryBlockItem_ClosesToWorldResource() throws IOException {
        SupplyGraph graph = SupplyGraph.loadDefault();
        Map<String, String> blockItems = blockItems();

        Map<String, String> holes = new LinkedHashMap<>();
        for (Path file : files()) {
            Schematic schematic = SchematicTextParser.parse(Files.readString(file), file.toString())
                    .schematic();
            for (Schematic.Entry entry : schematic.blocks()) {
                if (entry.optional() || SchematicBlocks.isSecondaryPart(entry.block(), entry.state())) {
                    continue;
                }
                String block = path(entry.block());
                String item = blockItems.containsKey(block) ? blockItems.get(block) : block;
                if (item == null || item.isBlank()) {
                    continue;
                }
                String key = block + " → " + item;
                if (!graph.items().containsKey(item) && !graph.isGroup(item)) {
                    holes.putIfAbsent(key, file.getFileName() + ": предмета нет в графе");
                } else if (!closes(graph, item, new LinkedHashSet<>())) {
                    holes.putIfAbsent(key, file.getFileName() + ": нет цепочки до сырья мира");
                }
            }
        }

        assertEquals(
                List.of(),
                new ArrayList<>(summary(holes)),
                "Каждый блок схемы обязан переводиться в предмет графа и закрываться цепочкой добычи до сырья "
                        + "мира. Дыры (блок → предмет: причина)");
    }

    private static Map<String, String> blockItems() {
        JsonNode root = JsonResource.parse(JsonResource.readClasspath(SupplyGraph.RESOURCE), SupplyGraph.RESOURCE);
        JsonNode table = root.path("blockItems");
        assertTrue(
                table.isObject() && !table.isEmpty(),
                "В графе нет раздела blockItems: без таблицы «блок → предмет» материалы схем не свести к предметам");
        Map<String, String> items = new HashMap<>();
        table.properties()
                .forEach(entry -> items.put(
                        entry.getKey(),
                        entry.getValue().isNull() ? "" : entry.getValue().asText()));
        return items;
    }

    private static List<String> summary(Map<String, String> holes) {
        List<String> lines = new ArrayList<>(holes.size());
        holes.forEach((hole, reason) -> lines.add(hole + " (" + reason + ")"));
        return lines;
    }

    private static boolean closes(SupplyGraph graph, String node, Set<String> visiting) {
        if (graph.isGroup(node)) {
            return graph.members(node).stream().anyMatch(member -> closes(graph, member, visiting));
        }
        if (!graph.items().containsKey(node) || !visiting.add(node)) {
            return false;
        }
        try {
            for (SupplyGraph.Way way : graph.producers(node)) {
                if (!graph.implemented(way.id()) || way.world() != null) {
                    return true;
                }
                boolean supplied = true;
                for (String input : way.consumes().keySet()) {
                    if (!closes(graph, input, visiting)) {
                        supplied = false;
                        break;
                    }
                }
                if (supplied) {
                    return true;
                }
            }
            return false;
        } finally {
            visiting.remove(node);
        }
    }

    private static List<Path> files() throws IOException {
        try (Stream<Path> stream = Files.list(ROOT)) {
            return stream.filter(file -> file.toString().endsWith(SchematicTextParser.EXTENSION))
                    .sorted()
                    .toList();
        }
    }

    private static String path(String block) {
        int colon = block.indexOf(':');
        return colon < 0 ? block : block.substring(colon + 1);
    }
}
