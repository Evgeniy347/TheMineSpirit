package com.theminespirit.core.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Combinators;
import org.junit.jupiter.api.Test;

class TaskRegistryProperties {
    private static final int ACTIVE_KEEPERS = 5;

    @Property(tries = 100)
    void reserve_AnyCompletionOrder_SizeStaysWithinCapPlusActive(@ForAll("scenarios") Scenario scenario) {
        TaskRegistry registry = new TaskRegistry();
        List<String> activeIds = new ArrayList<>();
        for (int i = 0; i < scenario.completions(); i++) {
            String taskId = registry.reserve("bot-done-" + i, null);
            registry.observe(taskId, TaskState.SUCCEEDED, null, null);
        }
        for (int i = 0; i < ACTIVE_KEEPERS; i++) {
            String taskId = registry.reserve("bot-active-" + i, null);
            registry.observe(taskId, TaskState.RUNNING, null, null);
            activeIds.add(taskId);
        }
        for (int i = 0; i < scenario.extraCompletions(); i++) {
            String taskId = registry.reserve("bot-extra-" + i, null);
            registry.observe(taskId, TaskState.FAILED, null, "провал");
        }

        assertTrue(registry.completedCount() <= TaskRegistry.MAX_COMPLETED,
                "Завершённых обязано остаться не больше " + TaskRegistry.MAX_COMPLETED
                        + ", фактически " + registry.completedCount());
        assertTrue(registry.size() <= TaskRegistry.MAX_COMPLETED + ACTIVE_KEEPERS,
                "Размер реестра ограничен лимитом завершённых плюс активные: " + registry.size());
        for (String activeId : activeIds) {
            assertEquals(TaskState.RUNNING, registry.get(activeId).orElseThrow().state(),
                    "Активная задача " + activeId + " не должна вытесняться ни при каком порядке завершений");
        }
    }

    @Property(tries = 100)
    void reserve_AnySequence_IdsUniqueAndGrowing(@ForAll("counts") int count) {
        TaskRegistry registry = new TaskRegistry();
        int tasks = Math.floorMod(count, 300) + 1;
        Set<String> seen = new HashSet<>();
        long previous = 0;
        for (int i = 0; i < tasks; i++) {
            String bot = "bot-" + (i % 3);
            registry.activeForBot(bot).ifPresent(active -> registry.observe(active.taskId(), TaskState.SUCCEEDED,
                    null, null));
            String taskId = registry.reserve(bot, null);
            assertTrue(seen.add(taskId), "id обязан быть уникальным, повтор " + taskId);
            long numeric = Long.parseLong(taskId.substring(2));
            assertTrue(numeric > previous, "id обязан расти: " + previous + " → " + numeric);
            previous = numeric;
        }
        assertEquals(tasks, seen.size(), "Число выданных id обязано совпасть с числом reserve");
    }

    @Test
    void reserve_LikeIdempotencyEviction_CompletedCapHoldsForThousands() {
        TaskRegistry registry = new TaskRegistry();
        for (int i = 0; i < 10_000; i++) {
            String taskId = registry.reserve("bot-" + i, null);
            registry.observe(taskId, TaskState.SUCCEEDED, null, null);
        }

        assertEquals(TaskRegistry.MAX_COMPLETED, registry.completedCount(),
                "После 10 000 завершённых в реестре обязано остаться ровно " + TaskRegistry.MAX_COMPLETED);
    }

    @Provide
    Arbitrary<Scenario> scenarios() {
        return Combinators.combine(
                Arbitraries.integers().between(250, 400),
                Arbitraries.integers().between(0, 50)).as(Scenario::new);
    }

    @Provide
    Arbitrary<Integer> counts() {
        return Arbitraries.integers().between(0, 10_000);
    }

    record Scenario(int completions, int extraCompletions) {
    }
}
