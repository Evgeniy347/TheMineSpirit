package com.theminespirit.core.board;

import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CatalogLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
    private static final int MINE_DESCENT_SIZE = 3;
    private static final int GROUNDS_CLEAR_RADIUS = 16;
    private static final int CRATER_FILL_RADIUS = 1;
    private static final int DISMANTLE_RADIUS = 4;
    private static final int LIGHTING_TORCHES = 4;
    private static final int LIGHTING_OFFSET = 2;
    private static final String FENCE_SCHEME = "fence";
    private static final String ENCHANT_ROOM_SCHEME = "enchant_room";
    private static final int SUPPLY_CONTAINER_RADIUS = 8;

    private TicketPlans() {
    }

    public static TaskNode plan(BoardCase boardCase, TicketTarget target) {
        return switch (boardCase) {
            case FILL_CRATER -> fillCrater(target);
            case REPAIR_FENCE -> repairFence(target);
            case CAPTURE_ANIMAL -> captureAnimal(target);
            case BUILD_PEN -> buildPen(target);
            case LURE_ANIMAL -> lureAnimal(target);
            case BUILD_MINE -> buildMine(target);
            case SUPPLY_SITE -> supplySite(target);
            case CLEAR_GROUNDS -> clearGrounds(target);
            case BUILD_HOUSE -> buildHouse(target);
            case REPAIR_BUILDING -> repairBuilding(target);
            case STOCK_CHESTS -> craft("minecraft:chest", 2, target);
            case CRAFTING_STATION -> craft("minecraft:crafting_table", 1, target);
            case CRAFT_TOOLS -> craft("minecraft:wooden_pickaxe", 1, target);
            case BUILD_FENCE -> sequence(gotoTarget(target), schematic(FENCE_SCHEME, target));
            case LIGHTING -> lighting(target);
            case ENCHANT_ROOM -> sequence(gotoTarget(target), schematic(ENCHANT_ROOM_SCHEME, target));
            case ENCHANT_SHELVES -> craft("minecraft:bookshelf", 4, target);
            case DISMANTLE_SHELTER -> dismantleShelter(target);
            default -> throw new IllegalArgumentException("Кейс " + boardCase
                    + " не имеет плана на доске: выдача задания без плана оставила бы духа "
                    + "без задачи — новый кейс обязан прийти со своей строкой плана");
        };
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

    private static TaskNode buildPen(TicketTarget target) {
        String scheme = target.schemeFile() == null ? "pen" : target.schemeFile();
        return sequence(gotoTarget(target), new ScriptCall("build_schematic",
                Map.of("file", scheme, "x", target.target().x(), "y", target.target().y(),
                        "z", target.target().z())));
    }

    private static TaskNode lureAnimal(TicketTarget target) {
        String lure = target.lureItem() == null ? DEFAULT_LURE_ITEM : target.lureItem();
        return sequence(
                new ScriptCall("withdraw", Map.of("container", "nearest", "item", lure, "count", 1)),
                gotoTarget(target));
    }

    private static TaskNode buildMine(TicketTarget target) {
        return new ScriptCall("dig_area", Map.of("x", target.target().x(), "y", target.target().y(),
                "z", target.target().z(), "size", MINE_DESCENT_SIZE));
    }

    private static TaskNode craft(String item, int count, TicketTarget target) {
        return sequence(gotoTarget(target), new ScriptCall("craft",
                Map.of("item", item, "count", count, "radius", CatalogLimits.STATION_RADIUS_DEFAULT)));
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
        return new ScriptCall("goto", Map.of("x", target.target().x(), "y", target.target().y(),
                "z", target.target().z()));
    }

    private static TaskNode sequence(TaskNode... steps) {
        List<TaskNode> list = new ArrayList<>(steps.length);
        for (TaskNode step : steps) {
            list.add(step);
        }
        return new SequenceNode(list);
    }
}
