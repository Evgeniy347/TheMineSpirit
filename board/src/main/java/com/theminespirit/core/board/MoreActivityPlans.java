package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CatalogLimits;
import java.util.List;
import java.util.Map;

public final class MoreActivityPlans {
    private static final String BLOCK = "block";
    private static final String ACQUIRE = "acquire";
    private static final String ITEM = "item";
    private static final String COUNT = "count";
    private static final String RADIUS = "radius";
    private static final String CONTAINER = "container";
    private static final String NEAREST = "nearest";
    private static final String WAIT = "wait";
    private static final String TICKS = "ticks";

    private MoreActivityPlans() {}

    public static boolean handles(BoardCase boardCase) {
        return switch (boardCase) {
            case ACTIVITY_DEFEND,
                    ACTIVITY_SOCIAL,
                    ACTIVITY_BUILD,
                    ACTIVITY_FARM,
                    ACTIVITY_SMELT,
                    ACTIVITY_UNLOAD,
                    ACTIVITY_RETURN,
                    ACTIVITY_SENSE -> true;
            default -> false;
        };
    }

    public static TaskNode plan(BoardCase boardCase, TicketTarget target) {
        return switch (boardCase) {
            case ACTIVITY_DEFEND -> defendPlayer(target);
            case ACTIVITY_SOCIAL -> feedPlayer(target);
            case ACTIVITY_BUILD -> putBlockAt(target);
            case ACTIVITY_FARM -> fertilize(target);
            case ACTIVITY_SMELT -> refuel(target);
            case ACTIVITY_UNLOAD -> unload(target);
            case ACTIVITY_RETURN -> new ScriptCall("return_home", Map.of());
            case ACTIVITY_SENSE ->
                new ScriptCall("scan_blocks", Map.of(RADIUS, CatalogLimits.PERCEPTION_RADIUS_DEFAULT));
            default -> throw new IllegalArgumentException("Кейс " + boardCase + " не является действием области");
        };
    }

    private static TaskNode defendPlayer(TicketTarget target) {
        return sequence(gotoPoint(target.target()), new ScriptCall("defend", Map.of("target", playerSelector(target))));
    }

    private static TaskNode feedPlayer(TicketTarget target) {
        String item = requiredItem(target, "кормить игрока");
        int count = Math.max(1, Math.min(target.count(), CatalogLimits.COUNT_MAX));
        return sequence(
                new FallbackNode(List.of(
                        new ScriptCall(
                                "withdraw",
                                Map.of(
                                        CONTAINER,
                                        NEAREST,
                                        ITEM,
                                        item,
                                        COUNT,
                                        count,
                                        RADIUS,
                                        CatalogLimits.CONTAINER_RADIUS_DEFAULT)),
                        new ScriptCall(WAIT, Map.of(TICKS, CatalogLimits.WAIT_TICKS_MIN)))),
                new ScriptCall("deliver", Map.of(ITEM, item, COUNT, count, "to", playerSelector(target))));
    }

    private static TaskNode putBlockAt(TicketTarget target) {
        String item = requiredItem(target, "ставить блок");
        if ("bridge".equals(target.building())) {
            int count = Math.max(1, Math.min(target.supplyCount(), CatalogLimits.COUNT_MAX));
            return sequence(
                    new ScriptCall(ACQUIRE, Map.of(ITEM, item, COUNT, count)),
                    new ScriptCall(
                            "bridge",
                            Map.of(
                                    "x",
                                    target.target().x(),
                                    "y",
                                    target.target().y(),
                                    "z",
                                    target.target().z(),
                                    "length",
                                    8,
                                    BLOCK,
                                    item)));
        }
        return sequence(
                new ScriptCall(ACQUIRE, Map.of(ITEM, item, COUNT, 1)),
                gotoPoint(target.target()),
                new ScriptCall(
                        "place_block",
                        Map.of(
                                BLOCK,
                                item,
                                "x",
                                target.target().x(),
                                "y",
                                target.target().y(),
                                "z",
                                target.target().z())));
    }

    private static TaskNode fertilize(TicketTarget target) {
        return sequence(
                gotoPoint(target.target()),
                new ScriptCall(ACQUIRE, Map.of(ITEM, "bone_meal", COUNT, 1)),
                new ScriptCall(
                        "bone_meal",
                        Map.of(
                                "crop",
                                "any",
                                RADIUS,
                                CatalogLimits.FARM_RADIUS_DEFAULT,
                                COUNT,
                                CatalogLimits.BONE_MEAL_COUNT_DEFAULT)));
    }

    private static TaskNode refuel(TicketTarget target) {
        String item = requiredItem(target, "заправить станцию");
        int count = Math.max(1, Math.min(Math.max(target.supplyCount(), target.count()), CatalogLimits.COUNT_MAX));
        String container = target.target().x() + "," + target.target().y() + ","
                + target.target().z();
        return sequence(
                new ScriptCall(ACQUIRE, Map.of(ITEM, item, COUNT, count)),
                gotoPoint(target.target()),
                new ScriptCall(
                        "deposit",
                        Map.of(
                                CONTAINER,
                                container,
                                ITEM,
                                item,
                                COUNT,
                                count,
                                RADIUS,
                                CatalogLimits.CONTAINER_RADIUS_DEFAULT)));
    }

    private static TaskNode unload(TicketTarget target) {
        return sequence(
                gotoPoint(target.target()),
                new ScriptCall(
                        "unload",
                        Map.of(CONTAINER, "warehouse", RADIUS, CatalogLimits.CONTAINER_RADIUS_DEFAULT, "force", true)));
    }

    private static String playerSelector(TicketTarget target) {
        String selector = target.building();
        if (selector == null || !selector.startsWith("player:")) {
            throw new IllegalArgumentException("Задание не назвало игрока: цель вида player:<имя> задаёт адресата, "
                    + "иначе дух не знает, к кому идти");
        }
        return selector;
    }

    private static String requiredItem(TicketTarget target, String action) {
        String item = target.supplyItem();
        if (item == null || item.isBlank()) {
            throw new IllegalArgumentException("Задание не назвало предмет: без supplyItem нечем " + action);
        }
        return item;
    }

    private static ScriptCall gotoPoint(BlockCoord point) {
        return new ScriptCall("goto", Map.of("x", point.x(), "y", point.y(), "z", point.z()));
    }

    private static TaskNode sequence(TaskNode... steps) {
        return new SequenceNode(List.of(steps));
    }
}
