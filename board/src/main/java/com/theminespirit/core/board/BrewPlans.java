package com.theminespirit.core.board;

import com.theminespirit.core.alchemy.BrewRecipes;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CatalogLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class BrewPlans {
    private static final String ITEM = "item";
    private static final String COUNT = "count";
    private static final String RADIUS = "radius";
    private static final String ACQUIRE = "acquire";
    private static final String CRAFT = "craft";
    private static final String MINECRAFT = "minecraft:";

    private BrewPlans() {}

    static TaskNode plan(BoardCase boardCase, TicketTarget target) {
        return switch (boardCase) {
            case GRIND_BLAZE_POWDER -> grind(target);
            case BREW_POTION -> brew(target);
            case HARVEST_NETHER_WART -> harvest(target);
            case CRAFT_BREWING_STAND -> stand(target);
            default -> throw new IllegalArgumentException("Кейс " + boardCase + " — не зельеварение");
        };
    }

    private static TaskNode grind(TicketTarget target) {
        int count = target.count() > 0 ? Math.min(target.count(), 2) : 2;
        int rods = (count + 1) / 2;
        return new SequenceNode(List.of(
                TicketPlans.gotoTarget(target),
                new ScriptCall(ACQUIRE, Map.of(ITEM, MINECRAFT + "blaze_rod", COUNT, rods)),
                new ScriptCall(
                        CRAFT,
                        Map.of(
                                ITEM,
                                MINECRAFT + "blaze_powder",
                                COUNT,
                                count,
                                RADIUS,
                                CatalogLimits.STATION_RADIUS_DEFAULT))));
    }

    private static TaskNode brew(TicketTarget target) {
        String product = product(target.craftItem());
        int count = target.count() > 0 ? Math.min(target.count(), CatalogLimits.COUNT_MAX) : 1;
        List<TaskNode> steps = new ArrayList<>();
        steps.add(TicketPlans.gotoTarget(target));
        BrewRecipes.needs(product, count)
                .forEach((item, need) -> steps.add(new ScriptCall(
                        ACQUIRE, Map.of(ITEM, MINECRAFT + item, COUNT, Math.min(need, CatalogLimits.COUNT_MAX)))));
        steps.add(new ScriptCall(
                "brew", Map.of(ITEM, product, COUNT, count, RADIUS, CatalogLimits.STATION_RADIUS_DEFAULT)));
        return new SequenceNode(steps);
    }

    private static TaskNode harvest(TicketTarget target) {
        int count = target.count() > 0 ? Math.min(target.count(), CatalogLimits.COUNT_MAX) : 1;
        return new SequenceNode(List.of(
                TicketPlans.gotoTarget(target),
                new ScriptCall(ACQUIRE, Map.of(ITEM, MINECRAFT + "soul_sand", COUNT, 1)),
                new ScriptCall(ACQUIRE, Map.of(ITEM, MINECRAFT + "nether_wart", COUNT, 1)),
                new ScriptCall("farm_nether_wart", Map.of(COUNT, count, RADIUS, CatalogLimits.FARM_RADIUS_DEFAULT)),
                TicketPlans.store(target, MINECRAFT + "nether_wart")));
    }

    private static TaskNode stand(TicketTarget target) {
        return TicketPlans.placeInYard(MINECRAFT + "brewing_stand", 1, target);
    }

    private static String product(@Nullable String craft) {
        return craft != null
                        && (craft.startsWith(BrewRecipes.POTION_PREFIX) || craft.startsWith(BrewRecipes.SPLASH_PREFIX))
                ? craft
                : "potion_healing";
    }
}
