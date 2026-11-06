package com.theminespirit.core.board;

import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CatalogLimits;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ActionToolPlans {
    private static final String CONTAINER = "container";
    private static final String ITEM = "item";
    private static final String COUNT = "count";
    private static final String RADIUS = "radius";
    private static final String AUTO = "auto";
    private static final String WAREHOUSE = "warehouse";

    private ActionToolPlans() {}

    public static TaskNode shovel() {
        return any("*_shovel");
    }

    public static TaskNode hoe() {
        return any("*_hoe");
    }

    public static TaskNode any(String kind) {
        Objects.requireNonNull(kind, "Вид инструмента не может быть null: без него неизвестно, что искать в сундуках");
        return new FallbackNode(List.of(withdraw(AUTO, kind), withdraw(WAREHOUSE, kind)));
    }

    private static ScriptCall withdraw(String container, String kind) {
        return new ScriptCall(
                "withdraw",
                Map.of(CONTAINER, container, ITEM, kind, COUNT, 1, RADIUS, CatalogLimits.CONTAINER_RADIUS_DEFAULT));
    }
}
