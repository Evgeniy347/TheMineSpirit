package com.theminespirit.core.planner;

import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class SourcePlans {
    private static final String COUNT = "count";

    private SourcePlans() {}

    static @Nullable TaskNode source(ResourceKnowledge knowledge, String item, int count) {
        return knowledge.sourceScript(item).map(script -> plan(script, count)).orElse(null);
    }

    static TaskNode plan(ResourceKnowledge.SourceScript source, int count) {
        if (count <= source.maxCount()) {
            return call(source, count);
        }
        List<TaskNode> batches = new ArrayList<>();
        int left = count;
        while (left > 0) {
            int batch = Math.min(left, source.maxCount());
            batches.add(call(source, batch));
            left -= batch;
        }
        return new SequenceNode(batches);
    }

    private static ScriptCall call(ResourceKnowledge.SourceScript source, int count) {
        Map<String, Object> params = new HashMap<>(source.params());
        params.put(COUNT, count);
        return new ScriptCall(source.script(), Map.copyOf(params));
    }
}
