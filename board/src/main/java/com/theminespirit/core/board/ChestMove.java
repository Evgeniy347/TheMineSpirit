package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CatalogLimits;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ChestMove {
    public static final String PREFIX = "move:";
    public static final int MAX_DISTANCE = 64;

    private ChestMove() {}

    public static boolean applies(TicketTarget target) {
        return target.from() != null
                && target.supplyItem() != null
                && target.target().equals(target.to());
    }

    public static TaskNode plan(TicketTarget target) {
        BlockCoord from = Objects.requireNonNull(target.from(), "Перенос без сундука-источника");
        BlockCoord to = Objects.requireNonNull(target.to(), "Перенос без сундука двора");
        String item = Objects.requireNonNull(target.supplyItem(), "Перенос без предмета");
        refusal(from, to).ifPresent(reason -> {
            throw new IllegalArgumentException(reason);
        });
        int count = Math.max(CatalogLimits.COUNT_MIN, Math.min(target.supplyCount(), CatalogLimits.COUNT_MAX));
        return new SequenceNode(List.of(
                new ScriptCall("withdraw", Map.of("container", coord(from), "item", item, "count", count)),
                new ScriptCall("goto", Map.of("x", to.x(), "y", to.y(), "z", to.z())),
                new ScriptCall(
                        "deposit", Map.of("container", "yard", "item", item, "count", count, "leftover", true))));
    }

    public static Optional<String> refusal(BlockCoord from, BlockCoord to) {
        int distance = distance(from, to);
        if (distance <= MAX_DISTANCE) {
            return Optional.empty();
        }
        return Optional.of("Перенос из " + from + " в " + to + " — " + distance
                + " блоков при пределе " + MAX_DISTANCE
                + ": сундук-источник слишком далеко от поселения, дух не дойдёт до него через пещеры");
    }

    private static int distance(BlockCoord from, BlockCoord to) {
        int dx = from.x() - to.x();
        int dy = from.y() - to.y();
        int dz = from.z() - to.z();
        return (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    public static String id(BlockCoord from, String item) {
        return PREFIX + from.x() + "," + from.y() + "," + from.z() + ":" + item;
    }

    private static String coord(BlockCoord cell) {
        return cell.x() + "," + cell.y() + "," + cell.z();
    }
}
