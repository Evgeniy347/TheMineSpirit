package com.theminespirit.core.board;

import com.theminespirit.core.inventory.ContainerSnapshot;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.api.CatalogLimits;
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
    private static final String WAREHOUSE = "warehouse";

    private ChestPlans() {}

    public static TaskNode demolish(ContainerSnapshot chest, List<BlockCoord> cells) {
        return demolish(chest, cells, null);
    }

    public static TaskNode demolish(ContainerSnapshot chest, List<BlockCoord> cells, @Nullable BlockCoord deposit) {
        return demolish(chest, cells, deposit, List.of());
    }

    public static TaskNode demolish(
            ContainerSnapshot chest, List<BlockCoord> cells, @Nullable BlockCoord deposit, List<BlockCoord> covers) {
        Objects.requireNonNull(chest, "Сундук не может быть null: его содержимое выносят до слома");
        Objects.requireNonNull(cells, "Клетки сундука не могут быть null: ломать нечего");
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("У сноса сундука нет ни одной клетки: ломать нечего");
        }
        Objects.requireNonNull(covers, "Крышки сундука не могут быть null: пустые — пустой список");
        String target = deposit == null ? WAREHOUSE : coord(deposit);
        List<TaskNode> steps = new ArrayList<>();
        for (BlockCoord cover : covers) {
            steps.add(PlanSteps.clearStrict(cover));
        }
        if (!covers.isEmpty()) {
            steps.add(new ScriptCall(
                    "collect_drops", Map.of(RADIUS, CatalogLimits.DROP_RADIUS_DEFAULT, "overflow", "return_to_base")));
        }
        List<ChestClearance.Move> moves = ChestClearance.moves(chest);
        for (ChestClearance.Move move : moves) {
            for (int left = move.count(); left > 0; left -= CatalogLimits.COUNT_MAX) {
                steps.add(new ScriptCall(
                        WITHDRAW,
                        Map.of(
                                CONTAINER,
                                coord(chest.pos()),
                                ITEM,
                                move.item(),
                                COUNT,
                                Math.min(left, CatalogLimits.COUNT_MAX),
                                RADIUS,
                                CatalogLimits.CONTAINER_RADIUS_DEFAULT)));
            }
        }
        for (ChestClearance.Move move : moves) {
            for (int left = move.count(); left > 0; left -= CatalogLimits.COUNT_MAX) {
                steps.add(new ScriptCall(
                        DEPOSIT,
                        Map.of(
                                CONTAINER,
                                target,
                                ITEM,
                                move.item(),
                                COUNT,
                                Math.min(left, CatalogLimits.COUNT_MAX),
                                RADIUS,
                                CatalogLimits.CONTAINER_RADIUS_DEFAULT,
                                "leftover",
                                true)));
            }
        }
        for (BlockCoord cell : cells) {
            steps.add(new ScriptCall("unload", Map.of(CONTAINER, target, "force", false)));
            steps.add(PlanSteps.clearStrict(cell));
            steps.add(new ScriptCall(
                    "collect_drops", Map.of(RADIUS, CatalogLimits.DROP_RADIUS_DEFAULT, "overflow", "return_to_base")));
        }
        return new SequenceNode(steps);
    }

    public static TaskNode breakOnly(List<BlockCoord> cells) {
        Objects.requireNonNull(cells, "Клетки сундука не могут быть null: ломать нечего");
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("У сундука нет ни одной клетки: ломать нечего");
        }
        List<TaskNode> steps = new ArrayList<>(cells.size());
        for (BlockCoord cell : cells) {
            steps.add(MorePlans.unload(false));
            steps.add(PlanSteps.clearStrict(cell));
            steps.add(new ScriptCall(
                    "collect_drops", Map.of(RADIUS, CatalogLimits.DROP_RADIUS_DEFAULT, "overflow", "return_to_base")));
        }
        return new SequenceNode(steps);
    }

    private static String coord(BlockCoord at) {
        return at.x() + "," + at.y() + "," + at.z();
    }
}
