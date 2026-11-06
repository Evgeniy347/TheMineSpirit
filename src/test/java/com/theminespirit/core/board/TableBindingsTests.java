package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertSame;

import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.intellect.IntellectTuning;
import com.theminespirit.core.intellect.InventoryNormTable;
import com.theminespirit.core.intellect.OutsideRules;
import com.theminespirit.core.intellect.defense.DefenseProfile;
import com.theminespirit.core.processing.ProcessingRules;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.repair.RepairData;
import com.theminespirit.core.tactics.TacticsConfig;
import com.theminespirit.core.tactics.adapter.TacticsAdapterSettings;
import com.theminespirit.core.town.TownRules;
import org.junit.jupiter.api.Test;

class TableBindingsTests {
    @Test
    void reloadBoundTables_OwnerCurrent_ReturnsSameValue() {
        DataTables tables = DataTables.INSTANCE;

        assertSame(tables.board().current(), BoardConfig.current(), "board.json читается у владельца, не из reload");
        assertSame(tables.stages().current(), StageRules.current(), "stages.json читается у владельца");
        assertSame(tables.demand().current(), DemandRules.current(), "settlement_demand.json читается у владельца");
        assertSame(tables.leveling().current(), LevelingRules.current(), "leveling.json читается у владельца");
        assertSame(tables.graph().current(), SupplyGraph.current(), "graph.json читается у владельца");
        assertSame(tables.presets().current(), PresetLibrary.current(), "presets.json читается у владельца");
        assertSame(tables.town().current(), TownRules.current(), "town.json читается у владельца");
        assertSame(tables.repair().current(), RepairData.current(), "repair.json читается у владельца");
        assertSame(tables.tactics().current(), TacticsConfig.loadDefault(), "конфиг тактики читается у владельца");
        assertSame(
                tables.adapter().current(), TacticsAdapterSettings.loadDefault(), "adapter.json читается у владельца");
        assertSame(tables.intellect().current(), IntellectTuning.loadDefault(), "intellect.json читается у владельца");
        assertSame(
                tables.inventoryNorm().current(),
                InventoryNormTable.loadDefault(),
                "inventory_norm.json читается у владельца");
        assertSame(tables.outside().current(), OutsideRules.loadDefault(), "outside.json читается у владельца");
        assertSame(tables.priorities().current(), DefenseProfile.loadDefault(), "priorities.json читается у владельца");
        assertSame(
                tables.processing().current(), ProcessingRules.loadDefault(), "processing.json читается у владельца");
    }
}
