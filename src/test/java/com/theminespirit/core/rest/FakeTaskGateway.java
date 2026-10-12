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
    private RuntimeException startFailure;
    private RuntimeException observationFailure;

    FakeTaskGateway observing(WorldObservation observation) {
        this.observation = observation;
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

    @Override
    public WorldObservation observation(String bot) {
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
        if (startFailure != null) {
            throw startFailure;
        }
        starts.add(bot);
    }

    @Override
    public void cancel(String bot, String taskId) {
        cancels.add(taskId);
    }
}
