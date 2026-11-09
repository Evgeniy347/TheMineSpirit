package com.theminespirit.core.reload;

import com.theminespirit.core.base.RespawnRules;
import com.theminespirit.core.board.BoardConfig;
import com.theminespirit.core.board.ConfigStamp;
import com.theminespirit.core.board.DemandRules;
import com.theminespirit.core.board.LevelingRules;
import com.theminespirit.core.board.StageRules;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.board.graph.VitalNeeds;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.SpiritReach;
import com.theminespirit.core.hunger.HungerRules;
import com.theminespirit.core.husbandry.FeedTable;
import com.theminespirit.core.intellect.IntellectTuning;
import com.theminespirit.core.intellect.InventoryNormTable;
import com.theminespirit.core.intellect.OutsideRules;
import com.theminespirit.core.intellect.defense.DefenseProfile;
import com.theminespirit.core.inventory.KitFor;
import com.theminespirit.core.inventory.WarehouseCategories;
import com.theminespirit.core.mining.DescentData;
import com.theminespirit.core.mining.DescentLevels;
import com.theminespirit.core.mining.DropTable;
import com.theminespirit.core.observation.ObservationLimits;
import com.theminespirit.core.processing.ProcessingRules;
import com.theminespirit.core.repair.RepairData;
import com.theminespirit.core.tactics.TacticsConfig;
import com.theminespirit.core.tactics.adapter.TacticsAdapterSettings;
import com.theminespirit.core.town.TownRules;
import java.util.List;

final class DataTableBindings {
    private DataTableBindings() {}

    static void bind(DataTables tables) {
        ConfigStamp.useCurrent(() -> tables.board().status().hash());
        ObservationLimits.useSource(() -> tables.observation().current());
        FeedTable.useSource(() -> tables.feed().current());
        RespawnRules.useSource(() -> tables.respawn().current());
        BoardConfig.useSource(() -> tables.board().current());
        StageRules.useSource(() -> tables.stages().current());
        DemandRules.useSource(() -> tables.demand().current());
        LevelingRules.useSource(() -> tables.leveling().current());
        SupplyGraph.useSource(() -> tables.graph().current());
        @SuppressWarnings("unchecked")
        TableHolder<List<VitalNeeds.Need>> vital = (TableHolder<List<VitalNeeds.Need>>) tables.registry()
                .holder("vital")
                .orElseThrow(() ->
                        new IllegalStateException("Таблица vital не зарегистрирована: гейт нужд читал бы старый файл"));
        VitalNeeds.useSource(vital::current);
        PresetLibrary.useSource(() -> tables.presets().current());
        SpiritReach.useSource(() -> tables.building().current().reach());
        TownRules.useSource(() -> tables.town().current());
        RepairData.useSource(() -> tables.repair().current());
        HungerRules.useSource(() -> tables.hunger().current());
        DescentData.useSource(() -> tables.descent().current());
        DescentLevels.useSource(() -> tables.descentLevels().current());
        DropTable.useSource(() -> tables.drops().current());
        WarehouseCategories.useSource(() -> tables.warehouseCategories().current());
        KitFor.useSource(() -> tables.kitFor().current());
        TacticsConfig.useSource(() -> tables.tactics().current());
        TacticsAdapterSettings.useSource(() -> tables.adapter().current());
        IntellectTuning.useSource(() -> tables.intellect().current());
        InventoryNormTable.useSource(() -> tables.inventoryNorm().current());
        OutsideRules.useSource(() -> tables.outside().current());
        DefenseProfile.useSource(() -> tables.priorities().current());
        ProcessingRules.useSource(() -> tables.processing().current());
    }
}
