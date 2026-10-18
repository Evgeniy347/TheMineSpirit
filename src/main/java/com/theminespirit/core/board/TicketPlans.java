package com.theminespirit.core.board;

import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.mining.DescentData;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CatalogLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * План задания по кейсу: чистая функция «кейс + точка → TaskNode» из скриптов
 * существующего каталога. Новых скриптов не добавляет: план собирается из
 * goto/acquire/fill/capture/build_schematic/withdraw/dig_area/clear_area/deliver.
 */
public final class TicketPlans {
    private static final String DEFAULT_SCHEME = "house";
    private static final String DEFAULT_FENCE_BLOCK = "oak_fence";
    private static final String DEFAULT_LURE_ITEM = "wheat";
    private static final String DEFAULT_SUPPLY_ITEM = "dirt";
    private static final int DEFAULT_SUPPLY_COUNT = 64;
    private static final int GROUNDS_CLEAR_RADIUS = 16;
    private static final int CRATER_FILL_RADIUS = 1;
    private static final int DISMANTLE_RADIUS = 4;
    private static final int LIGHTING_TORCHES = 4;
    private static final int LIGHTING_OFFSET = 2;
    private static final String FENCE_SCHEME = "fence";
    private static final String ENCHANT_ROOM_SCHEME = "enchant_room";
    private static final String ENCHANT_ROOM_SHELVES_SCHEME = "enchant_room_v1";
    private static final int SUPPLY_CONTAINER_RADIUS = 8;
    private static final int MINE_TUNNEL_SIZE = 5;
    private static final int MINE_STEPS_SIZE = 3;
    private static final int RESTOCK_FOOD_COUNT = 48;
    private static final int RELOCATE_CLEAR_RADIUS = 4;

    /**
     * Кейсы без плана доски: задание исполняется вне очереди планов (дух ест сам,
     * без выдачи TaskNode). Список явный: новый кейс без плана встаёт сюда, а не
     * проваливается в исключение молча.
     */
    public static final Set<BoardCase> EXTERNAL_CASES = Set.of(BoardCase.EAT_TO_FULL);

