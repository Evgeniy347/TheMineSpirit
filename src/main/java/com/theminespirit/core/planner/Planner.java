package com.theminespirit.core.planner;

import com.theminespirit.core.observation.WorldObservation;

@FunctionalInterface
public interface Planner {
    PlanResult plan(Goal goal, WorldObservation observation);
}
