package com.theminespirit.core.board;

import com.theminespirit.core.inventory.ContainerSnapshot;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CatalogLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class ChestPlans {
    private static final String WITHDRAW = "withdraw";
    private static final String DEPOSIT = "deposit";
    private static final String CONTAINER = "container";
    private static final String ITEM = "item";
    private static final String COUNT = "count";
    private static final String RADIUS = "radius";
    private static final String NEAREST = "nearest";
    private static final String WAREHOUSE = "warehouse";
    private static final String WAIT = "wait";
    private static final String TICKS = "ticks";
    private static final String CHEST = "minecraft:chest";

    private ChestPlans() {}

    public static TaskNode placeOne(BlockCoord at, String wood) {
        Objects.requireNonNull(at, "Клетка нового сундука не может быть null: без неё некуда ставить");
        String planks = (wood == null || wood.isBlank() ? "oak" : wood) + "_planks";
        List<TaskNode> steps = new ArrayList<>();
        steps.add(new FallbackNode(List.of(
                new ScriptCall(
                        WITHDRAW,
                        Map.of(
                                CONTAINER,
                                NEAREST,
                                ITEM,
                                planks,
                                COUNT,
                                ChestClearance.CHEST_PLANKS,
                                RADIUS,
                                CatalogLimits.CONTAINER_RADIUS_DEFAULT)),
                new ScriptCall(WAIT, Map.of(TICKS, CatalogLimits.WAIT_TICKS_MIN)))));
        steps.add(new ScriptCall("craft", Map.of(ITEM, CHEST, COUNT, 1, RADIUS, CatalogLimits.STATION_RADIUS_DEFAULT)));
        steps.add(new ScriptCall("place_block", Map.of("block", CHEST, "x", at.x(), "y", at.y(), "z", at.z())));
        return new SequenceNode(steps);
    }

    public static TaskNode demolish(ContainerSnapshot chest, List<BlockCoord> cells) {
        return demolish(chest, cells, null);
    }

    public static TaskNode demolish(ContainerSnapshot chest, List<BlockCoord> cells, @Nullable BlockCoord deposit) {
        Objects.requireNonNull(chest, "Сундук не может быть null: его содержимое выносят до слома");
        Objects.requireNonNull(cells, "Клетки сундука не могут быть null: ломать нечего");
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("У сноса сундука нет ни одной клетки: ломать нечего");
        }
        String target = deposit == null ? WAREHOUSE : coord(deposit);
        List<TaskNode> steps = new ArrayList<>();
        for (ChestClearance.Move move : ChestClearance.moves(chest)) {
            for (int left = move.count(); left > 0; left -= CatalogLimits.COUNT_MAX) {
                int batch = Math.min(left, CatalogLimits.COUNT_MAX);
                steps.add(new ScriptCall(
                        WITHDRAW,
                        Map.of(
                                CONTAINER,
                                coord(chest.pos()),
                                ITEM,
                                move.item(),
                                COUNT,
                                batch,
                                RADIUS,
                                CatalogLimits.CONTAINER_RADIUS_DEFAULT)));
                steps.add(new ScriptCall(
                        DEPOSIT,
                        Map.of(
                                CONTAINER,
                                target,
                                ITEM,
                                move.item(),
                                COUNT,
                                batch,
                                RADIUS,
                                CatalogLimits.CONTAINER_RADIUS_DEFAULT,
                                "leftover",
                                true)));
            }
        }
        for (BlockCoord cell : cells) {
            steps.add(PlanSteps.clearStrict(cell));
        }
        return new SequenceNode(steps);
    }

    public static TaskNode breakOnly(List<BlockCoord> cells) {
        Objects.requireNonNull(cells, "Клетки пустого сундука не могут быть null: ломать нечего");
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("У пустого сундука нет ни одной клетки: ломать нечего");
        }
        List<TaskNode> steps = new ArrayList<>(cells.size());
        for (BlockCoord cell : cells) {
            steps.add(PlanSteps.clearStrict(cell));
        }
        return new SequenceNode(steps);
    }

    private static String coord(BlockCoord at) {
        return at.x() + "," + at.y() + "," + at.z();
    }
}
