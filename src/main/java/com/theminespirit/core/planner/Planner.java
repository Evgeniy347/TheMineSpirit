package com.theminespirit.core.planner;

import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.core.plan.TaskNode;

public interface Planner {
    TaskNode plan(Goal goal, WorldObservation observation);
}