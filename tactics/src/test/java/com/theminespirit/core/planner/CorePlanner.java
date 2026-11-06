package com.theminespirit.core.planner;

import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.CoreScripts;
import java.util.Map;
import java.util.Set;

public final class CorePlanner {
    private CorePlanner() {}

    public static FallbackPlanner fallback() {
        return new FallbackPlanner(CoreScripts.defaultCatalog(), ConditionCatalog.empty());
    }

    public static TaskNode resolve(String item, int count, ResourceKnowledge knowledge) {
        return ResourceResolver.resolve(item, count, knowledge, CoreScripts.defaultCatalog(), ConditionCatalog.empty());
    }

    public static TaskNode resolve(
            String item, int count, ResourceKnowledge knowledge, Map<String, Integer> inventory) {
        return ResourceResolver.resolve(
                item, count, knowledge, CoreScripts.defaultCatalog(), ConditionCatalog.empty(), inventory);
    }

    public static TaskNode resolve(
            String item, int count, ResourceKnowledge knowledge, Map<String, Integer> inventory, Set<String> stations) {
        return ResourceResolver.resolve(
                item, count, knowledge, CoreScripts.defaultCatalog(), ConditionCatalog.empty(), inventory, stations);
    }
}
