package com.theminespirit.core.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.BuildSchematicParams;
import com.theminespirit.core.town.StageSchemes;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class FenceStubPlan {
    private static final String BUILD_SCHEMATIC = "build_schematic";
    private static final String FILE = "file";

    private FenceStubPlan() {}

    public static TaskNode of(BlockCoord origin, int stage, @Nullable String wood) {
        Objects.requireNonNull(origin, "Точка заглушки не может быть null: без неё некуда ставить фрагмент");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(FILE, StageSchemes.fenceStub(stage));
        params.put("x", origin.x());
        params.put("y", origin.y());
        params.put("z", origin.z());
        if (wood != null) {
            params.put(BuildSchematicParams.WOOD, wood);
        }
        return new ScriptCall(BUILD_SCHEMATIC, Map.copyOf(params));
    }
}
