package com.theminespirit.core.board.graph;

import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CatalogLimits;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class ActivityPlans {
    private static final String GOTO = "goto";
    private static final String RADIUS = "radius";
    private static final String COUNT = "count";
    private static final String CROP = "crop";
    private static final String CONTAINER = "container";
    private static final String ITEM = "item";
    private static final String WITHDRAW = "withdraw";

    public record Plan(String script, TaskNode task) {
        public Plan {
            Objects.requireNonNull(script, "Имя скрипта занятия не может быть null: без него занятие не учесть");
            Objects.requireNonNull(task, "План занятия не может быть null: духу нечего исполнять");
        }
    }

    private ActivityPlans() {}

    public static Plan till(BlockCoord at) {
        return new Plan(
                "till",
                sequence(
                        gotoPoint(at),
                        new ScriptCall(
                                "till",
                                Map.of(
                                        RADIUS,
                                        CatalogLimits.FARM_RADIUS_DEFAULT,
                                        COUNT,
                                        CatalogLimits.TILL_COUNT_DEFAULT))));
    }

    public static Plan plantCrop(BlockCoord at, String crop, int count) {
        return new Plan(
                "plant_crop",
                sequence(
                        gotoPoint(at),
                        new ScriptCall(
                                "plant_crop",
                                Map.of(
                                        CROP,
                                        crop,
                                        RADIUS,
                                        CatalogLimits.FARM_RADIUS_DEFAULT,
                                        COUNT,
                                        Math.max(1, Math.min(count, CatalogLimits.COUNT_MAX))))));
    }

    public static Plan harvestCrop(BlockCoord at) {
        return new Plan(
                "harvest_crop",
                sequence(
                        gotoPoint(at),
                        new ScriptCall(
                                "harvest_crop",
                                Map.of(CROP, "any", RADIUS, CatalogLimits.FARM_RADIUS_DEFAULT, "replant", true))));
    }

    public static Plan milk(BlockCoord at, int count) {
        return new Plan(
                "milk",
                sequence(
                        gotoPoint(at),
                        new ScriptCall(
                                "milk",
                                Map.of(
                                        RADIUS,
                                        CatalogLimits.HERD_RADIUS_DEFAULT,
                                        COUNT,
                                        Math.max(1, Math.min(count, CatalogLimits.HERD_COUNT_MAX))))));
    }

    public static Plan feed(BlockCoord at, Species species, int count) {
        return new Plan(
                "feed",
                sequence(
                        gotoPoint(at),
                        new ScriptCall(
                                "feed",
                                Map.of(
                                        "species",
                                        species.name().toLowerCase(Locale.ROOT),
                                        RADIUS,
                                        CatalogLimits.HERD_RADIUS_DEFAULT,
                                        COUNT,
                                        Math.max(1, Math.min(count, CatalogLimits.HERD_COUNT_MAX)),
                                        "babiesFirst",
                                        true))));
    }

    public static Plan craft(BlockCoord at, String item, int count) {
        return new Plan(
                "craft",
                sequence(
                        gotoPoint(at),
                        new ScriptCall(
                                "craft",
                                Map.of(
                                        ITEM,
                                        item,
                                        COUNT,
                                        Math.max(1, Math.min(count, CatalogLimits.COUNT_MAX)),
                                        RADIUS,
                                        CatalogLimits.STATION_RADIUS_DEFAULT))));
    }

    public static Plan equip(BlockCoord chest, BlockCoord at, String item) {
        return new Plan(
                "equip",
                sequence(
                        gotoPoint(at),
                        new ScriptCall(
                                WITHDRAW,
                                Map.of(
                                        CONTAINER,
                                        container(chest),
                                        ITEM,
                                        item,
                                        COUNT,
                                        1,
                                        RADIUS,
                                        CatalogLimits.CONTAINER_RADIUS_DEFAULT)),
                        new ScriptCall("equip", Map.of(ITEM, item))));
    }

    public static Plan unload(BlockCoord at) {
        return new Plan(
                "unload",
                sequence(
                        gotoPoint(at),
                        new ScriptCall(
                                "unload",
                                Map.of(
                                        CONTAINER,
                                        "auto",
                                        RADIUS,
                                        CatalogLimits.CONTAINER_RADIUS_DEFAULT,
                                        "force",
                                        true))));
    }

    public static Plan sortChest(BlockCoord at) {
        return new Plan(
                "sort_chest",
                sequence(
                        gotoPoint(at),
                        new ScriptCall(
                                "sort_chest",
                                Map.of(CONTAINER, container(at), RADIUS, CatalogLimits.CONTAINER_RADIUS_DEFAULT))));
    }

    public static Plan withdraw(BlockCoord from, BlockCoord at, String item, int count) {
        return new Plan(
                WITHDRAW,
                sequence(
                        gotoPoint(at),
                        new ScriptCall(
                                WITHDRAW,
                                Map.of(
                                        CONTAINER,
                                        container(from),
                                        ITEM,
                                        item,
                                        COUNT,
                                        Math.max(1, Math.min(count, CatalogLimits.COUNT_MAX)),
                                        RADIUS,
                                        CatalogLimits.CONTAINER_RADIUS_DEFAULT))));
    }

    public static Plan moveItem(BlockCoord from, BlockCoord to, BlockCoord at, String item, int count) {
        return new Plan(
                "deposit",
                sequence(
                        gotoPoint(at),
                        new ScriptCall(
                                WITHDRAW,
                                Map.of(
                                        CONTAINER,
                                        container(from),
                                        ITEM,
                                        item,
                                        COUNT,
                                        Math.max(1, Math.min(count, CatalogLimits.COUNT_MAX)),
                                        RADIUS,
                                        CatalogLimits.CONTAINER_RADIUS_DEFAULT)),
                        gotoPoint(to),
                        new ScriptCall(
                                "deposit",
                                Map.of(
                                        CONTAINER,
                                        container(to),
                                        ITEM,
                                        item,
                                        COUNT,
                                        Math.max(1, Math.min(count, CatalogLimits.COUNT_MAX)),
                                        RADIUS,
                                        CatalogLimits.CONTAINER_RADIUS_DEFAULT,
                                        "leftover",
                                        true))));
    }

    public static Plan bridge(BlockCoord at, int length, @Nullable String block) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("x", at.x());
        params.put("y", at.y());
        params.put("z", at.z());
        params.put(
                "length", Math.max(CatalogLimits.BRIDGE_LENGTH_MIN, Math.min(length, CatalogLimits.BRIDGE_LENGTH_MAX)));
        if (block != null && !block.isBlank()) {
            params.put("block", block);
        }
        return new Plan("bridge", new ScriptCall("bridge", params));
    }

    public static Plan pillar(int height, @Nullable String block) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("height", Math.max(1, Math.min(height, CatalogLimits.COUNT_MAX)));
        params.put("down", false);
        if (block != null && !block.isBlank()) {
            params.put("block", block);
        }
        return new Plan("pillar_up", new ScriptCall("pillar_up", params));
    }

    public static Plan smelt(BlockCoord at, String item, int count) {
        return new Plan(
                "smelt",
                sequence(
                        gotoPoint(at),
                        new ScriptCall(
                                "smelt",
                                Map.of(
                                        ITEM,
                                        item,
                                        COUNT,
                                        Math.max(1, Math.min(count, CatalogLimits.COUNT_MAX)),
                                        RADIUS,
                                        CatalogLimits.STATION_RADIUS_DEFAULT))));
    }

    private static SequenceNode sequence(TaskNode... steps) {
        return new SequenceNode(List.of(steps));
    }

    private static ScriptCall gotoPoint(BlockCoord point) {
        return new ScriptCall(GOTO, Map.of("x", point.x(), "y", point.y(), "z", point.z()));
    }

    private static String container(BlockCoord point) {
        return point.x() + "," + point.y() + "," + point.z();
    }
}
