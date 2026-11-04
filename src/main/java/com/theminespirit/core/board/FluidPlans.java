package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.core.script.FillBucketParams;
import com.theminespirit.core.script.FluidKind;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class FluidPlans {
    private static final String ITEM = "item";
    private static final String COUNT = "count";
    private static final String RADIUS = "radius";
    private static final String CONTAINER = "container";
    private static final String FLUID = "fluid";
    private static final String ACQUIRE = "acquire";
    private static final String FILL_BUCKET = "fill_bucket";
    private static final String POUR_BUCKET = "pour_bucket";
    private static final String DEPOSIT = "deposit";
    private static final String BUCKET = "bucket";

    private FluidPlans() {}

    public static TaskNode fetch(FluidKind fluid, BlockCoord source, BlockCoord store) {
        Objects.requireNonNull(fluid, "Жидкость не может быть null: без неё неизвестно, что нести");
        return sequence(
                acquireBucket(),
                gotoPoint(source),
                fill(fluid),
                gotoPoint(store),
                new ScriptCall(
                        DEPOSIT,
                        Map.of(
                                CONTAINER,
                                "auto",
                                ITEM,
                                fluid.bucketItem(),
                                COUNT,
                                1,
                                RADIUS,
                                CatalogLimits.CONTAINER_RADIUS_DEFAULT)));
    }

    public static TaskNode waterCrops(FluidKind fluid, BlockCoord source, BlockCoord cell) {
        return waterCrops(fluid, source, source, cell);
    }

    public static TaskNode waterCrops(FluidKind fluid, BlockCoord source, BlockCoord stand, BlockCoord cell) {
        Objects.requireNonNull(fluid, "Жидкость не может быть null: без неё неизвестно, чем поливать");
        Objects.requireNonNull(stand, "Стоянка у источника не может быть null: иначе дух полезет в воду");
        return sequence(acquireBucket(), gotoPoint(stand), fill(fluid), pour(fluid, cell));
    }

    public static TaskNode acquireBucket() {
        return new ScriptCall(ACQUIRE, Map.of(ITEM, BUCKET, COUNT, 1));
    }

    public static TaskNode pourFromBucket(FluidKind fluid, BlockCoord cell) {
        Objects.requireNonNull(fluid, "Жидкость не может быть null: без неё неизвестно, что лить");
        Objects.requireNonNull(cell, "Клетка не может быть null: без неё неизвестно, куда лить");
        return pour(fluid, cell);
    }

    private static ScriptCall fill(FluidKind fluid) {
        return new ScriptCall(FILL_BUCKET, Map.of(FLUID, fluid.id(), RADIUS, FillBucketParams.RADIUS_DEFAULT));
    }

    private static ScriptCall pour(FluidKind fluid, BlockCoord cell) {
        return new ScriptCall(POUR_BUCKET, Map.of(FLUID, fluid.id(), "x", cell.x(), "y", cell.y(), "z", cell.z()));
    }

    private static ScriptCall gotoPoint(BlockCoord point) {
        return new ScriptCall("goto", Map.of("x", point.x(), "y", point.y(), "z", point.z()));
    }

    private static TaskNode sequence(TaskNode... steps) {
        return new SequenceNode(List.of(steps));
    }
}
