package com.theminespirit.core.board;

import com.theminespirit.core.building.SchematicPart;
import com.theminespirit.core.forestry.TreeSpecies;
import com.theminespirit.core.husbandry.FeedTable;
import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.mining.OreKind;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.script.BuildSchematicParams;
import com.theminespirit.core.script.CatalogLimits;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * План задания по кейсу: чистая функция «кейс + точка → TaskNode» из скриптов
 * существующего каталога. Новых скриптов не добавляет: план собирается из
 * goto/acquire/fill/capture/build_schematic/withdraw/dig_area/clear_area/deliver.
 */
public final class TicketPlans {
    public static final Set<BoardCase> EXTERNAL_CASES = Set.of(BoardCase.EAT_TO_FULL);
    public static final String MINE_DESCENT_SCHEME = "mine_descent_v1";
    public static final int CLEAR_QUARTERS = 4;
    // Ограда кусками периметра на разных духов: одним духом она шла ~2900 тиков, двое простаивали (приёмка «джунгли»).
    public static final int FENCE_PARTS = 4;
    public static final String MINE_DESCENT_ROTATION = "clockwise_90";
    // Брёвен с малого дерева (дуб 4–6): число деревьев под добычу брёвен — с запасом (INC-64).
    private static final int LOGS_PER_TREE = 4;
    private static final String BLOCK = "block";
    private static final String COBBLESTONE = "cobblestone";
    private static final int LEFT_ORE_RADIUS = 4;
    private static final String ITEM = "item";
    private static final String COUNT = "count";
    private static final String CONTAINER = "container";
    private static final String NEAREST = "nearest";
    private static final String SPECIES = "species";
    private static final String RADIUS = "radius";
    private static final String DEFAULT_SCHEME = "stone_house";
    private static final String DEFAULT_FENCE_BLOCK = "oak_fence";
    private static final String DEFAULT_LURE_ITEM = "wheat";
    private static final String DEFAULT_SUPPLY_ITEM = "dirt";
    private static final int DEFAULT_SUPPLY_COUNT = 64;
    private static final int GROUNDS_CLEAR_RADIUS = 16;
    private static final int CRATER_FILL_RADIUS = 1;
    private static final int DISMANTLE_RADIUS = 4;
    private static final String FENCE_SCHEME = "fence";
    private static final String ENCHANT_ROOM_SCHEME = "enchant_room_v1";
    private static final String ENCHANT_ROOM_SHELVES_SCHEME = "enchant_room_v2";
    private static final String ENCHANT_ROOM_HALL_SCHEME = "enchant_room_v3";
    private static final int SUPPLY_CONTAINER_RADIUS = 8;
    // Остатки шага (палки, доски, брёвна, камень, уголь, факелы) — в сундук сразу по окончании шага: у занятого духа
    // палки после добычи забора держали крафт факелов ~2000 тиков (приёмка «джунгли» 24.10, transfer…:stick PENDING).
    private static final List<String> LEFTOVERS = List.of(
            "minecraft:stick",
            "minecraft:*_planks",
            "minecraft:*_log",
            "minecraft:cobblestone",
            "minecraft:charcoal",
            "minecraft:coal",
            "minecraft:torch");

