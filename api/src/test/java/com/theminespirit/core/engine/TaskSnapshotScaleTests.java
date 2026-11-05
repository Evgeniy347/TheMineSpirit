package com.theminespirit.core.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.plan.ConditionNode;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ParallelNode;
import com.theminespirit.core.plan.RepeatNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TaskSnapshotScaleTests {
    private static final int LIMIT_NODES = 256;
    private static final int LIMIT_DEPTH = 16;
    private static final int LIMIT_BYTES = 64 * 1024;

    @Test
    void snapshotAndRestore_AtNodeLimit_TouchEachNodeAtMostTwice() {
        TaskNode plan = caterpillar(LIMIT_NODES, LIMIT_DEPTH);
        assertEquals(LIMIT_NODES, countNodes(plan), "план обязан упираться в предел узлов");
        assertEquals(LIMIT_DEPTH, countDepth(plan), "план обязан упираться в предел глубины");

        ScriptRegistry scripts = ScriptRegistry.of(Map.of(
                "go", params -> () -> ScriptStatus.SUCCEEDED,
                "hold", params -> () -> ScriptStatus.RUNNING));
        ConditionRegistry conditions = ConditionRegistry.of(Map.of());

        TaskRunner runner = TaskRunner.start(plan, scripts, conditions);
        for (int i = 0; i < LIMIT_DEPTH; i++) {
            assertEquals(RunStatus.RUNNING, runner.tick(), "план на пределе обязан дойти до самой глубины");
        }

        EngineCounters.reset();
        TaskSnapshot snapshot = runner.snapshot()
                .orElseThrow(() -> new AssertionError("работающая задача на пределе обязана отдавать снимок"));
        String json = TaskSnapshotCodec.encode(snapshot);
        int bytes = json.getBytes(StandardCharsets.UTF_8).length;
        assertTrue(
                bytes <= LIMIT_BYTES,
                "JSON снимка " + bytes + " байт превышает предел " + LIMIT_BYTES
                        + ": снимок хранят в NBT сущности и большие значения его раздувают");
        assertTrue(
                EngineCounters.snapshotVisits <= 2L * LIMIT_NODES,
                "обход снимка посетил " + EngineCounters.snapshotVisits + " исполнителей: каждый узел обязан "
                        + "посещаться не более двух раз, иначе снимок квадратичен по числу узлов");

        EngineCounters.reset();
        TaskRunner restored = TaskRunner.restore(TaskSnapshotCodec.decode(json), scripts, conditions);
        assertEquals(RunStatus.RUNNING, restored.status(), "восстановленная задача на пределе остаётся RUNNING");
        assertTrue(
                EngineCounters.executorCreations <= 2L * LIMIT_NODES,
                "восстановление создало " + EngineCounters.executorCreations + " исполнителей: каждый узел "
                        + "обязан создаваться не более двух раз");
    }

    private static TaskNode caterpillar(int totalNodes, int depth) {
        int tailScripts = totalNodes - (depth - 1) - (depth - 2);
        List<TaskNode> tail = new ArrayList<>();
        for (int i = 0; i < tailScripts; i++) {
            tail.add(new ScriptCall("hold", Map.of()));
        }
        TaskNode node = new SequenceNode(tail);
        for (int i = 0; i < depth - 2; i++) {
            node = new SequenceNode(List.of(new ScriptCall("go", Map.of()), node));
        }
        return node;
    }

    private static int countNodes(TaskNode node) {
        return switch (node) {
            case ScriptCall ignored -> 1;
            case SequenceNode sequence -> 1 + nodeSum(sequence.steps());
            case FallbackNode fallback -> 1 + nodeSum(fallback.steps());
            case ParallelNode parallel -> 1 + nodeSum(parallel.steps());
            case RepeatNode repeat -> 1 + countNodes(repeat.step());
            case ConditionNode condition ->
                1
                        + countNodes(condition.thenStep())
                        + (condition.elseStep() == null ? 0 : countNodes(condition.elseStep()));
        };
    }

    private static int nodeSum(List<TaskNode> steps) {
        int total = 0;
        for (TaskNode step : steps) {
            total += countNodes(step);
        }
        return total;
    }

    private static int countDepth(TaskNode node) {
        return switch (node) {
            case ScriptCall ignored -> 1;
            case SequenceNode sequence -> 1 + childDepth(sequence.steps());
            case FallbackNode fallback -> 1 + childDepth(fallback.steps());
            case ParallelNode parallel -> 1 + childDepth(parallel.steps());
            case RepeatNode repeat -> 1 + countDepth(repeat.step());
            case ConditionNode condition -> {
                int then = countDepth(condition.thenStep());
                int other = condition.elseStep() == null ? 0 : countDepth(condition.elseStep());
                yield 1 + Math.max(then, other);
            }
        };
    }

    private static int childDepth(List<TaskNode> steps) {
        int deepest = 0;
        for (TaskNode step : steps) {
            deepest = Math.max(deepest, countDepth(step));
        }
        return deepest;
    }
}
