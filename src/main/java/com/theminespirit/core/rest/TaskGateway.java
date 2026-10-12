package com.theminespirit.core.rest;

import com.theminespirit.core.plan.TaskNode;
import java.util.Map;

public interface TaskGateway {
    long start(String bot, TaskNode plan);

    long startGoal(String bot, String goal, Map<String, Object> params);

    void cancelIfPresent(String bot, String taskId);
}