    private static final int MINE_TUNNEL_SIZE = 5;
    private static final int MINE_STEPS_SIZE = 3;
    private static final int RESTOCK_FOOD_COUNT = 48;
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
            case CRAFT_TORCHES -> Optional.of("minecraft:torch");
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
        return switch (boardCase) {
            case BUILD_MINE -> buildMineLevel(level, target);
            case BUILD_PEN -> buildPenLevel(level, target);
            case ENCHANT_ROOM -> enchantRoomLevel(level, target);
            case BUILD_HOUSE -> buildHouse(level, target);
            case BUILD_WAREHOUSE -> buildWarehouseLevel(level, target);
            default -> legacyPlan(boardCase, target);
        };
    }

    private static TaskNode legacyPlan(BoardCase boardCase, TicketTarget target) {
        return switch (boardCase) {
            case FILL_CRATER -> fillCrater(target);
            case REPAIR_FENCE -> repairFence(target);
            case CAPTURE_ANIMAL -> captureAnimal(target);
            case LURE_ANIMAL -> lureAnimal(target);
            case SUPPLY_SITE -> supplySite(target);
            case GATHER_MATERIALS -> gatherMaterials(target);
            case CLEAR_GROUNDS -> clearGrounds(target);
            case COLLECT_DROPS -> collectDrops(target);
            case REPAIR_BUILDING -> repairBuilding(target);
            case REPAIR_TOOL -> repairTool(target);
            // Сундуки склада ставятся во дворе сразу (place): в инвентаре крафтера склада не было (приёмка «джунгли»).
            case STOCK_CHESTS -> yard(target);
            case CRAFTING_STATION -> tableNearSpirit(target);
            case CRAFT_ANVIL -> craft("minecraft:anvil", 1, target);
            case CRAFT_SMITHING_TABLE -> craft("minecraft:smithing_table", 1, target);
            case CRAFT_SMOKER -> craft("minecraft:smoker", 1, target);
            // Печь ставится во дворе станций сразу: в инвентаре крафтера её не находила плавка других духов, а сам
            // крафтер ставил её там, где плавил (24.10: «нужна печь: рядом (16) нет», факелы ограды ждали печь).
            case CRAFT_FURNACE -> placeInYard("minecraft:furnace", 1, target);
            // Факелы и остаток угля — в сундук у дома: иначе они оставались у крафтера, и следующий крафт факелов
            // ждал передачу угля от него, занятого оградой, 1700 тиков (приёмка «джунгли» 24.10).
            case CRAFT_TORCHES ->
                sequence(
                        craft("minecraft:torch", torchCount(target), target),
                        store(target, LEFTOVERS.toArray(String[]::new)));
            case SMELT_CHARCOAL -> smeltCharcoal(target);
            case FISH -> fishWorkPoint(target);
            case CRAFT_WOODEN_PICKAXE,
                    CRAFT_WOODEN_AXE,
                    CRAFT_WOODEN_SHOVEL,
                    CRAFT_STONE_PICKAXE,
                    CRAFT_STONE_AXE,
                    CRAFT_STONE_SHOVEL,
                    CRAFT_STONE_SWORD -> craftTool(boardCase, target);
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
            case PLACE_BEEHIVE -> placeBeehive(target);
            case BUILD_FENCE -> buildFence(target);
            case LIGHTING -> lighting(target);
            case ENCHANT_SHELVES -> craft("minecraft:bookshelf", 4, target);
            case DISMANTLE_SHELTER -> dismantleShelter(target);
            case BREED_ANIMALS -> breedAnimals(target);
            case HERD_CULL -> herdCull(target);
            case RELOCATE_BUILDING -> relocateBuilding(target);
            case RESTOCK_FOOD -> restockFood(target);
            case TRANSFER_ITEM -> transferItem(target);
            case MINE_LEFT_ORE -> mineLeftOre(target);
            default ->
                throw new IllegalArgumentException("Кейс " + boardCase
                        + " не имеет плана на доске: выдача задания без плана оставила бы духа "
                        + "без задачи — новый кейс обязан прийти со своей строкой плана "
                        + "или записью в EXTERNAL_CASES");
        };
    }

    // Спуск по схеме владельца (mine_descent_v1) в стороне от дома, вглубь на юг: ступени
    // 1×3 с шагом в блок до y=50 из файла — дух копает булыжник только здесь и выходит наверх пешком.
    public static BlockCoord mineDescentOrigin(BlockCoord home) {
        Objects.requireNonNull(home, "Дом не может быть null: спуск в шахту ставится в стороне от дома");
        return SettlementLayout.mineEntrance(home);
    }

    private static TaskNode mineDescent(TicketTarget target) {
        BlockCoord origin = mineDescentOrigin(target.target());
        int x = origin.x();
        int z = origin.z();
        return sequence(
                gotoPoint(new BlockCoord(x, target.target().y(), z)),
                new ScriptCall(
                        "build_schematic",
                        Map.of(
                                "file",
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
                                "fill",
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
        String scheme = switch (level) {
            case 1 -> "pen_4x5";
            case 2 -> "pen_7x7";
            default -> "pen_9x9";
        };
        return sequence(
                gotoTarget(target),
                new ScriptCall(
                        "build_schematic",
                        Map.of(
                                "file",
                                scheme,
                                "x",
                                target.target().x(),
                                "y",
                                target.target().y(),
                                "z",
                                target.target().z())));
    }

    private static TaskNode buildWarehouseLevel(int level, TicketTarget target) {
        return sequence(gotoTarget(target), schematic(WarehouseRules.scheme(level), target));
    }

    private static TaskNode enchantRoomLevel(int level, TicketTarget target) {
        String scheme = switch (level) {
            case 1 -> ENCHANT_ROOM_SCHEME;
            case 2 -> ENCHANT_ROOM_SHELVES_SCHEME;
            default -> ENCHANT_ROOM_HALL_SCHEME;
        };
        return sequence(gotoTarget(target), schematic(scheme, target));
    }

    private static TaskNode fillCrater(TicketTarget target) {
        int blocks = target.craterBlocks() > 0
                ? target.craterBlocks()
                : craterBlocksEstimate(BoardTuning.CRATER_BLOCKS_ESTIMATE_RADIUS);
        return sequence(
                gotoTarget(target),
                new ScriptCall("acquire", Map.of(ITEM, "dirt", COUNT, Math.min(blocks, CatalogLimits.COUNT_MAX))),
                fillArea(target, "dirt", CRATER_FILL_RADIUS));
    }

    private static int craterBlocksEstimate(int radius) {
        return (int) Math.ceil(Math.PI * radius * radius);
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
                        "withdraw",
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

    // Ограда — по периметру участка вокруг дома (SettlementLayout): точка задания — якорь участка.
    // count задания — номер куска периметра (1..FENCE_PARTS) для отдельного духа; 0 — вся ограда одним заданием.
    private static TaskNode buildFence(TicketTarget target) {
        int part = target.count();
        String scheme =
                part >= 1 && part <= FENCE_PARTS ? SchematicPart.name(FENCE_SCHEME, part, FENCE_PARTS) : FENCE_SCHEME;
        return sequence(gotoTarget(target), schematic(scheme, SettlementLayout.fenceOrigin(target.target()), target));
    }

    // Станции поселения (верстак, сундуки, печь) дух ставит вокруг себя: крафт — на рабочем дворе участка, вне
    // габарита дома, иначе стройка дома упиралась в верстак у якоря (решение 54b п.2).
    // Скрафтить во дворе станций и сразу поставить там (place): печь и сундуки склада первых шагов.
    private static TaskNode placeInYard(String item, int count, TicketTarget target) {
        return withLeftovers(
                sequence(
                        gotoPoint(SettlementLayout.workYard(target.target())),
                        new ScriptCall(
                                "craft",
                                Map.of(
                                        ITEM,
                                        item,
                                        COUNT,
                                        count,
                                        RADIUS,
                                        CatalogLimits.STATION_RADIUS_DEFAULT,
                                        "place",
                                        true))),
                target);
    }

    // Порядок владельца (INC-63): первый верстак — временный, рядом с духом, без привязки к двору; двор ставится после
    // выравнивания на высоте плана.
    private static TaskNode tableNearSpirit(TicketTarget target) {
        return withLeftovers(
                new ScriptCall(
                        "craft",
                        Map.of(
                                ITEM,
                                "minecraft:crafting_table",
                                COUNT,
                                1,
                                RADIUS,
                                CatalogLimits.STATION_RADIUS_DEFAULT,
                                "place",
                                true)),
                target);
    }

    // Двор после выравнивания: сундуки склада и постоянный верстак двора (поиск в радиусе 1 — временный верстак у
    // места первых крафтов его не заменяет).
    private static TaskNode yard(TicketTarget target) {
        return withLeftovers(
                sequence(
                        gotoPoint(SettlementLayout.workYard(target.target())),
                        new ScriptCall(
                                "craft",
                                Map.of(
                                        ITEM,
                                        "minecraft:chest",
                                        COUNT,
                                        2,
                                        RADIUS,
                                        CatalogLimits.STATION_RADIUS_DEFAULT,
                                        "place",
                                        true)),
                        new ScriptCall(
                                "craft", Map.of(ITEM, "minecraft:crafting_table", COUNT, 1, RADIUS, 1, "place", true))),
                target);
    }

    private static TaskNode craft(String item, int count, TicketTarget target) {
        return sequence(
                gotoTarget(target),
                new ScriptCall(
                        "craft", Map.of(ITEM, item, COUNT, count, RADIUS, CatalogLimits.STATION_RADIUS_DEFAULT)));
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

    private static int torchCount(TicketTarget target) {
        int count = target.count() > 0 ? target.count() : TorchRules.TORCHES_PER_CRAFT;
        return Math.min(count, CatalogLimits.COUNT_MAX);
    }

    private static TaskNode smeltCharcoal(TicketTarget target) {
        String log = target.supplyItem() == null ? "oak_log" : target.supplyItem();
        int logs = target.supplyCount() > 0 ? target.supplyCount() : TorchRules.CHARCOAL_LOGS;
        int charcoal = target.count() > 0 ? target.count() : TorchRules.CHARCOAL_BATCH;
        return sequence(
                gotoTarget(target),
                new ScriptCall("acquire", Map.of(ITEM, log, COUNT, Math.min(logs, CatalogLimits.COUNT_MAX))),
                new ScriptCall(
                        "smelt",
                        Map.of(ITEM, "charcoal", COUNT, charcoal, RADIUS, CatalogLimits.STATION_RADIUS_DEFAULT)),
                store(target, LEFTOVERS.toArray(String[]::new)));
    }

    // Сдать всё названное в ближайший сундук у места задания; сундука нет или сдавать нечего — шаг не валит план.
    private static TaskNode store(TicketTarget target, String... items) {
        List<TaskNode> steps = new ArrayList<>(items.length + 1);
        steps.add(gotoTarget(target));
        for (String item : items) {
            steps.add(new FallbackNode(List.of(
                    new ScriptCall(
                            "deposit",
                            Map.of(
                                    CONTAINER,
                                    NEAREST,
                                    ITEM,
                                    item,
                                    COUNT,
                                    CatalogLimits.COUNT_MAX,
                                    RADIUS,
                                    SUPPLY_CONTAINER_RADIUS,
                                    "leftover",
                                    true)),
                    new ScriptCall("wait", Map.of("ticks", CatalogLimits.WAIT_TICKS_MIN)))));
        }
        return new SequenceNode(steps);
    }

    private static TaskNode withLeftovers(TaskNode step, TicketTarget target) {
        return sequence(step, store(target, LEFTOVERS.toArray(String[]::new)));
    }

    private static TaskNode craftTool(BoardCase boardCase, TicketTarget target) {
        int count = target.toolCount() > 0 ? target.toolCount() : 1;
        return withLeftovers(
                craft(ToolSet.itemId(boardCase), Math.min(count, CatalogLimits.COUNT_MAX), target), target);
    }

    private static TaskNode lighting(TicketTarget target) {
        // Перед хижиной и за ней (SettlementLayout): внутри ограды клетки не заданы схемой, калитку освещают факелы на
        // её
        // столбах. Прежний ряд у дома (±2) попадал внутрь ограды 9×9 и валил её сверку.
        List<BlockCoord> torches = SettlementLayout.lighting(target.target());
        List<TaskNode> steps = new ArrayList<>(torches.size() + 2);
        steps.add(gotoTarget(target));
        // Факелы могут лежать в сундуке или у соседа: acquire берёт их до расстановки, иначе «нет материала torch».
        steps.add(new ScriptCall("acquire", Map.of(ITEM, "minecraft:torch", COUNT, torches.size())));
        for (BlockCoord torch : torches) {
            steps.add(new ScriptCall(
                    "place_block", Map.of(BLOCK, "minecraft:torch", "x", torch.x(), "y", torch.y(), "z", torch.z())));
        }
        return new SequenceNode(steps);
    }

    private static TaskNode dismantleShelter(TicketTarget target) {
        return new ScriptCall(
                "clear_area",
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

    private static TaskNode supplySite(TicketTarget target) {
        String item = target.supplyItem() == null ? DEFAULT_SUPPLY_ITEM : target.supplyItem();
        int count = target.supplyCount() > 0 ? target.supplyCount() : DEFAULT_SUPPLY_COUNT;
        int batch = Math.min(count, CatalogLimits.COUNT_MAX);
        return sequence(
                gotoTarget(target),
                new ScriptCall("acquire", Map.of(ITEM, item, COUNT, batch)),
                // Добыча уводит духа от места, а сдача ищет сундук рядом с духом (INC-30).
                gotoTarget(target),
                new ScriptCall(
                        "deposit",
                        Map.of(CONTAINER, NEAREST, ITEM, item, COUNT, batch, RADIUS, SUPPLY_CONTAINER_RADIUS)));
    }

    private static TaskNode collectDrops(TicketTarget target) {
        Map<String, Object> params = target.dropItems().isEmpty()
                ? Map.of(RADIUS, CatalogLimits.DROP_RADIUS_DEFAULT)
                : Map.of(RADIUS, CatalogLimits.DROP_RADIUS_DEFAULT, "filter", target.dropItems());
        return sequence(gotoPoint(target.target()), new ScriptCall("collect_drops", params));
    }

    // Добытое — в сундук поселения рядом с домом (решение главного агента 24.10): крафт и стройка берут из сундука,
    // а не ждут передачу от занятого духа-«копилки». Сундука нет — добытое остаётся у духа (передачи дух→дух).
    private static TaskNode gatherMaterials(TicketTarget target) {
        String item = target.supplyItem() == null ? DEFAULT_SUPPLY_ITEM : target.supplyItem();
        int count = target.supplyCount() > 0 ? target.supplyCount() : 1;
        int batch = Math.min(count, CatalogLimits.COUNT_MAX);
        return sequence(
                gatherOnly(target),
                gotoTarget(target),
                new FallbackNode(List.of(
                        new ScriptCall(
                                "deposit",
                                Map.of(CONTAINER, NEAREST, ITEM, item, COUNT, batch, RADIUS, SUPPLY_CONTAINER_RADIUS)),
                        new ScriptCall("wait", Map.of("ticks", CatalogLimits.WAIT_TICKS_MIN)))),
                store(target, LEFTOVERS.toArray(String[]::new)));
    }

    private static TaskNode gatherOnly(TicketTarget target) {
        String item = target.supplyItem() == null ? DEFAULT_SUPPLY_ITEM : target.supplyItem();
        int count = target.supplyCount() > 0 ? target.supplyCount() : 1;
        int batch = Math.min(count, CatalogLimits.COUNT_MAX);
        if (TorchRules.COAL.equals(item)) {
            // Уголь — из жилы (шахта у дома): acquire искал уголь через рецепты и уходил в древесный (журнал 05:49).
            return sequence(
                    gotoTarget(target),
                    new ScriptCall(
                            "mine_vein",
                            Map.of(
                                    "ore",
                                    OreKind.COAL.name().toLowerCase(Locale.ROOT),
                                    COUNT,
                                    batch,
                                    RADIUS,
                                    CatalogLimits.HARVEST_RADIUS_MAX)));
        }
        if (COBBLESTONE.equals(item)) {
            // Камень у дома бывает дальше 16 (выход скалы в стороне): acquire искал в 16 и проваливал
            // «подходящих блоков не осталось», печь стояла (приёмка «джунгли»). Как уголь — радиус 32.
            return sequence(
                    gotoTarget(target),
                    new ScriptCall(
                            "gather", Map.of(BLOCK, "stone", COUNT, batch, RADIUS, CatalogLimits.HARVEST_RADIUS_MAX)));
        }
        // fresh: добыть ещё, а не взять из сундука, куда добыча же сдаёт (запас не рос).
        ScriptCall acquire = new ScriptCall("acquire", Map.of(ITEM, item, COUNT, batch, "fresh", true));
        Optional<TreeSpecies> tree = treeOf(item);
        if (tree.isPresent()) {
            // Брёвна — рубкой деревьев (INC-64): chop_tree выбирает дерево с доступной стоянкой и пропускает
            // недостижимое; acquire (gather) трижды бился в одно недостижимое бревно и уходил в BLOCKED. Деревьев — с
            // запасом на малые; нет деревьев рядом — запасной путь acquire.
            int trees = Math.min(CatalogLimits.COUNT_MAX, (batch + LOGS_PER_TREE - 1) / LOGS_PER_TREE);
            return sequence(
                    gotoTarget(target),
                    new FallbackNode(List.of(
                            new ScriptCall(
                                    "chop_tree",
                                    Map.of(
                                            "species",
                                            tree.get().id(),
                                            COUNT,
                                            trees,
                                            RADIUS,
                                            CatalogLimits.CHOP_RADIUS_MAX,
                                            "replant",
                                            true)),
                            acquire)));
        }
        return sequence(gotoTarget(target), acquire);
    }

    private static Optional<TreeSpecies> treeOf(String item) {
        String full = item.startsWith("minecraft:") ? item : "minecraft:" + item;
        return TreeSpecies.ALL.stream()
                .filter(tree -> tree.logId().equals(full))
                .findFirst();
    }

    // count задания расчистки — номер четверти (1–4) для отдельного духа; 0 — вся расчистка одним заданием.
    private static TaskNode clearGrounds(TicketTarget target) {
        int radius = GROUNDS_CLEAR_RADIUS;
        List<TaskNode> chunks = List.of(
                clearGroundsChunk(target, -radius, -radius, -1, -1),
                clearGroundsChunk(target, 0, -radius, radius, -1),
                clearGroundsChunk(target, -radius, 0, -1, radius),
                clearGroundsChunk(target, 0, 0, radius, radius));
        int quarter = target.count();
        boolean single = quarter >= 1 && quarter <= CLEAR_QUARTERS;
        List<TaskNode> steps = new ArrayList<>(single ? List.of(chunks.get(quarter - 1)) : chunks);
        // Выравнивание участка к средней высоте (решение 16, 54b п.3): сначала срезка выступов (её булыжник и
        // земля — запас подсыпки), потом подсыпка ям. Выравнивание делится по четвертям, как расчистка: всё оно шло
        // в первой четверти одним духом, двое стояли (INC-64).
        PlotLeveling leveling = target.leveling() == null ? PlotLeveling.FLAT : target.leveling();
        BlockCoord center = target.target();
        for (PlotLeveling.Box box : leveling.cut()) {
            if (!single || quarterOf(box, center) == quarter) {
                steps.add(new ScriptCall("clear_area", boxParams(box, Map.of())));
            }
        }
        List<PlotLeveling.Box> fills = leveling.fill().stream()
                .filter(box -> !single || quarterOf(box, center) == quarter)
                .toList();
        for (PlotLeveling.Box layer : layers(fills)) {
            steps.add(new ScriptCall("fill", boxParams(layer, Map.of(BLOCK, leveling.block()))));
        }
        // Пустоты под участком (до VOID_DEPTH под уровнем) — засыпка без провала четверти: закрытый со всех сторон
        // карман духу не достать, это не повод валить выравнивание (INC-67).
        List<PlotLeveling.Box> voids = leveling.voids().stream()
                .filter(box -> !single || quarterOf(box, center) == quarter)
                .toList();
        for (PlotLeveling.Box layer : layers(voids)) {
            steps.add(new FallbackNode(List.of(
                    new ScriptCall("fill", boxParams(layer, Map.of(BLOCK, leveling.block()))),
                    new ScriptCall("wait", Map.of("ticks", CatalogLimits.WAIT_TICKS_MIN)))));
        }
        return withLeftovers(new SequenceNode(steps), target);
    }

    // Подсыпка — слоями снизу вверх по всей яме, а не рядами на всю глубину (INC-66): засыпав свой ряд, дух поднимался
    // на край, а соседний ряд оставался траншеей, дна которой с края не достать. В слое ряды с одинаковым x сливаются.
    static List<PlotLeveling.Box> layers(List<PlotLeveling.Box> fills) {
        List<PlotLeveling.Box> out = new ArrayList<>();
        int bottom = fills.stream().mapToInt(PlotLeveling.Box::y1).min().orElse(0);
        int top = fills.stream().mapToInt(PlotLeveling.Box::y2).max().orElse(-1);
        for (int y = bottom; y <= top; y++) {
            int level = y;
            List<PlotLeveling.Box> rows = fills.stream()
                    .filter(box -> box.y1() <= level && level <= box.y2())
                    .sorted(Comparator.comparingInt(PlotLeveling.Box::x1)
                            .thenComparingInt(PlotLeveling.Box::x2)
                            .thenComparingInt(PlotLeveling.Box::z1))
                    .toList();
            PlotLeveling.Box open = null;
            for (PlotLeveling.Box row : rows) {
                if (open != null && open.x1() == row.x1() && open.x2() == row.x2() && open.z2() + 1 == row.z1()) {
                    open = new PlotLeveling.Box(open.x1(), y, open.z1(), open.x2(), y, row.z2());
                    continue;
                }
                if (open != null) {
                    out.add(open);
                }
                open = new PlotLeveling.Box(row.x1(), y, row.z1(), row.x2(), y, row.z2());
            }
            if (open != null) {
                out.add(open);
            }
        }
        return out;
    }

    // Четверть ящика выравнивания по его началу — те же четверти, что у расчистки (1 — запад-север … 4 — восток-юг).
    static int quarterOf(PlotLeveling.Box box, BlockCoord center) {
        boolean east = box.x1() >= center.x();
        boolean south = box.z1() >= center.z();
        return 1 + (east ? 1 : 0) + (south ? 2 : 0);
    }

    private static Map<String, Object> boxParams(PlotLeveling.Box box, Map<String, Object> extra) {
        Map<String, Object> params = new LinkedHashMap<>(extra);
        params.put("x1", box.x1());
        params.put("y1", box.y1());
        params.put("z1", box.z1());
        params.put("x2", box.x2());
        params.put("y2", box.y2());
        params.put("z2", box.z2());
        return Map.copyOf(params);
    }

    private static ScriptCall clearGroundsChunk(TicketTarget target, int dx1, int dz1, int dx2, int dz2) {
        return new ScriptCall(
                "clear_area",
                Map.of(
                        "x1",
                        target.target().x() + dx1,
                        "y1",
                        target.target().y(),
                        "z1",
                        target.target().z() + dz1,
                        "x2",
                        target.target().x() + dx2,
                        "y2",
                        target.target().y() + 3,
                        "z2",
                        target.target().z() + dz2));
    }

    // Схема дома — по уровню из таблицы этапов (board/stages.json «schemes»): уровень 1 — хижина из дерева рядом,
    // уровень 2 — каменный дом (решение 54b п.1); уровня без строки — каменный дом, как раньше.
    private static TaskNode buildHouse(int level, TicketTarget target) {
        if (target.schemeFile() != null) {
            return sequence(gotoTarget(target), schematic(target.schemeFile(), target));
        }
        Optional<String> staged = DataTables.INSTANCE.stages().current().scheme(BoardCase.BUILD_HOUSE, level);
        if (staged.isEmpty()) {
            return sequence(gotoTarget(target), schematic(DEFAULT_SCHEME, target));
        }
        // Дом из таблицы этапов — в центре участка (SettlementLayout): точка задания — якорь участка.
        return sequence(
                gotoTarget(target),
                schematic(staged.orElseThrow(), SettlementLayout.houseOrigin(target.target()), target));
    }

    private static TaskNode repairBuilding(TicketTarget target) {
        String scheme = target.schemeFile() == null ? "repair" : target.schemeFile();
        return sequence(gotoTarget(target), schematic(scheme, target));
    }

    private static TaskNode repairTool(TicketTarget target) {
        if (target.craftItem() == null) {
            throw new IllegalArgumentException("Задание ремонта инструмента без предмета: скрипт repair_tool "
                    + "чинит конкретный изношенный предмет, без имени чинить нечего");
        }
        // Наковальню скрипт ищет вокруг себя: сначала к дому, где она стоит (гейт RepairWatch), иначе дух в шахте
        // или в поле проваливает ремонт «нет наковальни поблизости» (INC-24).
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
                        "build_schematic",
                        Map.of(
                                "file",
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

    // Оставленная руда: дух встаёт там, откуда её видел строитель шахты, и добывает её жилу нужной киркой
    // (кирку с Удачей или Шёлковым касанием выбирает ломка — MineOres).
    private static TaskNode mineLeftOre(TicketTarget target) {
        String ore = target.supplyItem();
        OreKind found = ore == null ? OreKind.ANY : OreKind.ofBlock(ore).orElse(OreKind.ANY);
        String kind = found.name().toLowerCase(Locale.ROOT);
        return sequence(
                gotoTarget(target),
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
        // Со склада: сначала к месту задания (сундуки у дома), затем withdraw рядом — получатель брал «ближайший»
        // вокруг себя и падал «рядом нет допустимого контейнера» (приёмка «джунгли» 24.10). Сундук успели опустошить
        // (добыча берёт из сундука сама) — передача не валится: провал ставил кейс передач духа на паузу, и все его
        // передачи ждали; крафт закажет недостающее заново по сундуку.
        return sequence(
                gotoTarget(target),
                new FallbackNode(List.of(
                        new ScriptCall(
                                "withdraw",
                                Map.of(
                                        CONTAINER,
                                        "auto",
                                        ITEM,
                                        target.supplyItem(),
                                        COUNT,
                                        count,
                                        RADIUS,
                                        CatalogLimits.CONTAINER_RADIUS_DEFAULT)),
                        new ScriptCall("wait", Map.of("ticks", CatalogLimits.WAIT_TICKS_MIN)))));
    }

    private static TaskNode restockFood(TicketTarget target) {
        if (target.supplyItem() == null) {
            throw new IllegalArgumentException("Пополнение еды без supplyItem: со склада забирают "
                    + "конкретный продукт, без имени предмета withdraw нечего взять");
        }
        return new ScriptCall(
                "withdraw",
                Map.of(
                        CONTAINER,
                        NEAREST,
                        ITEM,
                        target.supplyItem(),
                        COUNT,
                        RESTOCK_FOOD_COUNT,
                        RADIUS,
                        CatalogLimits.CONTAINER_RADIUS_DEFAULT));
    }

    private static ScriptCall schematic(String scheme, TicketTarget target) {
        return schematic(scheme, target.target(), target);
    }

    private static ScriptCall schematic(String scheme, BlockCoord origin, TicketTarget target) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("file", scheme);
        params.put("x", origin.x());
        params.put("y", origin.y());
        params.put("z", origin.z());
        if (target.wood() != null) {
            params.put(BuildSchematicParams.WOOD, target.wood());
        }
        return new ScriptCall("build_schematic", Map.copyOf(params));
    }

    private static TaskNode fillArea(TicketTarget target, String block, int radius) {
        return new ScriptCall(
                "fill",
                Map.of(
                        BLOCK,
                        block,
                        "x1",
                        target.target().x() - radius,
                        "y1",
                        target.target().y(),
                        "z1",
                        target.target().z() - radius,
                        "x2",
                        target.target().x() + radius,
                        "y2",
                        target.target().y(),
                        "z2",
                        target.target().z() + radius));
    }

    private static ScriptCall gotoTarget(TicketTarget target) {
        return gotoPoint(target.target());
    }

    private static ScriptCall gotoPoint(BlockCoord point) {
        return new ScriptCall("goto", Map.of("x", point.x(), "y", point.y(), "z", point.z()));
    }

    private static ScriptCall demolishBox(BlockCoord center, int radius) {
        return new ScriptCall(
                "clear_area",
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
