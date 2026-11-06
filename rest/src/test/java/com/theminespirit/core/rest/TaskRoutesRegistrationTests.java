package com.theminespirit.core.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.task.TaskRegistry;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TaskRoutesRegistrationTests {
    @Test
    void routes_AllFiveMethods_AreRegistered() {
        PlanningService planning = new PlanningService(CoreScripts.defaultCatalog(), ConditionCatalog.empty());
        Set<String> keys = new HashSet<>();
        new TaskRoutes(
                        new TaskRegistry(),
                        new FakeTaskGateway().planning(planning),
                        planning,
                        new IdempotencyCache(),
                        new SseSlots(),
                        new FakeBoardGateway())
                .routes()
                .forEach(route -> keys.add(route.method() + " " + route.pattern()));

        assertEquals(
                Set.of(
                        "POST /v1/bots/{id}/tasks",
                        "GET /v1/tasks/{id}",
                        "DELETE /v1/tasks/{id}",
                        "GET /v1/tasks/{id}/events",
                        "POST /v1/plan"),
                keys,
                "Чеклист п.7 обязывает ровно пять маршрутов задач");
    }
}
