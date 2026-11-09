package com.theminespirit.core.reload;

import com.theminespirit.core.armor.ArmorTable;
import com.theminespirit.core.base.RespawnRules;
import com.theminespirit.core.board.BoardConfig;
import com.theminespirit.core.board.DemandRules;
import com.theminespirit.core.board.LevelingRules;
import com.theminespirit.core.board.StageRules;
import com.theminespirit.core.board.WoodRules;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.building.BuildData;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.doors.DoorTuning;
import com.theminespirit.core.doors.DoorTuning.DoorRules;
import com.theminespirit.core.fishing.FishingData;
import com.theminespirit.core.fishing.FishingWait;
import com.theminespirit.core.forestry.ReplantData;
import com.theminespirit.core.hunger.HungerRules;
import com.theminespirit.core.husbandry.FeedTable;
import com.theminespirit.core.husbandry.SwordTier;
import com.theminespirit.core.intellect.IntellectTuning;
import com.theminespirit.core.intellect.InventoryNormTable;
import com.theminespirit.core.intellect.OutsideRules;
import com.theminespirit.core.intellect.defense.DefenseProfile;
import com.theminespirit.core.intellect.defense.DefenseProfileParser;
import com.theminespirit.core.inventory.BaseKit;
import com.theminespirit.core.inventory.KitFor;
import com.theminespirit.core.inventory.OverflowTuning;
import com.theminespirit.core.inventory.ValueTable;
import com.theminespirit.core.inventory.WarehouseCategories;
import com.theminespirit.core.mining.DescentData;
import com.theminespirit.core.mining.DescentLevels;
import com.theminespirit.core.mining.DropTable;
import com.theminespirit.core.mining.ToolReserve;
import com.theminespirit.core.movement.PersonalSpace;
import com.theminespirit.core.observation.ObservationLimits;
import com.theminespirit.core.pickup.MagnetRules;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.policy.ProtectionConfig;
import com.theminespirit.core.processing.ProcessingRules;
import com.theminespirit.core.repair.RepairData;
import com.theminespirit.core.route.RouteRules;
import com.theminespirit.core.tactics.TacticsConfig;
import com.theminespirit.core.tactics.adapter.TacticsAdapterSettings;
import com.theminespirit.core.town.TownRules;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class DataTables {
    public static final DataTables INSTANCE = new DataTables(Clock.systemUTC());

    public static final String TACTICS_ROOT = "data/theminespirit/tactics/";
    private final TableRegistry registry;
    private final AtomicInteger armorSeeds = new AtomicInteger();
    private final TableHolder<TacticsConfig> tactics;
    private final TableHolder<TacticsAdapterSettings> adapter;
    private final TableHolder<ValueTable> values;
    private final TableHolder<WarehouseCategories> warehouseCategories;
    private final TableHolder<BaseKit> kit;
    private final TableHolder<KitFor> kitFor;
    private final TableHolder<IntellectTuning> intellect;
    private final TableHolder<InventoryNormTable> inventoryNorm;
    private final TableHolder<OverflowTuning> overflow;
    private final TableHolder<OutsideRules> outside;
    private final TableHolder<ArmorTable> armor;
    private final TableHolder<FeedTable> feed;
    private final TableHolder<Map<String, SwordTier>> swords;
    private final TableHolder<DropTable> drops;
    private final TableHolder<ToolReserve> toolReplace;
    private final TableHolder<BlockProtection> protection;
    private final TableHolder<PresetLibrary> presets;
    private final TableHolder<BoardConfig> board;
    private final TableHolder<SupplyGraph> graph;
    private final TableHolder<DoorRules> doors;
    private final TableHolder<BuildData> building;
    private final TableHolder<MagnetRules> magnet;
    private final TableHolder<FishingWait> fishingWait;
    private final TableHolder<RouteRules> route;
    private final TableHolder<StageRules> stages;
    private final TableHolder<DemandRules> demand;
    private final TableHolder<WoodRules> wood;
    private final TableHolder<LevelingRules> leveling;
    private final TableHolder<TownRules> town;
    private final TableHolder<PersonalSpace> personalSpace;
    private final TableHolder<HungerRules> hunger;
    private final TableHolder<RespawnRules> respawn;
    private final TableHolder<FishingData> fishing;
    private final TableHolder<ReplantData> replant;
    private final TableHolder<DescentData> descent;
    private final TableHolder<DescentLevels> descentLevels;
    private final TableHolder<DefenseProfile> priorities;
    private final TableHolder<RepairData> repair;
    private final TableHolder<ObservationLimits> observation;
    private final Map<String, TableHolder<Schematic>> schematics;
    private final TableHolder<ProcessingRules> processing;

    public DataTables(Clock clock) {
        this.registry = new TableRegistry(clock);
        this.tactics = registry.registerLazy(DataTableDefinitions.tactics(), TacticsConfig::readFromClasspath);
        this.adapter = single(
                "adapter",
                TACTICS_ROOT + "adapter.json",
                (json, origin) -> TacticsAdapterSettings.fromJson(json),
                TacticsAdapterSettings::readFromClasspath);
        this.values = single(
                "item_values", "data/theminespirit/inventory/item_values.json", ValueTable::parse, ValueTable::load);
        this.warehouseCategories = single(
                "warehouse_categories",
                WarehouseCategories.RESOURCE,
                WarehouseCategories::parse,
                WarehouseCategories::load);
        this.kit = single("base_kit", "data/theminespirit/inventory/base_kit.json", BaseKit::parse, BaseKit::load);
        this.kitFor = single(
                "kit_activities",
                "data/theminespirit/inventory/kit_activities.json",
                KitFor::parse,
                KitFor::readClasspath);
        this.inventoryNorm = single(
                "inventory_norm",
                InventoryNormTable.RESOURCE,
                InventoryNormTable::parse,
                InventoryNormTable::readClasspath);
        this.intellect =
                single("intellect", IntellectTuning.RESOURCE, IntellectTuning::parse, IntellectTuning::defaults);
        this.overflow = single("overflow", OverflowTuning.RESOURCE, OverflowTuning::parse, OverflowTuning::load);
        this.outside = single("outside", OutsideRules.RESOURCE, OutsideRules::parse, OutsideRules::load);
        this.armor = single("armor", "data/theminespirit/armor/armor_table.json", ArmorTable::parse, () -> {
            armorSeeds.incrementAndGet();
            return ArmorTable.load();
        });
        this.feed = single("feed", "data/theminespirit/husbandry/feed.json", FeedTable::loadJson, FeedTable::loadFile);
        this.swords = single("sword", "data/theminespirit/husbandry/sword.json", SwordTier::parse, SwordTier::loadFile);
        this.drops = single("drops", "data/theminespirit/acquire/drops.json", DropTable::parse, DropTable::load);
        this.toolReplace = single("tool_replace", ToolReserve.RESOURCE, ToolReserve::parse, ToolReserve::load);
        this.protection = single(
                "protection",
                "data/theminespirit/policy/protection.json",
                ProtectionConfig::parse,
                ProtectionConfig::readClasspath);
        this.presets = registry.registerLazy(DataTableDefinitions.presets(), PresetLibrary::loadDefault);
        this.board = single("board", BoardConfig.RESOURCE, BoardConfig::fromJson, BoardConfig::readFromClasspath);
        this.graph = DataTableDefinitions.supply(registry);
        this.building = single("building", BuildData.RESOURCE, BuildData::parse, BuildData::load);
        this.magnet = single("magnet", MagnetRules.RESOURCE, MagnetRules::parse, MagnetRules::load);
        this.fishingWait = single("fishing_wait", FishingWait.RESOURCE, FishingWait::parse, FishingWait::load);
        this.route = single("route", RouteRules.RESOURCE, RouteRules::parse, RouteRules::load);
        this.stages = single("stages", StageRules.RESOURCE, StageRules::parse, StageRules::load);
        this.demand = single("settlement_demand", DemandRules.RESOURCE, DemandRules::parse, DemandRules::load);
        this.wood = single("wood", WoodRules.RESOURCE, WoodRules::parse, WoodRules::load);
        this.leveling = single("leveling", LevelingRules.RESOURCE, LevelingRules::parse, LevelingRules::load);
        this.town = single("town", TownRules.RESOURCE, TownRules::parse, TownRules::load);
        this.personalSpace =
                single("personal_space", PersonalSpace.RESOURCE, PersonalSpace::parse, PersonalSpace::load);
        this.doors = single("doors", "data/theminespirit/doors/doors.json", DoorTuning::parse, DoorTuning::loadDefault);
        this.hunger = single("hunger", HungerRules.RESOURCE, HungerRules::parse, HungerRules::load);
        this.respawn = single("respawn", RespawnRules.RESOURCE, RespawnRules::parse, RespawnRules::load);
        this.fishing = single("fishing", FishingData.RESOURCE, FishingData::parse, FishingData::load);
        this.replant = single("replant", ReplantData.RESOURCE, ReplantData::parse, ReplantData::load);
        this.descent = single("descent", DescentData.RESOURCE, DescentData::parse, DescentData::load);
        this.descentLevels =
                single("descent_levels", DescentLevels.RESOURCE, DescentLevels::parse, DescentLevels::load);
        this.priorities = single(
                "priorities",
                DefenseProfile.RESOURCE,
                (json, origin) -> DefenseProfileParser.fromJson(json),
                DefenseProfile::readFromClasspath);
        this.repair = single("repair", RepairData.RESOURCE, RepairData::parse, RepairData::load);
        this.observation =
                single("observation", ObservationLimits.RESOURCE, ObservationLimits::parse, ObservationLimits::load);
        this.schematics = DataTableDefinitions.schematics(registry);
        this.processing = single("processing", ProcessingRules.RESOURCE, ProcessingRules::parse, ProcessingRules::load);
        DataTableBindings.bind(this);
    }

    private <T> TableHolder<T> single(
            String id, String resourcePath, TableDefinition.SingleParser<T> parser, Supplier<T> seed) {
        return registry.registerLazy(TableDefinition.single(id, resourcePath, parser), seed);
    }

    public TableRegistry registry() {
        return registry;
    }

    public int armorSeeds() {
        return armorSeeds.get();
    }

    public TableHolder<TacticsConfig> tactics() {
        return tactics;
    }

    public TableHolder<TacticsAdapterSettings> adapter() {
        return adapter;
    }

    public TableHolder<ValueTable> values() {
        return values;
    }

    public TableHolder<WarehouseCategories> warehouseCategories() {
        return warehouseCategories;
    }

    public TableHolder<BaseKit> kit() {
        return kit;
    }

    public TableHolder<KitFor> kitFor() {
        return kitFor;
    }

    public TableHolder<InventoryNormTable> inventoryNorm() {
        return inventoryNorm;
    }

    public TableHolder<IntellectTuning> intellect() {
        return intellect;
    }

    public TableHolder<OverflowTuning> overflow() {
        return overflow;
    }

    public TableHolder<OutsideRules> outside() {
        return outside;
    }

    public TableHolder<ArmorTable> armor() {
        return armor;
    }

    public TableHolder<FeedTable> feed() {
        return feed;
    }

    public TableHolder<Map<String, SwordTier>> swords() {
        return swords;
    }

    public TableHolder<DropTable> drops() {
        return drops;
    }

    public TableHolder<ToolReserve> toolReplace() {
        return toolReplace;
    }

    public TableHolder<BlockProtection> protection() {
        return protection;
    }

    public TableHolder<PresetLibrary> presets() {
        return presets;
    }

    public TableHolder<BoardConfig> board() {
        return board;
    }

    public TableHolder<SupplyGraph> graph() {
        return graph;
    }

    public TableHolder<BuildData> building() {
        return building;
    }

    public TableHolder<MagnetRules> magnet() {
        return magnet;
    }

    public TableHolder<LevelingRules> leveling() {
        return leveling;
    }

    public TableHolder<TownRules> town() {
        return town;
    }

    public TableHolder<FishingWait> fishingWait() {
        return fishingWait;
    }

    public TableHolder<RouteRules> route() {
        return route;
    }

    public TableHolder<StageRules> stages() {
        return stages;
    }

    public TableHolder<DemandRules> demand() {
        return demand;
    }

    public TableHolder<WoodRules> wood() {
        return wood;
    }

    public TableHolder<PersonalSpace> personalSpace() {
        return personalSpace;
    }

    public TableHolder<DoorRules> doors() {
        return doors;
    }

    public TableHolder<HungerRules> hunger() {
        return hunger;
    }

    public TableHolder<RespawnRules> respawn() {
        return respawn;
    }

    public TableHolder<FishingData> fishing() {
        return fishing;
    }

    public TableHolder<ReplantData> replant() {
        return replant;
    }

    public TableHolder<DescentData> descent() {
        return descent;
    }

    public TableHolder<DescentLevels> descentLevels() {
        return descentLevels;
    }

    public TableHolder<DefenseProfile> priorities() {
        return priorities;
    }

    public TableHolder<RepairData> repair() {
        return repair;
    }

    public TableHolder<ProcessingRules> processing() {
        return processing;
    }

    public TableHolder<ObservationLimits> observation() {
        return observation;
    }

    public Optional<TableHolder<Schematic>> schematic(String file) {
        return Optional.ofNullable(schematics.get(file));
    }

    public List<TableRegistry.Report> reload(ContentSource source) {
        return registry.reload(source);
    }
}
