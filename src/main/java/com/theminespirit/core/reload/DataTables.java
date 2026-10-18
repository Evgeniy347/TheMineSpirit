package com.theminespirit.core.reload;

import com.theminespirit.core.armor.ArmorTable;
import com.theminespirit.core.board.BoardConfig;
import com.theminespirit.core.building.BuildData;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicTextParser;
import com.theminespirit.core.doors.DoorTuning.DoorRules;
import com.theminespirit.core.doors.DoorTuning;
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
import com.theminespirit.core.inventory.JsonResource;
import com.theminespirit.core.inventory.KitFor;
import com.theminespirit.core.inventory.OverflowTuning;
import com.theminespirit.core.inventory.ValueTable;
import com.theminespirit.core.inventory.WarehouseCategories;
import com.theminespirit.core.mining.DescentData;
import com.theminespirit.core.mining.DescentLevels;
import com.theminespirit.core.mining.DropTable;
import com.theminespirit.core.mining.ToolReserve;
import com.theminespirit.core.movement.Unstuck;
import com.theminespirit.core.observation.ObservationLimits;
import com.theminespirit.core.pickup.MagnetRules;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.policy.ProtectionConfig;
import com.theminespirit.core.processing.ProcessingRules;
import com.theminespirit.core.repair.RepairData;
import com.theminespirit.core.tactics.TacticsConfig;
import com.theminespirit.core.tactics.adapter.TacticsAdapterSettings;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Единственный держатель данных мода: таблицы ценностей, коэффициентов и правил. Читатели (core и neoforge)
 * берут текущие версии отсюда, а не читают classpath сами. NeoForge-адаптер на {@code AddReloadListenerEvent}
 * вызывает {@link #reload(ContentSource)} — так ванильная {@code /reload} и датапак мира переопределяют любой файл.
 *
 * <p>Таблицы, которых нет в ресурс-менеджере (нет ни в моде, ни в датапаке), не перезагружаются и продолжают
 * работать в classpath-версии: отсутствие файла — не ошибка, ошибкой считается только нечитаемый/битый json.
 */
public final class DataTables {
    /** Схемы, читаемые по имени потребителем: тела перечитываются на /reload вместе с остальными таблицами. */
    private static final List<String> LIVE_SCHEMATIC_FILES = List.of("fixture_plate", "fence", "storage_v1",
            "storage_v2", "storage_v3", "storage_v4", "reach_wall", "state_demo");

    public static final DataTables INSTANCE = new DataTables(Clock.systemUTC());

    public static final String TACTICS_ROOT = "data/theminespirit/tactics/";

    private final TableRegistry registry;
    private final AtomicInteger valueSeeds = new AtomicInteger();
    private final AtomicInteger kitSeeds = new AtomicInteger();
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
    private final TableHolder<DoorRules> doors;
    private final TableHolder<BuildData> building;
    private final TableHolder<MagnetRules> magnet;
    private final TableHolder<FishingWait> fishingWait;
    private final TableHolder<Unstuck> unstuck;
    private final TableHolder<HungerRules> hunger;
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
        this.tactics = registry.registerLazy(TableDefinition.composite("tactics",
                List.of(TACTICS_ROOT + "enemies.json", TACTICS_ROOT + "weights.json", TACTICS_ROOT + "rules.json",
                        TACTICS_ROOT + "modes.json"),
                contents -> TacticsConfig.fromJson(contents.get(0).json(), contents.get(1).json(),
                        contents.get(2).json(), contents.get(3).json())),
                TacticsConfig::readFromClasspath);
        this.adapter = registry.registerLazy(
                TableDefinition.single("adapter", TACTICS_ROOT + "adapter.json",
                        (json, origin) -> TacticsAdapterSettings.fromJson(json)),
                TacticsAdapterSettings::readFromClasspath);
        this.values = registry.registerLazy(
                TableDefinition.single("item_values", "data/theminespirit/inventory/item_values.json",
                        ValueTable::parse),
                () -> {
                    valueSeeds.incrementAndGet();
                    return ValueTable.load();
                });
        this.warehouseCategories = registry.registerLazy(
                TableDefinition.single("warehouse_categories", WarehouseCategories.RESOURCE,
                        WarehouseCategories::parse),
                WarehouseCategories::load);
        this.kit = registry.registerLazy(
                TableDefinition.single("base_kit", "data/theminespirit/inventory/base_kit.json", BaseKit::parse),
                () -> {
                    kitSeeds.incrementAndGet();
                    return BaseKit.load();
                });
        this.kitFor = registry.registerLazy(
                TableDefinition.single("kit_activities", "data/theminespirit/inventory/kit_activities.json",
                        KitFor::parse),
                KitFor::readClasspath);
        this.inventoryNorm = registry.registerLazy(
                TableDefinition.single("inventory_norm", InventoryNormTable.RESOURCE, InventoryNormTable::parse),
                InventoryNormTable::readClasspath);
        this.intellect = registry.registerLazy(
                TableDefinition.single("intellect", IntellectTuning.RESOURCE, IntellectTuning::parse),
                IntellectTuning::defaults);
        this.overflow = registry.registerLazy(
                TableDefinition.single("overflow", OverflowTuning.RESOURCE, OverflowTuning::parse),
                OverflowTuning::load);
        this.outside = registry.registerLazy(
                TableDefinition.single("outside", OutsideRules.RESOURCE, OutsideRules::parse),
                OutsideRules::load);
        this.armor = registry.registerLazy(
                TableDefinition.single("armor", "data/theminespirit/armor/armor_table.json", ArmorTable::parse),
                () -> {
                    armorSeeds.incrementAndGet();
                    return ArmorTable.load();
                });
        this.feed = registry.registerLazy(
                TableDefinition.single("feed", "data/theminespirit/husbandry/feed.json", FeedTable::loadJson),
                FeedTable::loadFile);
        this.swords = registry.registerLazy(
                TableDefinition.single("sword", "data/theminespirit/husbandry/sword.json", SwordTier::parse),
                SwordTier::loadFile);
        this.drops = registry.registerLazy(
                TableDefinition.single("drops", "data/theminespirit/acquire/drops.json", DropTable::parse),
                DropTable::load);
        this.toolReplace = registry.registerLazy(
                TableDefinition.single("tool_replace", ToolReserve.RESOURCE, ToolReserve::parse),
                ToolReserve::load);
        this.protection = registry.registerLazy(
                TableDefinition.single("protection", "data/theminespirit/policy/protection.json",
                        ProtectionConfig::parse),
                ProtectionConfig::readClasspath);
        this.presets = registry.registerLazy(
                TableDefinition.indexed(PresetLibrary.INDEX_ID,
                        PresetLibrary.SCHEMATICS_ROOT + PresetLibrary.INDEX_ID + ".json",
                        index -> PresetLibrary.bodyPaths(index.json(), index.origin()),
                        PresetLibrary::parse),
                () -> PresetLibrary.parse(loadPresetBodies()));
        this.board = registry.registerLazy(
                TableDefinition.single("board", BoardConfig.RESOURCE, BoardConfig::fromJson),
                BoardConfig::readFromClasspath);
        this.building = registry.registerLazy(
                TableDefinition.single("building", BuildData.RESOURCE, BuildData::parse), BuildData::load);
        this.magnet = registry.registerLazy(
                TableDefinition.single("magnet", MagnetRules.RESOURCE, MagnetRules::parse), MagnetRules::load);
        this.fishingWait = registry.registerLazy(
                TableDefinition.single("fishing_wait", FishingWait.RESOURCE, FishingWait::parse), FishingWait::load);
        this.unstuck = registry.registerLazy(
                TableDefinition.single("unstuck", Unstuck.RESOURCE, Unstuck::parse), Unstuck::load);
        this.doors = registry.registerLazy(
                TableDefinition.single("doors", "data/theminespirit/doors/doors.json", DoorTuning::parse),
                DoorTuning::loadDefault);
        this.hunger = registry.registerLazy(
                TableDefinition.single("hunger", HungerRules.RESOURCE, HungerRules::parse),
                HungerRules::load);
        this.fishing = registry.registerLazy(
                TableDefinition.single("fishing", FishingData.RESOURCE, FishingData::parse),
                FishingData::load);
        this.replant = registry.registerLazy(
                TableDefinition.single("replant", ReplantData.RESOURCE, ReplantData::parse),
                ReplantData::load);
        this.descent = registry.registerLazy(
                TableDefinition.single("descent", DescentData.RESOURCE, DescentData::parse),
                DescentData::load);
        this.descentLevels = registry.registerLazy(
                TableDefinition.single("descent_levels", DescentLevels.RESOURCE, DescentLevels::parse),
                DescentLevels::load);
        this.priorities = registry.registerLazy(
                TableDefinition.single("priorities", DefenseProfile.RESOURCE,
                        (json, origin) -> DefenseProfileParser.fromJson(json)),
                DefenseProfile::readFromClasspath);
        this.repair = registry.registerLazy(
                TableDefinition.single("repair", RepairData.RESOURCE, RepairData::parse),
                RepairData::load);
        this.observation = registry.registerLazy(
                TableDefinition.single("observation", ObservationLimits.RESOURCE, ObservationLimits::parse),
                ObservationLimits::load);
        Map<String, TableHolder<Schematic>> liveSchematics = new LinkedHashMap<>();
        for (String file : LIVE_SCHEMATIC_FILES) {
            String path = PresetLibrary.SCHEMATICS_ROOT + file + SchematicTextParser.EXTENSION;
            liveSchematics.put(file, registry.registerLazy(
                    TableDefinition.single("schematic_" + file, path,
                            (text, origin) -> SchematicTextParser.parse(text, origin).schematic()),
                    () -> SchematicTextParser.parse(JsonResource.readClasspath(path), path).schematic()));
        }
        this.schematics = Map.copyOf(liveSchematics);
        this.processing = registry.registerLazy(
                TableDefinition.single("processing", ProcessingRules.RESOURCE,
                        (json, origin) -> ProcessingRules.parse(json, origin)),
                ProcessingRules::load);
    }

    private static List<LoadedContent> loadPresetBodies() {
        String indexPath = PresetLibrary.SCHEMATICS_ROOT + PresetLibrary.INDEX_ID + ".json";
        String indexJson = JsonResource.readClasspath(indexPath);
        List<LoadedContent> contents = new ArrayList<>();
        contents.add(new LoadedContent(indexJson, indexPath + " (мод)", TableStatus.Source.MOD));
        for (String path : PresetLibrary.bodyPaths(indexJson, indexPath)) {
            contents.add(new LoadedContent(JsonResource.readClasspath(path), path + " (мод)", TableStatus.Source.MOD));
        }
        return List.copyOf(contents);
    }

    public TableRegistry registry() {
        return registry;
    }

    public int valueSeeds() {
        return valueSeeds.get();
    }

    public int kitSeeds() {
        return kitSeeds.get();
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

    public TableHolder<BuildData> building() {
        return building;
    }

    public TableHolder<MagnetRules> magnet() {
        return magnet;
    }

    public TableHolder<FishingWait> fishingWait() {
        return fishingWait;
    }

    public TableHolder<Unstuck> unstuck() {
        return unstuck;
    }

    public TableHolder<DoorRules> doors() {
        return doors;
    }

    public TableHolder<HungerRules> hunger() {
        return hunger;
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

    /**
     * Держатель схемы по имени файла, если схема зарегистрирована как таблица. Потребитель берёт
     * {@link TableHolder#current()}, поэтому /reload меняет тело схемы на лету. Схемы без регистрации
     * (тела пресетов) держателя не имеют — вызывающий читает их из classpath сам.
     */
    public Optional<TableHolder<Schematic>> schematic(String file) {
        return Optional.ofNullable(schematics.get(file));
    }

    /** Перезагружает все таблицы из источника. Возвращает отчёт по каждой таблице: обновлена или ошибка. */
    public List<TableRegistry.Report> reload(ContentSource source) {
        return registry.reload(source);
    }
}