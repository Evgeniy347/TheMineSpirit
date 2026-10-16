package com.theminespirit.core.board;

import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.Ingredient;
import com.theminespirit.core.planner.ResourceKnowledge;
import com.theminespirit.core.planner.ResourceResolver;
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

    private static final ResourceKnowledge SIMPLE_KNOWLEDGE = new ResourceKnowledge() {
        @Override
        public boolean isGatherable(String item) {
            return true;
        }

        @Override
        public boolean isCraftable(String item) {
            return false;
        }

        @Override
        public int outputCount(String item) {
            return 1;
        }

        @Override
        public List<Ingredient> ingredientsFor(String item) {
            return List.of();
        }

        @Override
        public boolean isSmeltable(String item) {
            return false;
        }

        @Override
        public String smeltInputFor(String item) {
            return item;
        }
    };

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
            default -> throw new IllegalArgumentException("Кейс " + boardCase
                    + " не имеет плана на доске: план собирается только для кейсов с точкой "
                    + "исполнения, остальные кейсы — внутренние шаги поселения");
        };
    }

    private static TaskNode fillCrater(TicketTarget target) {
        int blocks = target.craterBlocks() > 0 ? target.craterBlocks()
                : craterBlocksEstimate(BoardTuning.CRATER_BLOCKS_ESTIMATE_RADIUS);
        return sequence(
                gotoTarget(target),
                ResourceResolver.resolve("dirt", blocks,
                        target.knowledge() == null ? SIMPLE_KNOWLEDGE : target.knowledge()),
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

    private static TaskNode supplySite(TicketTarget target) {
        String item = target.supplyItem() == null ? DEFAULT_SUPPLY_ITEM : target.supplyItem();
        int count = target.supplyCount() > 0 ? target.supplyCount() : DEFAULT_SUPPLY_COUNT;
        return new ScriptCall("deliver", Map.of("item", item, "count", count, "to", "site"));
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
