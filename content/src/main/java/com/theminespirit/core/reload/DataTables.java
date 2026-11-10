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
import com.theminespirit.core.processing.ProcessingRules;
import com.theminespirit.core.repair.RepairData;
import com.theminespirit.core.tactics.TacticsConfig;
import com.theminespirit.core.tactics.adapter.TacticsAdapterSettings;
import com.theminespirit.core.town.TownRules;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public final class DataTables {
    public static final DataTables INSTANCE = new DataTables(Clock.systemUTC());

    public static final String TACTICS_ROOT = "data/theminespirit/tactics/";

    private final TableRegistry registry;
    private final AtomicInteger armorSeeds = new AtomicInteger();
    private final TacticsTables tacticsTables;
    private final MovementTables movement;
    private final InventoryTables inventory;
    private final WorldTables worldTables;
    private final BoardTables boardTables;

    public DataTables(Clock clock) {
        this.registry = new TableRegistry(clock);
        this.tacticsTables = TacticsTables.register(registry);
        this.movement = MovementTables.register(registry);
        this.inventory = InventoryTables.register(registry, armorSeeds);
        this.worldTables = WorldTables.register(registry);
        this.boardTables = BoardTables.register(registry);
        DataTableBindings.bind(this);
    }

    public TableRegistry registry() {
        return registry;
    }

    public int armorSeeds() {
        return armorSeeds.get();
    }

    MovementTables movement() {
        return movement;
    }

    public TableHolder<TacticsConfig> tactics() {
        return tacticsTables.tactics();
    }

    public TableHolder<TacticsAdapterSettings> adapter() {
        return tacticsTables.adapter();
    }

    public TableHolder<ValueTable> values() {
        return inventory.values();
    }

    public TableHolder<WarehouseCategories> warehouseCategories() {
        return inventory.warehouseCategories();
    }

    public TableHolder<BaseKit> kit() {
        return inventory.kit();
    }

    public TableHolder<KitFor> kitFor() {
        return inventory.kitFor();
    }

    public TableHolder<InventoryNormTable> inventoryNorm() {
        return inventory.inventoryNorm();
    }

    public TableHolder<IntellectTuning> intellect() {
        return tacticsTables.intellect();
    }

    public TableHolder<OverflowTuning> overflow() {
        return inventory.overflow();
    }

    public TableHolder<OutsideRules> outside() {
        return inventory.outside();
    }

    public TableHolder<ArmorTable> armor() {
        return inventory.armor();
    }

    public TableHolder<FeedTable> feed() {
        return worldTables.feed();
    }

    public TableHolder<Map<String, SwordTier>> swords() {
        return worldTables.swords();
    }

    public TableHolder<DropTable> drops() {
        return inventory.drops();
    }

    public TableHolder<ToolReserve> toolReplace() {
        return inventory.toolReplace();
    }

    public TableHolder<BlockProtection> protection() {
        return inventory.protection();
    }

    public TableHolder<PresetLibrary> presets() {
        return boardTables.presets();
    }

    public TableHolder<BoardConfig> board() {
        return boardTables.board();
    }

    public TableHolder<SupplyGraph> graph() {
        return boardTables.graph();
    }

    public TableHolder<BuildData> building() {
        return boardTables.building();
    }

    public TableHolder<MagnetRules> magnet() {
        return worldTables.magnet();
    }

    public TableHolder<LevelingRules> leveling() {
        return boardTables.leveling();
    }

    public TableHolder<TownRules> town() {
        return boardTables.town();
    }

    public TableHolder<FishingWait> fishingWait() {
        return worldTables.fishingWait();
    }

    public TableHolder<StageRules> stages() {
        return boardTables.stages();
    }

    public TableHolder<DemandRules> demand() {
        return boardTables.demand();
    }

    public TableHolder<WoodRules> wood() {
        return boardTables.wood();
    }

    public TableHolder<PersonalSpace> personalSpace() {
        return movement.personalSpace();
    }

    public TableHolder<DoorRules> doors() {
        return worldTables.doors();
    }

    public TableHolder<HungerRules> hunger() {
        return worldTables.hunger();
    }

    public TableHolder<RespawnRules> respawn() {
        return worldTables.respawn();
    }

    public TableHolder<FishingData> fishing() {
        return worldTables.fishing();
    }

    public TableHolder<ReplantData> replant() {
        return worldTables.replant();
    }

    public TableHolder<DescentData> descent() {
        return worldTables.descent();
    }

    public TableHolder<DescentLevels> descentLevels() {
        return worldTables.descentLevels();
    }

    public TableHolder<DefenseProfile> priorities() {
        return tacticsTables.priorities();
    }

    public TableHolder<RepairData> repair() {
        return worldTables.repair();
    }

    public TableHolder<ProcessingRules> processing() {
        return worldTables.processing();
    }

    public TableHolder<ObservationLimits> observation() {
        return movement.observation();
    }

    public Optional<TableHolder<Schematic>> schematic(String file) {
        return Optional.ofNullable(boardTables.schematics().get(file));
    }

    public List<TableRegistry.Report> reload(ContentSource source) {
        return registry.reload(source);
    }
}
