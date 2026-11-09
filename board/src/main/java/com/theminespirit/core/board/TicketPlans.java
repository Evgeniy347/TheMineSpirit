package com.theminespirit.core.board;

import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicActions;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.building.SchematicPart;
import com.theminespirit.core.building.WoodSpecies;
import com.theminespirit.core.husbandry.FeedTable;
import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.mining.OreKind;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.BuildSchematicParams;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.town.PlotLeveling;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageSites;
import com.theminespirit.core.town.StageWork;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public final class TicketPlans {
    public static final Set<BoardCase> EXTERNAL_CASES = Set.of(BoardCase.EAT_TO_FULL);
    public static final String MINE_DESCENT_SCHEME = StageSites.MINE_DESCENT_SCHEME;
    public static final int FENCE_PARTS = 4;
    public static final String MINE_DESCENT_ROTATION = StageSites.MINE_DESCENT_ROTATION;
    public static final int MINE_TUNNEL_SIZE = 5;
    private static final int MINE_FLOOR_TAKE = 32;
    private static final String BLOCK = "block";
    private static final int LEFT_ORE_RADIUS = 4;
    private static final String ITEM = "item";
    private static final String COUNT = "count";
    private static final String CONTAINER = "container";
    private static final String NEAREST = "nearest";
    private static final String SPECIES = "species";
    private static final String RADIUS = "radius";
    private static final String TORCH = "minecraft:torch";
    private static final int TORCHES_PER_CRAFT = 4;
    private static final int CHARCOAL_LOGS = 4;
    private static final int CHARCOAL_BATCH = 2;
    private static final String BUILD_SCHEMATIC = "build_schematic";
    private static final String FILE = "file";
    private static final String FILL = "fill";
    private static final String ACQUIRE = "acquire";
    private static final String CRAFT = "craft";
    private static final String PLACE = "place";
    private static final String WAIT = "wait";
    private static final String TICKS = "ticks";
    private static final String CLEAR_AREA = "clear_area";
    private static final String WITHDRAW = "withdraw";
    private static final String MINECRAFT = "minecraft:";
    private static final String DEFAULT_SCHEME = "stone_house";
    private static final String DEFAULT_FENCE_BLOCK = "oak_fence";
    private static final String DEFAULT_LURE_ITEM = "wheat";
    private static final int DISMANTLE_RADIUS = 4;
    private static final String FENCE_SCHEME = "fence";
    private static final String ENCHANT_ROOM_SCHEME = "enchant_room_v1";
    private static final String ENCHANT_ROOM_SHELVES_SCHEME = "enchant_room_v2";
    private static final String ENCHANT_ROOM_HALL_SCHEME = "enchant_room_v3";
    private static final int SUPPLY_CONTAINER_RADIUS = 8;
    static final List<String> LEFTOVERS = List.of(
            "minecraft:stick",
            "minecraft:*_planks",
            "minecraft:*_log",
            "minecraft:cobblestone",
            "minecraft:charcoal",
            "minecraft:coal",
            TORCH);

    private static final int MINE_STEPS_SIZE = 3;
    private static final int RELOCATE_CLEAR_RADIUS = 4;

    private TicketPlans() {}

    public static Optional<String> craftItem(BoardCase boardCase) {
        Objects.requireNonNull(boardCase, "Кейс крафта не может быть null: без него неизвестен предмет");
        if (ToolSet.isTool(boardCase)) {
            return Optional.of(ToolSet.itemId(boardCase));
        }
        return switch (boardCase) {
            case CRAFTING_STATION -> Optional.of("minecraft:crafting_table");
            case CRAFT_ANVIL -> Optional.of("minecraft:anvil");
            case CRAFT_SMITHING_TABLE -> Optional.of("minecraft:smithing_table");
            case CRAFT_SMOKER -> Optional.of("minecraft:smoker");
            case CRAFT_FURNACE -> Optional.of("minecraft:furnace");
            case CRAFT_TORCHES -> Optional.of(TORCH);
            case STOCK_CHESTS -> Optional.of("minecraft:chest");
            case ENCHANT_SHELVES -> Optional.of("minecraft:bookshelf");
            case PLACE_BEEHIVE -> Optional.of(HiveRules.CRAFTED_BEEHIVE);
            default -> Optional.empty();
        };
    }

    public static TaskNode plan(BoardCase boardCase, TicketTarget target) {
        return plan(boardCase, 1, target);
    }

    public static TaskNode plan(BoardCase boardCase, int level, TicketTarget target) {
        Objects.requireNonNull(boardCase, "Кейс плана не может быть null: без него непонятно, что строить");
        if (level < 1) {
            throw new IllegalArgumentException("Уровень плана " + level + " меньше 1: первая постройка "
                    + "ставится уровнем 1, нулевого уровня не существует");
        }
        int max = BuildingLevels.maxLevel(boardCase);
        if (level > max) {
            throw new IllegalArgumentException("Уровень " + level + " кейса " + boardCase + " выше максимального " + max
                    + ": уровень постройки не существует и выдавать его нельзя");
        }
        var staged = StagePlans.plan(boardCase, level, target).or(() -> RescuePlans.plan(boardCase, target));
        if (staged.isPresent()) {
            return staged.orElseThrow();
        }
        return switch (boardCase) {
            case BUILD_MINE -> buildMineLevel(level, target);
            case BUILD_PEN -> buildPenLevel(level, target);
            case ENCHANT_ROOM -> enchantRoomLevel(level, target);
            case BUILD_HOUSE -> buildHouse(level, target);
            case BUILD_ORCHARD -> buildOrchard(target);
            case BUILD_WAREHOUSE -> buildWarehouseLevel(level, target);
            default -> legacyPlan(boardCase, target, level);
        };
    }

    private static TaskNode legacyPlan(BoardCase boardCase, TicketTarget target, int level) {
        return switch (boardCase) {
            case FILL_CRATER -> PitFillPlan.plan(target);
            case REPAIR_FENCE -> repairFence(target);
            case CAPTURE_ANIMAL -> captureAnimal(target);
            case LURE_ANIMAL -> lureAnimal(target);
            case SUPPLY_SITE -> TicketStoragePlans.supplySite(target);
            case GATHER_MATERIALS ->
                target.leveling() == null ? TicketStoragePlans.gatherMaterials(target) : RoutePlans.dig(target);
            case CLEAR_GROUNDS -> withLeftovers(GroundsPlan.plan(target), target);
            case COLLECT_DROPS -> collectDrops(target);
            case REPAIR_BUILDING -> repairBuilding(target);
            case REPAIR_TOOL -> repairTool(target);
            case STOCK_CHESTS -> yard(target);
            case CRAFTING_STATION -> tableNearSpirit(target);
            case CRAFT_ANVIL -> craft("minecraft:anvil", 1, target);
            case CRAFT_SMITHING_TABLE -> craft("minecraft:smithing_table", 1, target);
            case CRAFT_SMOKER -> craft("minecraft:smoker", 1, target);
            case CRAFT_FURNACE -> placeInYard("minecraft:furnace", 1, target);
            case CRAFT_TORCHES -> TorchPlans.plan(target);
            case SMELT_CHARCOAL -> smeltCharcoal(target);
            case GRIND_BLAZE_POWDER, BREW_POTION, HARVEST_NETHER_WART, CRAFT_BREWING_STAND ->
                BrewPlans.plan(boardCase, target);
            case FISH -> fishWorkPoint(target);
            case CRAFT_WOODEN_PICKAXE,
                    CRAFT_WOODEN_AXE,
                    CRAFT_WOODEN_SHOVEL,
                    CRAFT_STONE_PICKAXE,
                    CRAFT_STONE_AXE,
                    CRAFT_STONE_SHOVEL,
                    CRAFT_STONE_SWORD,
                    CRAFT_IRON_PICKAXE,
                    CRAFT_IRON_AXE,
                    CRAFT_IRON_SHOVEL,
                    CRAFT_IRON_SWORD,
                    CRAFT_DIAMOND_PICKAXE,
                    CRAFT_DIAMOND_AXE,
                    CRAFT_DIAMOND_SHOVEL,
                    CRAFT_DIAMOND_SWORD -> ToolCraftPlan.of(boardCase, target);
            case CRAFT_SHEARS -> craft(WoolRules.CRAFTED_SHEARS, 1, target);
            case CRAFT_BED -> craft(bedItem(target), target.count() > 0 ? target.count() : 1, target);
            case WEAVE_WOOL ->
                craft(
                        target.craftItem() == null ? WoolRules.CRAFTED_WOOL : target.craftItem(),
                        target.count() > 0 ? target.count() : 1,
                        target);
            case SHEAR_SHEEP -> shearSheep(target);
            case HUNT_WILD_SHEEP -> huntWildSheep(target);
            case HARVEST_HIVE -> harvestHive();
            case HARVEST_ORCHARD -> OrchardPlans.harvest(target);
            case PLACE_BEEHIVE -> placeBeehive(target);
            case BUILD_FENCE -> buildFence(target, level);
            case LIGHTING -> TorchPlans.lighting(target);
            case BUILD_STREETS -> RoutePlans.path(target);
            case ENCHANT_SHELVES -> craft("minecraft:bookshelf", 4, target);
            case DISMANTLE_SHELTER -> dismantleShelter(target);
            case BREED_ANIMALS -> breedAnimals(target);
            case HERD_CULL -> herdCull(target);
            case RELOCATE_BUILDING -> relocateBuilding(target);
            case RESTOCK_FOOD -> TicketStoragePlans.restockFood(target);
            case TRANSFER_ITEM -> transferItem(target);
            case MINE_LEFT_ORE -> mineLeftOre(target);
            default ->
                throw new IllegalArgumentException("Кейс " + boardCase
                        + " не имеет плана на доске: выдача задания без плана оставила бы духа "
                        + "без задачи — новый кейс обязан прийти со своей строкой плана "
                        + "или записью в EXTERNAL_CASES");
        };
    }

    public static BlockCoord mineDescentOrigin(TicketTarget target) {
        Objects.requireNonNull(target, "Цель задания не может быть null: спуск ставится по плану или в цели");
        return target.work() == null ? target.target() : target.work().site().mineEntrance();
    }

    private static TaskNode mineDescent(TicketTarget target) {
        BlockCoord origin = mineDescentOrigin(target);
        int x = origin.x();
        int z = origin.z();
        return sequence(
                new FallbackNode(List.of(
                        gotoPoint(new BlockCoord(x, target.target().y(), z)),
                        new ScriptCall(WAIT, Map.of(TICKS, CatalogLimits.WAIT_TICKS_MIN)))),
                takeFloor("minecraft:cobblestone"),
                takeFloor(MINECRAFT + PlotLeveling.DEFAULT_BLOCK),
                new ScriptCall(
                        BUILD_SCHEMATIC,
                        Map.of(
                                FILE,
                                MINE_DESCENT_SCHEME,
                                "x",
                                x,
                                "y",
                                origin.y(),
                                "z",
                                z,
                                "rotation",
                                MINE_DESCENT_ROTATION)));
    }

    static TaskNode takeFrom(String item, int count) {
        return new FallbackNode(List.of(
                new ScriptCall(
                        WITHDRAW,
                        Map.of(
                                CONTAINER,
                                NEAREST,
                                ITEM,
                                item,
                                COUNT,
                                Math.max(1, Math.min(count, CatalogLimits.COUNT_MAX)),
                                RADIUS,
                                SUPPLY_CONTAINER_RADIUS)),
                new ScriptCall(WAIT, Map.of(TICKS, CatalogLimits.WAIT_TICKS_MIN))));
    }

    private static TaskNode takeFloor(String item) {
        return new FallbackNode(List.of(
                new ScriptCall(
                        WITHDRAW,
                        Map.of(
                                CONTAINER,
                                NEAREST,
                                ITEM,
                                item,
                                COUNT,
                                MINE_FLOOR_TAKE,
                                RADIUS,
                                SUPPLY_CONTAINER_RADIUS)),
                new ScriptCall(WAIT, Map.of(TICKS, CatalogLimits.WAIT_TICKS_MIN))));
    }

    private static TaskNode buildMineLevel(int level, TicketTarget target) {
        return switch (level) {
            case 1 -> mineDescent(target);
            case 2 ->
                new ScriptCall(
                        "dig_area",
                        Map.of(
                                "x",
                                target.target().x(),
                                "y",
                                target.target().y(),
                                "z",
                                target.target().z(),
                                "size",
                                MINE_TUNNEL_SIZE));
            case 3 ->
                sequence(
                        new ScriptCall(
                                "dig_area",
                                Map.of(
                                        "x",
                                        target.target().x(),
                                        "y",
                                        target.target().y(),
                                        "z",
                                        target.target().z(),
                                        "size",
                                        MINE_STEPS_SIZE)),
                        new ScriptCall(
                                FILL,
                                Map.of(
                                        BLOCK,
                                        "minecraft:cobblestone",
                                        "x1",
                                        target.target().x() - MINE_STEPS_SIZE,
                                        "y1",
                                        target.target().y() - 1,
                                        "z1",
                                        target.target().z() - MINE_STEPS_SIZE,
                                        "x2",
                                        target.target().x() + MINE_STEPS_SIZE,
                                        "y2",
                                        target.target().y() - 1,
                                        "z2",
                                        target.target().z() + MINE_STEPS_SIZE)));
            default ->
                sequence(
                        gotoPoint(target.target()),
                        new ScriptCall(
                                "dig_area",
                                Map.of(
                                        "x",
                                        target.target().x(),
                                        "y",
                                        target.target().y(),
                                        "z",
                                        target.target().z(),
                                        "size",
                                        MINE_TUNNEL_SIZE)));
        };
    }

    private static TaskNode buildPenLevel(int level, TicketTarget target) {
        return sequence(
                gotoTarget(target), schematic(penScheme(level), target, level > 1 ? penScheme(level - 1) : null));
    }

    private static String penScheme(int level) {
        return switch (level) {
            case 1 -> "pen_4x5";
            case 2 -> "pen_7x7";
            default -> "pen_9x9";
        };
    }

    private static TaskNode buildWarehouseLevel(int level, TicketTarget target) {
        return sequence(
                gotoTarget(target),
                schematic(WarehouseRules.scheme(level), target, level > 1 ? WarehouseRules.scheme(level - 1) : null));
    }

    private static TaskNode enchantRoomLevel(int level, TicketTarget target) {
        return sequence(
                gotoTarget(target),
                schematic(enchantScheme(level), target, level > 1 ? enchantScheme(level - 1) : null));
    }

    private static String enchantScheme(int level) {
        return switch (level) {
            case 1 -> ENCHANT_ROOM_SCHEME;
            case 2 -> ENCHANT_ROOM_SHELVES_SCHEME;
            default -> ENCHANT_ROOM_HALL_SCHEME;
        };
    }

    private static TaskNode repairFence(TicketTarget target) {
        String fence = target.fenceBlock() == null ? DEFAULT_FENCE_BLOCK : target.fenceBlock();
        return sequence(gotoTarget(target), fillArea(target, fence, 0));
    }

    private static TaskNode captureAnimal(TicketTarget target) {
        Species species = target.species() == null ? Species.COW : target.species();
        return luredCapture(species, FeedTable.feedFor(species).get(0));
    }

    private static TaskNode lureAnimal(TicketTarget target) {
        Species species = target.species() == null ? Species.COW : target.species();
        String lure = target.lureItem() == null ? DEFAULT_LURE_ITEM : target.lureItem();
        return luredCapture(species, lure);
    }

    private static TaskNode luredCapture(Species species, String feed) {
        return sequence(
                new ScriptCall(
                        WITHDRAW,
                        Map.of(
                                CONTAINER,
                                NEAREST,
                                ITEM,
                                feed,
                                COUNT,
                                1,
                                RADIUS,
                                CatalogLimits.CONTAINER_RADIUS_DEFAULT)),
                new ScriptCall(
                        "capture",
                        Map.of(
                                SPECIES,
                                species.name().toLowerCase(Locale.ROOT),
                                RADIUS,
                                CatalogLimits.CAPTURE_RADIUS_DEFAULT)));
    }

    private static TaskNode buildFence(TicketTarget target, int level) {
        int part = target.count();
        String base = level >= 2 ? "wall_3" : FENCE_SCHEME;
        String scheme = part >= 1 && part <= FENCE_PARTS ? SchematicPart.name(base, part, FENCE_PARTS) : base;
        return schematic(scheme, target.target(), target);
    }

    static TaskNode placeInYard(String item, int count, TicketTarget target) {
        return withLeftovers(
                sequence(
                        gotoTarget(target),
                        new ScriptCall(
                                CRAFT,
                                Map.of(
                                        ITEM,
                                        item,
                                        COUNT,
                                        count,
                                        RADIUS,
                                        CatalogLimits.STATION_RADIUS_DEFAULT,
                                        PLACE,
                                        true))),
                target);
    }

    private static TaskNode tableNearSpirit(TicketTarget target) {
        return withLeftovers(
                new ScriptCall(
                        CRAFT,
                        Map.of(
                                ITEM,
                                "minecraft:crafting_table",
                                COUNT,
                                1,
                                RADIUS,
                                CatalogLimits.STATION_RADIUS_DEFAULT,
                                PLACE,
                                true)),
                target);
    }

    private static TaskNode yard(TicketTarget target) {
        return withLeftovers(
                sequence(
                        gotoTarget(target),
                        new ScriptCall(
                                CRAFT,
                                Map.of(
                                        ITEM,
                                        "minecraft:chest",
                                        COUNT,
                                        2,
                                        RADIUS,
                                        CatalogLimits.STATION_RADIUS_DEFAULT,
                                        PLACE,
                                        true)),
                        new ScriptCall(
                                CRAFT, Map.of(ITEM, "minecraft:crafting_table", COUNT, 1, RADIUS, 1, PLACE, true))),
                target);
    }

    static TaskNode craft(String item, int count, TicketTarget target) {
        return sequence(
                gotoTarget(target),
                new ScriptCall(CRAFT, Map.of(ITEM, item, COUNT, count, RADIUS, CatalogLimits.STATION_RADIUS_DEFAULT)));
    }

    private static TaskNode fishWorkPoint(TicketTarget target) {
        return sequence(
                gotoTarget(target),
                new ScriptCall(
                        "fish",
                        Map.of(
                                COUNT,
                                CatalogLimits.FISH_COUNT_DEFAULT,
                                "duration_ticks",
                                CatalogLimits.FISH_DURATION_DEFAULT,
                                RADIUS,
                                CatalogLimits.FISH_RADIUS_DEFAULT)));
    }

    static int torchCount(TicketTarget target) {
        int count = target.count() > 0 ? target.count() : TORCHES_PER_CRAFT;
        return Math.min(count, CatalogLimits.COUNT_MAX);
    }

    private static TaskNode smeltCharcoal(TicketTarget target) {
        String log = target.supplyItem() == null ? "oak_log" : target.supplyItem();
        int logs = target.supplyCount() > 0 ? target.supplyCount() : CHARCOAL_LOGS;
        int charcoal = target.count() > 0 ? target.count() : CHARCOAL_BATCH;
        return sequence(
                gotoTarget(target),
                new ScriptCall(ACQUIRE, Map.of(ITEM, log, COUNT, Math.min(logs, CatalogLimits.COUNT_MAX))),
                new ScriptCall(
                        "smelt",
                        Map.of(ITEM, "charcoal", COUNT, charcoal, RADIUS, CatalogLimits.STATION_RADIUS_DEFAULT)),
                store(target, LEFTOVERS.toArray(String[]::new)));
    }

    static TaskNode store(TicketTarget target, String... items) {
        List<TaskNode> steps = new ArrayList<>(items.length + 1);
        steps.add(gotoTarget(target));
        for (String item : items) {
            steps.add(new FallbackNode(List.of(
                    new ScriptCall(
                            "deposit",
                            Map.of(
                                    CONTAINER,
                                    "auto",
                                    ITEM,
                                    item,
                                    COUNT,
                                    CatalogLimits.COUNT_MAX,
                                    RADIUS,
                                    SUPPLY_CONTAINER_RADIUS,
                                    "leftover",
                                    true)),
                    new ScriptCall(WAIT, Map.of(TICKS, CatalogLimits.WAIT_TICKS_MIN)))));
        }
        return new SequenceNode(steps);
    }

    static TaskNode withLeftovers(TaskNode step, TicketTarget target) {
        return sequence(step, store(target, LEFTOVERS.toArray(String[]::new)));
    }

    private static TaskNode dismantleShelter(TicketTarget target) {
        return new ScriptCall(
                CLEAR_AREA,
                Map.of(
                        "x1",
                        target.target().x() - DISMANTLE_RADIUS,
                        "y1",
                        target.target().y(),
                        "z1",
                        target.target().z() - DISMANTLE_RADIUS,
                        "x2",
                        target.target().x() + DISMANTLE_RADIUS,
                        "y2",
                        target.target().y() + 2,
                        "z2",
                        target.target().z() + DISMANTLE_RADIUS));
    }

    private static TaskNode collectDrops(TicketTarget target) {
        Map<String, Object> params = target.dropItems().isEmpty()
                ? Map.of(RADIUS, CatalogLimits.DROP_RADIUS_DEFAULT)
                : Map.of(RADIUS, CatalogLimits.DROP_RADIUS_DEFAULT, "filter", target.dropItems());
        return sequence(gotoPoint(target.target()), new ScriptCall("collect_drops", params));
    }

    private static TaskNode buildOrchard(TicketTarget target) {
        StageWork work = target.work();
        StageSite site = work == null ? null : work.site();
        String scheme = StageRules.current()
                .scheme(BoardCase.BUILD_ORCHARD, 1)
                .orElseThrow(() -> new IllegalStateException(
                        "stages.json: нет схемы сада уровня 1 — постройке сада не по чему строиться"));
        BlockCoord origin = site == null ? target.target() : site.orchardOrigin(target.building());
        String wood = target.wood() == null ? WoodSpecies.OAK : target.wood();
        List<TaskNode> steps = new ArrayList<>();
        BlockCoord yard = site == null ? target.target() : site.workYard();
        steps.add(new FallbackNode(
                List.of(gotoPoint(yard), new ScriptCall(WAIT, Map.of(TICKS, CatalogLimits.WAIT_TICKS_MIN)))));
        TorchPlans.addMaterials(steps, orchardMaterials(scheme), wood, target);
        steps.add(schematic(scheme, origin, target));
        return new SequenceNode(steps);
    }

    private static Map<String, Integer> orchardMaterials(String scheme) {
        Schematic schematic = PresetLibrary.current()
                .byId(scheme)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Нет схемы '" + scheme + "' для сада: проверьте presets.json и файл схемы"))
                .schematic();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Schematic.Entry entry : schematic.blocks()) {
            if (!entry.optional() && !SchematicActions.isNotBought(entry.block(), entry.state())) {
                counts.merge(SchematicBlocks.itemOf(entry.block()), 1, Integer::sum);
            }
        }
        return Map.copyOf(counts);
    }

    private static TaskNode buildHouse(int level, TicketTarget target) {
        String scheme = target.schemeFile() == null
                ? BuildingLevels.scheme(BoardCase.BUILD_HOUSE, level).orElse(DEFAULT_SCHEME)
                : target.schemeFile();
        String previous = level > 1
                ? BuildingLevels.scheme(BoardCase.BUILD_HOUSE, level - 1).orElse(null)
                : null;
        return sequence(gotoTarget(target), schematic(scheme, target, previous));
    }

    private static TaskNode repairBuilding(TicketTarget target) {
        if (target.schemeFile() == null && target.leveling() != null) {
            return LevelRepair.plan(target);
        }
        if (target.schemeFile() == null) {
            throw new IllegalArgumentException("Ремонт без схемы постройки и без выравнивания: чинят по схеме той "
                    + "постройки, что повреждена (StageWork.repairs или файл схемы); цель задания потеряна — ждать, "
                    + "пока аудит этапа её восстановит");
        }
        return sequence(gotoTarget(target), schematic(target.schemeFile(), target));
    }

    private static TaskNode repairTool(TicketTarget target) {
        if (target.craftItem() == null) {
            throw new IllegalArgumentException("Задание ремонта инструмента без предмета: скрипт repair_tool "
                    + "чинит конкретный изношенный предмет, без имени чинить нечего");
        }
        return sequence(gotoTarget(target), new ScriptCall("repair_tool", Map.of(ITEM, target.craftItem())));
    }

    private static TaskNode breedAnimals(TicketTarget target) {
        Species species = target.species() == null ? Species.COW : target.species();
        return new ScriptCall(
                "breed",
                Map.of(
                        SPECIES,
                        species.name().toLowerCase(Locale.ROOT),
                        RADIUS,
                        CatalogLimits.HERD_RADIUS_DEFAULT,
                        "pairs",
                        CatalogLimits.HERD_PAIRS_DEFAULT));
    }

    private static TaskNode herdCull(TicketTarget target) {
        Species species = target.species() == null ? Species.COW : target.species();
        return new ScriptCall(
                "cull",
                Map.of(SPECIES, species.name().toLowerCase(Locale.ROOT), RADIUS, CatalogLimits.HERD_RADIUS_DEFAULT));
    }

    private static TaskNode shearSheep(TicketTarget target) {
        int count = target.count() > 0 ? target.count() : WoolRules.minPenSheep();
        return sequence(
                gotoTarget(target),
                new ScriptCall(
                        "shear",
                        Map.of(
                                RADIUS, WoolRules.shearRadius(),
                                COUNT, Math.min(count, CatalogLimits.HERD_COUNT_MAX))));
    }

    private static TaskNode harvestHive() {
        return new ScriptCall(
                "harvest_hive", Map.of(RADIUS, HiveRules.hiveRadius(), COUNT, CatalogLimits.HIVE_COUNT_DEFAULT));
    }

    private static TaskNode placeBeehive(TicketTarget target) {
        BlockCoord point = target.target();
        BlockCoord cell = new BlockCoord(point.x(), point.y() + 1, point.z());
        return sequence(
                craft(HiveRules.CRAFTED_BEEHIVE, 1, target),
                new ScriptCall(
                        "place_block",
                        Map.of(BLOCK, HiveRules.CRAFTED_BEEHIVE, "x", cell.x(), "y", cell.y(), "z", cell.z())));
    }

    private static TaskNode huntWildSheep(TicketTarget target) {
        int count = target.count() > 0 ? target.count() : WoolRules.wildSheepPerDay();
        return sequence(
                gotoTarget(target),
                new ScriptCall(
                        "hunt",
                        Map.of(
                                SPECIES, Species.SHEEP.name().toLowerCase(Locale.ROOT),
                                RADIUS, WoolRules.wildSheepRadius(),
                                COUNT, Math.min(count, CatalogLimits.HERD_COUNT_MAX))));
    }

    private static String bedItem(TicketTarget target) {
        if (target.craftItem() != null) {
            return target.craftItem();
        }
        return WoolRules.CRAFTED_BED;
    }

    private static TaskNode relocateBuilding(TicketTarget target) {
        if (target.from() == null || target.to() == null) {
            throw new IllegalArgumentException(
                    "Перенос без from/to: снос идёт в from, " + "стройка — в to, без обеих точек план не собрать");
        }
        if (target.from().equals(target.to())) {
            throw new IllegalArgumentException(
                    "Перенос из " + target.from() + " в ту же точку: " + "сносить и строить на одном месте нечего");
        }
        if (target.schemeFile() == null) {
            throw new IllegalArgumentException("Перенос без схемы: в to строится та же постройка, "
                    + "что стояла в from, без имени схемы строить нечего");
        }
        if (target.schemeFile().startsWith("storage_v")) {
            return warehouseRelocate(target);
        }
        return new SequenceNode(List.of(
                gotoPoint(target.from()),
                demolishBox(target.from(), RELOCATE_CLEAR_RADIUS),
                gotoPoint(target.to()),
                new ScriptCall(
                        BUILD_SCHEMATIC,
                        Map.of(
                                FILE,
                                target.schemeFile(),
                                "x",
                                target.to().x(),
                                "y",
                                target.to().y(),
                                "z",
                                target.to().z()))));
    }

    private static TaskNode warehouseRelocate(TicketTarget target) {
        String scheme = relocation(target.schemeFile(), "schemeFile");
        BlockCoord from = relocation(target.from(), "from");
        BlockCoord to = relocation(target.to(), "to");
        int level = Integer.parseInt(scheme.substring("storage_v".length()));
        int radius = warehouseRadius(level);
        return new SequenceNode(List.of(
                gotoPoint(from),
                demolishBox(from, radius),
                new ScriptCall("collect_drops", Map.of(RADIUS, CatalogLimits.DROP_RADIUS_MAX)),
                gotoPoint(to),
                new ScriptCall(
                        "unload",
                        Map.of(CONTAINER, "auto", RADIUS, CatalogLimits.CONTAINER_RADIUS_DEFAULT, "force", true))));
    }

    private static <T> T relocation(@Nullable T value, String field) {
        if (value == null) {
            throw new IllegalStateException("Цель переноса склада без поля " + field
                    + ": кейс RELOCATE_BUILDING собирает цель с постройкой, откуда и куда — проверьте TicketTargets");
        }
        return value;
    }

    private static int warehouseRadius(int level) {
        if (level < 1 || level > WarehouseRules.MAX_LEVEL) {
            throw new IllegalArgumentException(
                    "Уровень склада " + level + " вне 1.." + WarehouseRules.MAX_LEVEL + ": радиуса сноса для него нет");
        }
        return switch (level) {
            case 1 -> 4;
            case 2 -> 7;
            case 3 -> 10;
            default -> 16;
        };
    }

    private static TaskNode mineLeftOre(TicketTarget target) {
        String ore = target.supplyItem();
        OreKind found = ore == null ? OreKind.ANY : OreKind.ofBlock(ore).orElse(OreKind.ANY);
        String kind = found.name().toLowerCase(Locale.ROOT);
        return sequence(
                gotoTarget(target),
                GatherPlans.pickaxe(),
                new ScriptCall("mine_vein", Map.of("ore", kind, COUNT, 1, RADIUS, LEFT_ORE_RADIUS)));
    }

    private static TaskNode transferItem(TicketTarget target) {
        if (target.transferTo() == null) {
            throw new IllegalArgumentException("Передача без получателя: deliver не знает, кому нести груз, "
                    + "а withdraw — чей инвентарь пополнять");
        }
        if (target.supplyItem() == null || target.supplyCount() <= 0) {
            throw new IllegalArgumentException(
                    "Передача без предмета или количества: " + "нечего нести и нечем закрыть нехватку");
        }
        int count = Math.min(target.supplyCount(), CatalogLimits.COUNT_MAX);
        if (target.transferFrom() != null) {
            return new ScriptCall(
                    "deliver", Map.of(ITEM, target.supplyItem(), COUNT, count, "to", "spirit:" + target.transferTo()));
        }
        return sequence(
                gotoTarget(target),
                new FallbackNode(List.of(
                        new ScriptCall(
                                WITHDRAW,
                                Map.of(
                                        CONTAINER,
                                        "auto",
                                        ITEM,
                                        target.supplyItem(),
                                        COUNT,
                                        count,
                                        RADIUS,
                                        CatalogLimits.CONTAINER_RADIUS_DEFAULT)),
                        new ScriptCall(WAIT, Map.of(TICKS, CatalogLimits.WAIT_TICKS_MIN)))));
    }

    private static ScriptCall schematic(String scheme, TicketTarget target) {
        return schematic(scheme, target.target(), target, null);
    }

    private static ScriptCall schematic(String scheme, TicketTarget target, @Nullable String previous) {
        return schematic(scheme, target.target(), target, previous);
    }

    private static ScriptCall schematic(String scheme, BlockCoord origin, TicketTarget target) {
        return schematic(scheme, origin, target, null);
    }

    private static ScriptCall schematic(
            String scheme, BlockCoord origin, TicketTarget target, @Nullable String previous) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(FILE, scheme);
        params.put("x", origin.x());
        params.put("y", origin.y());
        params.put("z", origin.z());
        if (target.wood() != null) {
            params.put(BuildSchematicParams.WOOD, target.wood());
        }
        if (previous != null) {
            params.put(BuildSchematicParams.PREVIOUS, previous);
        }
        return new ScriptCall(BUILD_SCHEMATIC, Map.copyOf(params));
    }

    private static TaskNode fillArea(TicketTarget target, String block, int radius) {
        return fillArea(target, block, radius, target.target().y());
    }

    private static TaskNode fillArea(TicketTarget target, String block, int radius, int fromY) {
        return new ScriptCall(
                FILL,
                Map.of(
                        BLOCK,
                        block,
                        "x1",
                        target.target().x() - radius,
                        "y1",
                        fromY,
                        "z1",
                        target.target().z() - radius,
                        "x2",
                        target.target().x() + radius,
                        "y2",
                        target.target().y(),
                        "z2",
                        target.target().z() + radius));
    }

    static ScriptCall gotoTarget(TicketTarget target) {
        return gotoPoint(target.target());
    }

    private static ScriptCall gotoPoint(BlockCoord point) {
        return new ScriptCall("goto", Map.of("x", point.x(), "y", point.y(), "z", point.z()));
    }

    private static ScriptCall demolishBox(BlockCoord center, int radius) {
        return new ScriptCall(
                CLEAR_AREA,
                Map.of(
                        "x1",
                        center.x() - radius,
                        "y1",
                        center.y(),
                        "z1",
                        center.z() - radius,
                        "x2",
                        center.x() + radius,
                        "y2",
                        center.y() + 2,
                        "z2",
                        center.z() + radius,
                        "demolish",
                        true));
    }

    private static TaskNode sequence(TaskNode... steps) {
        List<TaskNode> list = new ArrayList<>(steps.length);
        for (TaskNode step : steps) {
            list.add(step);
        }
        return new SequenceNode(list);
    }
}
