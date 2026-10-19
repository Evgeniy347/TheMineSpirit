package com.theminespirit.core.planner;

import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class RecordingPlanner implements Planner {
    private final TaskNode response;
    private final List<Goal> goals = new ArrayList<>();
    private final List<WorldObservation> observations = new ArrayList<>();

    private RecordingPlanner(TaskNode response) {
        this.response = response;
    }

    static RecordingPlanner returning(TaskNode response) {
        return new RecordingPlanner(response);
    }

    @Override
    public PlanResult plan(Goal goal, WorldObservation observation) {
        goals.add(goal);
        observations.add(observation);
        return new PlanResult(response, PlanSource.FALLBACK, Optional.empty());
    }

    boolean isEmpty() {
        return goals.isEmpty();
    }

    Goal lastGoal() {
        return goals.get(goals.size() - 1);
    }

    WorldObservation lastObservation() {
        return observations.get(observations.size() - 1);
    }
}
