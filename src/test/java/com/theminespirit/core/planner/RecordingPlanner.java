package com.theminespirit.core.planner;

import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.List;

final class RecordingPlanner implements Planner {
    private final TaskNode response;
    private final List<Goal> goals = new ArrayList<>();
    private final List<WorldObservation> observations = new ArrayList<>();

    static RecordingPlanner returning(TaskNode response) {
        return new RecordingPlanner(response);
    }

    private RecordingPlanner(TaskNode response) {
        this.response = response;
    }

    @Override
    public TaskNode plan(Goal goal, WorldObservation observation) {
        goals.add(goal);
        observations.add(observation);
        return response;
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