    private TicketPlans() {
    }

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
            throw new IllegalArgumentException("Уровень " + level + " кейса " + boardCase + " выше максимального "
                    + max + ": уровень постройки не существует и выдавать его нельзя");
        }
        return switch (boardCase) {
            case BUILD_MINE -> buildMineLevel(level, target);
            case BUILD_PEN -> buildPenLevel(level, target);
            case ENCHANT_ROOM -> enchantRoomLevel(level, target);
            case BUILD_HOUSE -> buildHouse(target);
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
            case STOCK_CHESTS -> craft("minecraft:chest", 2, target);
            case CRAFTING_STATION -> craft("minecraft:crafting_table", 1, target);
            case CRAFT_ANVIL -> craft("minecraft:anvil", 1, target);
            case CRAFT_SMITHING_TABLE -> craft("minecraft:smithing_table", 1, target);
            case CRAFT_SMOKER -> craft("minecraft:smoker", 1, target);
            case FISH -> fishWorkPoint(target);
            case CRAFT_WOODEN_PICKAXE, CRAFT_WOODEN_AXE, CRAFT_WOODEN_SHOVEL, CRAFT_STONE_PICKAXE,
                    CRAFT_STONE_AXE, CRAFT_STONE_SHOVEL, CRAFT_STONE_SWORD -> craftTool(boardCase, target);
            case CRAFT_SHEARS -> craft(WoolRules.CRAFTED_SHEARS, 1, target);
            case CRAFT_BED -> craft(bedItem(target), target.count() > 0 ? target.count() : 1, target);
            case WEAVE_WOOL -> craft(target.craftItem() == null ? WoolRules.CRAFTED_WOOL : target.craftItem(),
                    target.count() > 0 ? target.count() : 1, target);
            case SHEAR_SHEEP -> shearSheep(target);
            case HUNT_WILD_SHEEP -> huntWildSheep(target);
            case HARVEST_HIVE -> harvestHive();
            case PLACE_BEEHIVE -> placeBeehive(target);
            case BUILD_FENCE -> sequence(gotoTarget(target), schematic(FENCE_SCHEME, target));
            case LIGHTING -> lighting(target);
            case ENCHANT_SHELVES -> craft("minecraft:bookshelf", 4, target);
            case DISMANTLE_SHELTER -> dismantleShelter(target);
            case BREED_ANIMALS -> breedAnimals(target);
            case HERD_CULL -> herdCull(target);
            case RELOCATE_BUILDING -> relocateBuilding(target);
            case RESTOCK_FOOD -> restockFood(target);
            case TRANSFER_ITEM -> transferItem(target);
            default -> throw new IllegalArgumentException("Кейс " + boardCase
                    + " не имеет плана на доске: выдача задания без плана оставила бы духа "
                    + "без задачи — новый кейс обязан прийти со своей строкой плана "
                    + "или записью в EXTERNAL_CASES");
        };
    }

    private static TaskNode buildMineLevel(int level, TicketTarget target) {
        return switch (level) {
            case 1 -> new ScriptCall("dig_descent", Map.of("direction", "south",
                    "y", DescentData.loadDefault().targetY()));
            case 2 -> new ScriptCall("dig_area", Map.of("x", target.target().x(), "y", target.target().y(),
                    "z", target.target().z(), "size", MINE_TUNNEL_SIZE));
            case 3 -> sequence(
                    new ScriptCall("dig_area", Map.of("x", target.target().x(), "y", target.target().y(),
                            "z", target.target().z(), "size", MINE_STEPS_SIZE)),
                    new ScriptCall("fill", Map.of("block", "minecraft:cobblestone",
                            "x1", target.target().x() - MINE_STEPS_SIZE,
                            "y1", target.target().y() - 1,
                            "z1", target.target().z() - MINE_STEPS_SIZE,
                            "x2", target.target().x() + MINE_STEPS_SIZE,
                            "y2", target.target().y() - 1,
                            "z2", target.target().z() + MINE_STEPS_SIZE)));
            default -> sequence(gotoPoint(target.target()),
                    new ScriptCall("dig_area", Map.of("x", target.target().x(), "y", target.target().y(),
                            "z", target.target().z(), "size", MINE_TUNNEL_SIZE)));
        };
    }

    private static TaskNode buildPenLevel(int level, TicketTarget target) {
        String scheme = switch (level) {
            case 1 -> "pen_4x5";
            case 2 -> "pen_7x7";
            default -> "pen_9x9";
        };
        return sequence(gotoTarget(target), new ScriptCall("build_schematic",
                Map.of("file", scheme, "x", target.target().x(), "y", target.target().y(),
                        "z", target.target().z())));
    }

    private static TaskNode enchantRoomLevel(int level, TicketTarget target) {
        String scheme = level == 1 ? ENCHANT_ROOM_SCHEME : ENCHANT_ROOM_SHELVES_SCHEME;
        return sequence(gotoTarget(target), schematic(scheme, target));
    }

    private static TaskNode fillCrater(TicketTarget target) {
        int blocks = target.craterBlocks() > 0 ? target.craterBlocks()
                : craterBlocksEstimate(BoardTuning.CRATER_BLOCKS_ESTIMATE_RADIUS);
        return sequence(
                gotoTarget(target),
                new ScriptCall("acquire", Map.of("item", "dirt", "count",
                        Math.min(blocks, CatalogLimits.COUNT_MAX))),
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
        return new ScriptCall("capture", Map.of("species", species.name().toLowerCase(Locale.ROOT),
                "radius", CatalogLimits.CAPTURE_RADIUS_DEFAULT));
    }

    private static TaskNode lureAnimal(TicketTarget target) {
        String lure = target.lureItem() == null ? DEFAULT_LURE_ITEM : target.lureItem();
        return sequence(
                new ScriptCall("withdraw", Map.of("container", "nearest", "item", lure, "count", 1)),
                gotoTarget(target));
    }

    private static TaskNode craft(String item, int count, TicketTarget target) {
        return sequence(gotoTarget(target), new ScriptCall("craft",
                Map.of("item", item, "count", count, "radius", CatalogLimits.STATION_RADIUS_DEFAULT)));
    }

    /** Рыбалка на запомненной рабочей точке: подойти к берегу и ловить удочкой. */
    private static TaskNode fishWorkPoint(TicketTarget target) {
        return sequence(gotoTarget(target), new ScriptCall("fish", Map.of(
                "count", CatalogLimits.FISH_COUNT_DEFAULT,
                "duration_ticks", CatalogLimits.FISH_DURATION_DEFAULT,
                "radius", CatalogLimits.FISH_RADIUS_DEFAULT)));
    }

    /** Инструмент набора: крафтится по числу комплектов поселения, но не больше партии каталога. */
    private static TaskNode craftTool(BoardCase boardCase, TicketTarget target) {
        int count = target.toolCount() > 0 ? target.toolCount() : 1;
        return craft(ToolSet.itemId(boardCase), Math.min(count, CatalogLimits.COUNT_MAX), target);
    }

    private static TaskNode lighting(TicketTarget target) {
        int[][] offsets = {{LIGHTING_OFFSET, LIGHTING_OFFSET}, {-LIGHTING_OFFSET, LIGHTING_OFFSET},
                {LIGHTING_OFFSET, -LIGHTING_OFFSET}, {-LIGHTING_OFFSET, -LIGHTING_OFFSET}};
        int torches = Math.min(LIGHTING_TORCHES, offsets.length);
        List<TaskNode> steps = new ArrayList<>(torches + 1);
        steps.add(gotoTarget(target));
        for (int index = 0; index < torches; index++) {
            int[] offset = offsets[index];
            steps.add(new ScriptCall("place_block", Map.of("block", "minecraft:torch",
                    "x", target.target().x() + offset[0], "y", target.target().y() + 1,
                    "z", target.target().z() + offset[1])));
        }
        return new SequenceNode(steps);
    }

    private static TaskNode dismantleShelter(TicketTarget target) {
        return new ScriptCall("clear_area", Map.of(
                "x1", target.target().x() - DISMANTLE_RADIUS,
                "y1", target.target().y(),
                "z1", target.target().z() - DISMANTLE_RADIUS,
                "x2", target.target().x() + DISMANTLE_RADIUS,
                "y2", target.target().y() + 2,
                "z2", target.target().z() + DISMANTLE_RADIUS));
    }

    private static TaskNode supplySite(TicketTarget target) {
        String item = target.supplyItem() == null ? DEFAULT_SUPPLY_ITEM : target.supplyItem();
        int count = target.supplyCount() > 0 ? target.supplyCount() : DEFAULT_SUPPLY_COUNT;
        int batch = Math.min(count, CatalogLimits.COUNT_MAX);
        return sequence(gotoTarget(target),
                new ScriptCall("acquire", Map.of("item", item, "count", batch)),
                new ScriptCall("deposit", Map.of("container", "nearest", "item", item, "count", batch,
                        "radius", SUPPLY_CONTAINER_RADIUS)));
    }

    private static TaskNode collectDrops(TicketTarget target) {
        Map<String, Object> params = target.dropItems().isEmpty()
                ? Map.of("radius", CatalogLimits.DROP_RADIUS_DEFAULT)
                : Map.of("radius", CatalogLimits.DROP_RADIUS_DEFAULT, "filter", target.dropItems());
        return sequence(gotoPoint(target.target()), new ScriptCall("collect_drops", params));
    }

    private static TaskNode gatherMaterials(TicketTarget target) {
        String item = target.supplyItem() == null ? DEFAULT_SUPPLY_ITEM : target.supplyItem();
        int count = target.supplyCount() > 0 ? target.supplyCount() : 1;
        int batch = Math.min(count, CatalogLimits.COUNT_MAX);
        return sequence(gotoTarget(target),
                new ScriptCall("acquire", Map.of("item", item, "count", batch)));
    }

    private static TaskNode clearGrounds(TicketTarget target) {
        return new ScriptCall("clear_area", Map.of(
                "x1", target.target().x() - GROUNDS_CLEAR_RADIUS,
                "y1", target.target().y() - 1,
                "z1", target.target().z() - GROUNDS_CLEAR_RADIUS,
                "x2", target.target().x() + GROUNDS_CLEAR_RADIUS,
                "y2", target.target().y() + 2,
                "z2", target.target().z() + GROUNDS_CLEAR_RADIUS));
    }

    private static TaskNode buildHouse(TicketTarget target) {
        String scheme = target.schemeFile() == null ? DEFAULT_SCHEME : target.schemeFile();
        return sequence(gotoTarget(target), schematic(scheme, target));
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
        return new ScriptCall("repair_tool", Map.of("item", target.craftItem()));
    }

    private static TaskNode breedAnimals(TicketTarget target) {
        Species species = target.species() == null ? Species.COW : target.species();
        return new ScriptCall("breed", Map.of("species",
                species.name().toLowerCase(Locale.ROOT),
                "radius", CatalogLimits.HERD_RADIUS_DEFAULT,
                "pairs", CatalogLimits.HERD_PAIRS_DEFAULT));
    }

    private static TaskNode herdCull(TicketTarget target) {
        Species species = target.species() == null ? Species.COW : target.species();
        return new ScriptCall("cull", Map.of("species",
                species.name().toLowerCase(Locale.ROOT),
                "radius", CatalogLimits.HERD_RADIUS_DEFAULT));
    }

    private static TaskNode shearSheep(TicketTarget target) {
        int count = target.count() > 0 ? target.count() : WoolRules.minPenSheep();
        return sequence(gotoTarget(target), new ScriptCall("shear", Map.of(
                "radius", WoolRules.shearRadius(),
                "count", Math.min(count, CatalogLimits.HERD_COUNT_MAX))));
    }

    private static TaskNode harvestHive() {
        return new ScriptCall("harvest_hive", Map.of(
                "radius", HiveRules.hiveRadius(),
                "count", CatalogLimits.HIVE_COUNT_DEFAULT));
    }

    private static TaskNode placeBeehive(TicketTarget target) {
        BlockCoord point = target.target();
        BlockCoord cell = new BlockCoord(point.x(), point.y() + 1, point.z());
        return sequence(
                craft(HiveRules.CRAFTED_BEEHIVE, 1, target),
                new ScriptCall("place_block", Map.of("block", HiveRules.CRAFTED_BEEHIVE,
                        "x", cell.x(), "y", cell.y(), "z", cell.z())));
    }

    private static TaskNode huntWildSheep(TicketTarget target) {
        int count = target.count() > 0 ? target.count() : WoolRules.wildSheepPerDay();
        return sequence(gotoTarget(target), new ScriptCall("hunt", Map.of(
                "species", Species.SHEEP.name().toLowerCase(Locale.ROOT),
                "radius", WoolRules.wildSheepRadius(),
                "count", Math.min(count, CatalogLimits.HERD_COUNT_MAX))));
    }

    private static String bedItem(TicketTarget target) {
        if (target.craftItem() != null) {
            return target.craftItem();
        }
        return WoolRules.CRAFTED_BED;
    }

    private static TaskNode relocateBuilding(TicketTarget target) {
        if (target.from() == null || target.to() == null) {
            throw new IllegalArgumentException("Перенос без from/to: снос идёт в from, "
                    + "стройка — в to, без обеих точек план не собрать");
        }
        if (target.from().equals(target.to())) {
            throw new IllegalArgumentException("Перенос из " + target.from() + " в ту же точку: "
                    + "сносить и строить на одном месте нечего");
        }
        if (target.schemeFile() == null) {
            throw new IllegalArgumentException("Перенос без схемы: в to строится та же постройка, "
                    + "что стояла в from, без имени схемы строить нечего");
        }
        return new SequenceNode(List.of(
                gotoPoint(target.from()),
                demolishBox(target.from(), RELOCATE_CLEAR_RADIUS),
                gotoPoint(target.to()),
                new ScriptCall("build_schematic", Map.of("file", target.schemeFile(),
                        "x", target.to().x(), "y", target.to().y(), "z", target.to().z()))));
    }

    private static TaskNode transferItem(TicketTarget target) {
        if (target.transferTo() == null) {
            throw new IllegalArgumentException("Передача без получателя: deliver не знает, кому нести груз, "
                    + "а withdraw — чей инвентарь пополнять");
        }
        if (target.supplyItem() == null || target.supplyCount() <= 0) {
            throw new IllegalArgumentException("Передача без предмета или количества: "
                    + "нечего нести и нечем закрыть нехватку");
        }
        int count = Math.min(target.supplyCount(), CatalogLimits.COUNT_MAX);
        if (target.transferFrom() != null) {
            return new ScriptCall("deliver", Map.of("item", target.supplyItem(), "count", count,
                    "to", "spirit:" + target.transferTo()));
        }
        return new ScriptCall("withdraw", Map.of("container", "nearest", "item", target.supplyItem(),
                "count", count, "radius", CatalogLimits.CONTAINER_RADIUS_DEFAULT));
    }

    private static TaskNode restockFood(TicketTarget target) {
        if (target.supplyItem() == null) {
            throw new IllegalArgumentException("Пополнение еды без supplyItem: со склада забирают "
                    + "конкретный продукт, без имени предмета withdraw нечего взять");
        }
        return new ScriptCall("withdraw", Map.of("container", "nearest", "item", target.supplyItem(),
                "count", RESTOCK_FOOD_COUNT, "radius", CatalogLimits.CONTAINER_RADIUS_DEFAULT));
    }

    private static ScriptCall schematic(String scheme, TicketTarget target) {
        return new ScriptCall("build_schematic", Map.of("file", scheme, "x", target.target().x(),
                "y", target.target().y(), "z", target.target().z()));
    }

    private static TaskNode fillArea(TicketTarget target, String block, int radius) {
        return new ScriptCall("fill", Map.of(
                "block", block,
                "x1", target.target().x() - radius,
                "y1", target.target().y(),
                "z1", target.target().z() - radius,
                "x2", target.target().x() + radius,
                "y2", target.target().y(),
                "z2", target.target().z() + radius));
    }

    private static ScriptCall gotoTarget(TicketTarget target) {
        return gotoPoint(target.target());
    }

    private static ScriptCall gotoPoint(BlockCoord point) {
        return new ScriptCall("goto", Map.of("x", point.x(), "y", point.y(),
                "z", point.z()));
    }


    private static ScriptCall demolishBox(BlockCoord center, int radius) {
        return new ScriptCall("clear_area", Map.of(
                "x1", center.x() - radius,
                "y1", center.y(),
                "z1", center.z() - radius,
                "x2", center.x() + radius,
                "y2", center.y() + 2,
                "z2", center.z() + radius,
                "demolish", true));
    }

    private static TaskNode sequence(TaskNode... steps) {
        List<TaskNode> list = new ArrayList<>(steps.length);
        for (TaskNode step : steps) {
            list.add(step);
        }
        return new SequenceNode(list);
    }
}
