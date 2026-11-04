package com.theminespirit.core.board;

import com.theminespirit.core.building.SchematicActions;
import com.theminespirit.core.mining.OreKind;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CatalogLimits;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class TicketStoragePlans {
    private static final String CONTAINER = "container";
    private static final String ITEM = "item";
    private static final String COUNT = "count";
    private static final String RADIUS = "radius";
    private static final String NEAREST = "nearest";
    private static final String WAREHOUSE = "warehouse";
    private static final String ACQUIRE = "acquire";
    private static final String WITHDRAW = "withdraw";
    private static final String WAIT = "wait";
    private static final String TICKS = "ticks";
    private static final String BLOCK = "block";
    private static final String COAL = "coal";
    private static final String COBBLESTONE = "cobblestone";
    private static final String DEFAULT_SUPPLY_ITEM = "dirt";
    private static final int DEFAULT_SUPPLY_COUNT = 64;
    private static final int SUPPLY_CONTAINER_RADIUS = 8;
    private static final int RESTOCK_FOOD_COUNT = 48;

    private TicketStoragePlans() {}

    static TaskNode supplySite(TicketTarget target) {
        if (ChestMove.applies(target)) {
            return ChestMove.plan(target);
        }
        String item = target.supplyItem() == null ? DEFAULT_SUPPLY_ITEM : target.supplyItem();
        int count = target.supplyCount() > 0 ? target.supplyCount() : DEFAULT_SUPPLY_COUNT;
        int batch = Math.min(count, CatalogLimits.COUNT_MAX);
        BlockCoord deposit = target.deposit() == null ? target.target() : target.deposit();
        return new SequenceNode(List.of(
                TicketPlans.gotoTarget(target),
                new ScriptCall(ACQUIRE, Map.of(ITEM, item, COUNT, batch)),
                gotoPoint(deposit),
                new ScriptCall(
                        "deposit",
                        Map.of(CONTAINER, WAREHOUSE, ITEM, item, COUNT, batch, RADIUS, SUPPLY_CONTAINER_RADIUS))));
    }

    static TaskNode gatherMaterials(TicketTarget target) {
        if (target.craftItem() != null && !target.craftItem().isBlank()) {
            return ToolCraftPlan.ofItem(target.craftItem(), target);
        }
        String item = target.supplyItem() == null ? DEFAULT_SUPPLY_ITEM : target.supplyItem();
        int count = target.supplyCount() > 0 ? target.supplyCount() : 1;
        int batch = Math.min(count, CatalogLimits.COUNT_MAX);
        if (SchematicActions.isActionPlaced(item)) {
            return gatherOnly(target);
        }
        BlockCoord deposit = target.deposit();
        String container = "auto";
        return new SequenceNode(List.of(
                gatherOnly(target),
                deposit == null ? TicketPlans.gotoTarget(target) : gotoPoint(deposit),
                new FallbackNode(List.of(
                        new ScriptCall(
                                "deposit",
                                Map.of(
                                        CONTAINER,
                                        container,
                                        ITEM,
                                        item,
                                        COUNT,
                                        batch,
                                        RADIUS,
                                        SUPPLY_CONTAINER_RADIUS)),
                        new ScriptCall(WAIT, Map.of(TICKS, CatalogLimits.WAIT_TICKS_MIN)))),
                TicketPlans.store(target, TicketPlans.LEFTOVERS.toArray(String[]::new))));
    }

    private static TaskNode gatherOnly(TicketTarget target) {
        String item = target.supplyItem() == null ? DEFAULT_SUPPLY_ITEM : target.supplyItem();
        int count = target.supplyCount() > 0 ? target.supplyCount() : 1;
        int batch = Math.min(count, CatalogLimits.COUNT_MAX);
        if (COAL.equals(item)) {
            return new SequenceNode(List.of(
                    TicketPlans.gotoTarget(target),
                    new ScriptCall(
                            "mine_vein",
                            Map.of(
                                    "ore",
                                    OreKind.COAL.name().toLowerCase(Locale.ROOT),
                                    COUNT,
                                    batch,
                                    RADIUS,
                                    CatalogLimits.HARVEST_RADIUS_MAX))));
        }
        if (COBBLESTONE.equals(item)) {
            return new SequenceNode(List.of(
                    TicketPlans.gotoTarget(target),
                    new ScriptCall(
                            "gather", Map.of(BLOCK, "stone", COUNT, batch, RADIUS, CatalogLimits.HARVEST_RADIUS_MAX))));
        }
        Optional<TaskNode> gathered = GatherPlans.plan(target, item, batch);
        if (gathered.isPresent()) {
            return gathered.get();
        }
        ScriptCall acquire = new ScriptCall(ACQUIRE, Map.of(ITEM, item, COUNT, batch, "fresh", true));
        return new SequenceNode(List.of(TicketPlans.gotoTarget(target), acquire));
    }

    static TaskNode restockFood(TicketTarget target) {
        if (target.supplyItem() == null) {
            throw new IllegalArgumentException("Пополнение еды без supplyItem: со склада забирают "
                    + "конкретный продукт, без имени предмета withdraw нечего взять");
        }
        BlockCoord point = target.deposit();
        String container = point == null ? NEAREST : containerId(point);
        ScriptCall withdraw = new ScriptCall(
                WITHDRAW,
                Map.of(
                        CONTAINER,
                        container,
                        ITEM,
                        target.supplyItem(),
                        COUNT,
                        RESTOCK_FOOD_COUNT,
                        RADIUS,
                        CatalogLimits.CONTAINER_RADIUS_DEFAULT));
        return point == null ? withdraw : new SequenceNode(List.of(gotoPoint(point), withdraw));
    }

    private static ScriptCall gotoPoint(BlockCoord point) {
        return new ScriptCall("goto", Map.of("x", point.x(), "y", point.y(), "z", point.z()));
    }

    private static String containerId(BlockCoord point) {
        return point.x() + "," + point.y() + "," + point.z();
    }
}
