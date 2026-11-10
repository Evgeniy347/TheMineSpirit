package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class MorePlans {
    public static final String GOTO = "goto";
    public static final String WAIT = "wait";
    public static final int WAIT_TICKS = 20;
    public static final int FARM_RADIUS = 8;
    public static final int FARM_COUNT = 4;
    public static final int CONTAINER_RADIUS = 8;
    public static final int KEEP_DISTANCE = 5;
    public static final String SAFE_MINE_MODE = "staircase";
    public static final String SAFE_MINE_DIRECTION = "south";

    private MorePlans() {}

    public static TaskNode returnHome() {
        return new ScriptCall("return_home", Map.of());
    }

    public static TaskNode meet(BlockCoord at, String target) {
        Objects.requireNonNull(target, "Цель встречи не может быть null: без неё дух не знает, к кому идти");
        return sequence(gotoPoint(at), new ScriptCall("meet", Map.of("target", target)));
    }

    public static TaskNode keepDistance(BlockCoord at, String target) {
        Objects.requireNonNull(target, "Цель дистанции не может быть null: без неё дух не знает, от кого уходить");
        return sequence(
                gotoPoint(at), new ScriptCall("keep_distance", Map.of("target", target, "distance", KEEP_DISTANCE)));
    }

    public static TaskNode patrol(BlockCoord at) {
        Objects.requireNonNull(at, "Точка патруля не может быть null: без неё духу некуда идти");
        return sequence(
                gotoPoint(at),
                new ScriptCall(
                        "patrol",
                        Map.of(
                                "points",
                                List.of(at.x() + "," + at.y() + "," + at.z()),
                                "mode",
                                "ping_pong",
                                "loops",
                                1)));
    }

    public static TaskNode safeMine(BlockCoord at) {
        Objects.requireNonNull(at, "Вход шахты не может быть null: без него дух копает где стоит");
        return sequence(
                gotoPoint(at),
                new ScriptCall("safe_mine", Map.of("mode", SAFE_MINE_MODE, "direction", SAFE_MINE_DIRECTION)));
    }

    public static TaskNode plantCrop(BlockCoord at, String crop) {
        Objects.requireNonNull(at, "Поле не может быть null: без него дух не знает, где сажать");
        Objects.requireNonNull(crop, "Культура не может быть null: без неё дух не знает, что сажать");
        return sequence(
                gotoPoint(at),
                new ScriptCall("plant_crop", Map.of("crop", crop, "radius", FARM_RADIUS, "count", FARM_COUNT)));
    }

    public static TaskNode closeDoor(BlockCoord door) {
        Objects.requireNonNull(door, "Дверь не может быть null: без неё нечего закрывать");
        return sequence(
                gotoPoint(door), new ScriptCall("close_door", Map.of("x", door.x(), "y", door.y(), "z", door.z())));
    }

    public static TaskNode cover(BlockCoord at, String block) {
        Objects.requireNonNull(at, "Место не может быть null: без него дух не знает, куда ставить");
        Objects.requireNonNull(block, "Блок не может быть null: без него дух не знает, что ставить");
        return sequence(
                gotoPoint(at),
                new ScriptCall("place_block", Map.of("block", block, "x", at.x(), "y", at.y(), "z", at.z())));
    }

    public static TaskNode clearCells(List<BlockCoord> cells) {
        Objects.requireNonNull(cells, "Клетки не могут быть null: пустой список — нечего убирать");
        List<TaskNode> steps = new ArrayList<>(cells.size());
        for (BlockCoord cell : cells) {
            steps.add(retried(new ScriptCall("clear_area", box(cell, Map.of()))));
        }
        return new SequenceNode(steps);
    }

    public static TaskNode fillCells(List<BlockCoord> cells, String block) {
        Objects.requireNonNull(cells, "Клетки не могут быть null: пустой список — нечего заливать");
        Objects.requireNonNull(block, "Блок не может быть null: без него нечем заливать");
        List<TaskNode> steps = new ArrayList<>(cells.size());
        for (BlockCoord cell : cells) {
            steps.add(retried(new ScriptCall("fill", box(cell, Map.of("block", block)))));
        }
        return new SequenceNode(steps);
    }

    public static TaskNode unload(boolean force) {
        return new ScriptCall("unload", Map.of("container", "warehouse", "force", force));
    }

    public static TaskNode withdraw(String item, int count) {
        Objects.requireNonNull(item, "Предмет не может быть null: без него нечего брать");
        return new ScriptCall(
                "withdraw",
                Map.of(
                        "container",
                        "nearest",
                        "item",
                        item,
                        "count",
                        count,
                        "radius",
                        CONTAINER_RADIUS,
                        "leftover",
                        false));
    }

    public static TaskNode scan() {
        return new ScriptCall("scan", Map.of());
    }

    public static List<BlockCoord> hazardCenters(BlockCoord... centers) {
        Objects.requireNonNull(centers, "Центры проверки опасностей не могут быть null: пусто — пустой список");
        List<BlockCoord> distinct = new ArrayList<>();
        for (BlockCoord center : centers) {
            BlockCoord known = Objects.requireNonNull(
                    center, "Центр проверки опасности не может быть null: без него нечего осматривать");
            if (!distinct.contains(known)) {
                distinct.add(known);
            }
        }
        return List.copyOf(distinct);
    }

    private static TaskNode retried(ScriptCall call) {
        return new FallbackNode(List.of(call, new ScriptCall(WAIT, Map.of("ticks", WAIT_TICKS))));
    }

    private static Map<String, Object> box(BlockCoord cell, Map<String, Object> extra) {
        Map<String, Object> params = new LinkedHashMap<>(extra);
        params.put("x1", cell.x());
        params.put("y1", cell.y());
        params.put("z1", cell.z());
        params.put("x2", cell.x());
        params.put("y2", cell.y());
        params.put("z2", cell.z());
        return Map.copyOf(params);
    }

    private static TaskNode gotoPoint(BlockCoord at) {
        return new ScriptCall(GOTO, Map.of("x", at.x(), "y", at.y(), "z", at.z()));
    }

    private static SequenceNode sequence(TaskNode... steps) {
        return new SequenceNode(List.of(steps));
    }
}
