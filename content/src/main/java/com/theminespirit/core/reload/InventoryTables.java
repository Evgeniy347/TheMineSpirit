package com.theminespirit.core.reload;

import com.theminespirit.core.armor.ArmorTable;
import com.theminespirit.core.intellect.InventoryNormTable;
import com.theminespirit.core.intellect.OutsideRules;
import com.theminespirit.core.inventory.BaseKit;
import com.theminespirit.core.inventory.KitFor;
import com.theminespirit.core.inventory.OverflowTuning;
import com.theminespirit.core.inventory.ValueTable;
import com.theminespirit.core.inventory.WarehouseCategories;
import com.theminespirit.core.mining.DropTable;
import com.theminespirit.core.mining.ToolReserve;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.policy.ProtectionConfig;
import java.util.concurrent.atomic.AtomicInteger;

record InventoryTables(
        TableHolder<ValueTable> values,
        TableHolder<WarehouseCategories> warehouseCategories,
        TableHolder<BaseKit> kit,
        TableHolder<KitFor> kitFor,
        TableHolder<InventoryNormTable> inventoryNorm,
        TableHolder<OverflowTuning> overflow,
        TableHolder<OutsideRules> outside,
        TableHolder<ArmorTable> armor,
        TableHolder<DropTable> drops,
        TableHolder<ToolReserve> toolReplace,
        TableHolder<BlockProtection> protection) {
    static InventoryTables register(TableRegistry registry, AtomicInteger armorSeeds) {
        return new InventoryTables(
                DataTableDefinitions.single(
                        registry,
                        "item_values",
                        "data/theminespirit/inventory/item_values.json",
                        ValueTable::parse,
                        ValueTable::load),
                DataTableDefinitions.single(
                        registry,
                        "warehouse_categories",
                        WarehouseCategories.RESOURCE,
                        WarehouseCategories::parse,
                        WarehouseCategories::load),
                DataTableDefinitions.single(
                        registry,
                        "base_kit",
                        "data/theminespirit/inventory/base_kit.json",
                        BaseKit::parse,
                        BaseKit::load),
                DataTableDefinitions.single(
                        registry,
                        "kit_activities",
                        "data/theminespirit/inventory/kit_activities.json",
                        KitFor::parse,
                        KitFor::readClasspath),
                DataTableDefinitions.single(
                        registry,
                        "inventory_norm",
                        InventoryNormTable.RESOURCE,
                        InventoryNormTable::parse,
                        InventoryNormTable::readClasspath),
                DataTableDefinitions.single(
                        registry, "overflow", OverflowTuning.RESOURCE, OverflowTuning::parse, OverflowTuning::load),
                DataTableDefinitions.single(
                        registry, "outside", OutsideRules.RESOURCE, OutsideRules::parse, OutsideRules::load),
                DataTableDefinitions.single(
                        registry, "armor", "data/theminespirit/armor/armor_table.json", ArmorTable::parse, () -> {
                            armorSeeds.incrementAndGet();
                            return ArmorTable.load();
                        }),
                DataTableDefinitions.single(
                        registry, "drops", "data/theminespirit/acquire/drops.json", DropTable::parse, DropTable::load),
                DataTableDefinitions.single(
                        registry, "tool_replace", ToolReserve.RESOURCE, ToolReserve::parse, ToolReserve::load),
                DataTableDefinitions.single(
                        registry,
                        "protection",
                        "data/theminespirit/policy/protection.json",
                        ProtectionConfig::parse,
                        ProtectionConfig::readClasspath));
    }
}
