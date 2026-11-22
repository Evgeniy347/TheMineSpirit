package com.theminespirit.core.rest;

import com.theminespirit.core.plan.TaskNode;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public interface TaskGateway {
    long start(String bot, TaskNode plan);

    long startGoal(String bot, String goal, Map<String, Object> params);

    default long start(String bot, String taskId, @Nullable String title, TaskNode plan) {
        return start(bot, plan);
    }

    default long startGoal(String bot, String taskId, @Nullable String title, String goal, Map<String, Object> params) {
        return startGoal(bot, goal, params);
    }

    void cancelIfPresent(String bot, String taskId);
}
