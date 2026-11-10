package com.theminespirit.core.reload;

import com.theminespirit.core.board.BoardConfig;
import com.theminespirit.core.board.DemandRules;
import com.theminespirit.core.board.LevelingRules;
import com.theminespirit.core.board.StageRules;
import com.theminespirit.core.board.WoodRules;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.building.BuildData;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.town.TownRules;
import java.util.Map;

record BoardTables(
        TableHolder<PresetLibrary> presets,
        TableHolder<BoardConfig> board,
        TableHolder<SupplyGraph> graph,
        TableHolder<BuildData> building,
        TableHolder<StageRules> stages,
        TableHolder<DemandRules> demand,
        TableHolder<WoodRules> wood,
        TableHolder<LevelingRules> leveling,
        TableHolder<TownRules> town,
        Map<String, TableHolder<Schematic>> schematics) {
    static BoardTables register(TableRegistry registry) {
        return new BoardTables(
                registry.registerLazy(DataTableDefinitions.presets(), PresetLibrary::loadDefault),
                DataTableDefinitions.single(
                        registry, "board", BoardConfig.RESOURCE, BoardConfig::fromJson, BoardConfig::readFromClasspath),
                DataTableDefinitions.supply(registry),
                DataTableDefinitions.single(
                        registry, "building", BuildData.RESOURCE, BuildData::parse, BuildData::load),
                DataTableDefinitions.single(
                        registry, "stages", StageRules.RESOURCE, StageRules::parse, StageRules::load),
                DataTableDefinitions.single(
                        registry, "settlement_demand", DemandRules.RESOURCE, DemandRules::parse, DemandRules::load),
                DataTableDefinitions.single(registry, "wood", WoodRules.RESOURCE, WoodRules::parse, WoodRules::load),
                DataTableDefinitions.single(
                        registry, "leveling", LevelingRules.RESOURCE, LevelingRules::parse, LevelingRules::load),
                DataTableDefinitions.single(registry, "town", TownRules.RESOURCE, TownRules::parse, TownRules::load),
                DataTableDefinitions.schematics(registry));
    }
}
