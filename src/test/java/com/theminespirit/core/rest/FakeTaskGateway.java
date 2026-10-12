package com.theminespirit.core.rest;

import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Фальшивый платформенный шлюз для тестов обработчиков задач.
 */
final class FakeTaskGateway implements TaskGateway {
    private final List<String> starts = new ArrayList<>();
    private final List<String> cancels = new ArrayList<>();
    private WorldObservation observation;
    private PlanningService planner;
    private RuntimeException startFailure;
    private RuntimeException observationFailure;
    private Runnable onCancel;
    private Runnable onStart;
    private int hops;

    FakeTaskGateway observing(WorldObservation observation) {
        this.observation = observation;
        return this;
    }

    FakeTaskGateway planning(PlanningService planner) {
        this.planner = planner;
        return this;
    }

    FakeTaskGateway runsOnStart(Runnable action) {
        this.onStart = action;
        return this;
    }

    FakeTaskGateway runsOnCancel(Runnable action) {
        this.onCancel = action;
        return this;
    }

    FakeTaskGateway startFailsWith(RuntimeException failure) {
        this.startFailure = failure;
        return this;
    }

    FakeTaskGateway observationFailsWith(RuntimeException failure) {
        this.observationFailure = failure;
        return this;
    }

    List<String> starts() {
        return starts;
    }

    List<String> cancels() {
        return cancels;
    }

    int hops() {
        return hops;
    }

    public WorldObservation observation(String bot) {
        hops++;
        if (observationFailure != null) {
            throw observationFailure;
        }
        if (observation == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Дух '" + bot + "' не найден: наблюдение недоступно",
                    Map.of("id", bot));
        }
        return observation;
    }

    @Override
    public void start(String bot, TaskNode plan) {
        hops++;
        if (startFailure != null) {
            throw startFailure;
        }
        starts.add(bot);
        if (onStart != null) {
            onStart.run();
        }
    }

    @Override
    public void startGoal(String bot, String goal, Map<String, Object> params) {
        hops++;
        if (observationFailure != null) {
            throw observationFailure;
        }
        if (observation == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Дух '" + bot + "' не найден: наблюдение недоступно",
                    Map.of("id", bot));
        }
        if (planner != null) {
            planner.planGoal(goal, params, observation);
        }
        if (startFailure != null) {
            throw startFailure;
        }
        starts.add(bot);
        if (onStart != null) {
            onStart.run();
        }
    }

    @Override
    public void cancel(String bot, String taskId) {
        hops++;
        cancels.add(taskId);
        if (onCancel != null) {
            onCancel.run();
        }
    }
}
