package com.theminespirit.core.reload;

import com.theminespirit.core.board.graph.GraphFragments;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.board.graph.VitalNeeds;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicTextParser;
import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.tactics.TacticsConfig;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

final class DataTableDefinitions {
    private DataTableDefinitions() {}

    static <T> TableHolder<T> single(
            TableRegistry registry,
            String id,
            String resourcePath,
            TableDefinition.SingleParser<T> parser,
            Supplier<T> seed) {
        return registry.registerLazy(TableDefinition.single(id, resourcePath, parser), seed);
    }

    static TableHolder<SupplyGraph> supply(TableRegistry registry) {
        vital(registry);
        return graph(registry);
    }

    static TableHolder<SupplyGraph> graph(TableRegistry registry) {
        return registry.registerLazy(
                TableDefinition.composite(
                        "graph",
                        List.of(SupplyGraph.RESOURCE, SupplyGraph.RECIPES),
                        contents -> GraphFragments.mergeWithClasspath(
                                contents.get(0).json(),
                                contents.get(0).origin(),
                                contents.get(1).json(),
                                contents.get(1).origin())),
                SupplyGraph::loadDefault);
    }

    static void vital(TableRegistry registry) {
        registry.registerLazy(
                TableDefinition.single("vital", VitalNeeds.RESOURCE, VitalNeeds::parse), VitalNeeds::needs);
    }

    static TableDefinition<TacticsConfig> tactics() {
        return TableDefinition.composite(
                "tactics",
                List.of(
                        DataTables.TACTICS_ROOT + "enemies.json",
                        DataTables.TACTICS_ROOT + "weights.json",
                        DataTables.TACTICS_ROOT + "rules.json",
                        DataTables.TACTICS_ROOT + "modes.json"),
                contents -> TacticsConfig.fromJson(
                        contents.get(0).json(),
                        contents.get(1).json(),
                        contents.get(2).json(),
                        contents.get(3).json()));
    }

    static TableDefinition<PresetLibrary> presets() {
        return TableDefinition.indexed(
                PresetLibrary.INDEX_ID,
                PresetLibrary.SCHEMATICS_ROOT + PresetLibrary.INDEX_ID + ".json",
                index -> PresetLibrary.bodyPaths(index.json(), index.origin()),
                PresetLibrary::parse);
    }

    static Map<String, TableHolder<Schematic>> schematics(TableRegistry registry) {
        Map<String, TableHolder<Schematic>> liveSchematics = new LinkedHashMap<>();
        for (String file : schematicFiles()) {
            String path = PresetLibrary.SCHEMATICS_ROOT + file + SchematicTextParser.EXTENSION;
            liveSchematics.put(
                    file,
                    registry.registerLazy(
                            TableDefinition.single(
                                    "schematic_" + file,
                                    path,
                                    (text, origin) -> SchematicTextParser.parse(text, origin)
                                            .schematic()),
                            () -> SchematicTextParser.parse(JsonResource.readClasspath(path), path)
                                    .schematic()));
        }
        return Map.copyOf(liveSchematics);
    }

    static List<String> schematicFiles() {
        return List.of(
                "fixture_plate",
                "fence",
                "storage_v1",
                "storage_v2",
                "storage_v3",
                "storage_v4",
                "reach_wall",
                "retreat_nook",
                "shelter",
                "state_demo");
    }
}
