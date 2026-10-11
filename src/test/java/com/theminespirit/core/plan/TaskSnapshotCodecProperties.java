package com.theminespirit.core.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.engine.TaskProgress;
import com.theminespirit.core.engine.TaskProgress.ConditionAt;
import com.theminespirit.core.engine.TaskProgress.ConditionBranch;
import com.theminespirit.core.engine.TaskProgress.FallbackAt;
import com.theminespirit.core.engine.TaskProgress.ParallelAt;
import com.theminespirit.core.engine.TaskProgress.ParallelChild;
import com.theminespirit.core.engine.TaskProgress.RepeatAt;
import com.theminespirit.core.engine.TaskProgress.ScriptAt;
import com.theminespirit.core.engine.TaskProgress.SequenceAt;
import com.theminespirit.core.engine.TaskSnapshot;
import com.theminespirit.core.engine.TaskSnapshotCodec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class TaskSnapshotCodecProperties {
    private static final List<ScriptStatus> STATUSES = List.of(ScriptStatus.values());

    @Property
    void decode_AnyEncodedSnapshot_EqualsSource(@ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        Random random = new Random(seed);
        TaskNode plan = RandomPlans.valid(random);
        TaskSnapshot snapshot = new TaskSnapshot(TaskSnapshot.VERSION, RunStatus.RUNNING,
                random.nextBoolean() ? null : "причина", plan, progress(plan, random));

        TaskSnapshot restored = TaskSnapshotCodec.decode(TaskSnapshotCodec.encode(snapshot));

        assertEquals(snapshot, restored,
                "любой снимок RUNNING обязан переживать encode→decode без потерь: расхождение означает, что часть "
                        + "прогресса уехала в JSON не полностью и после перезапуска план пойдёт иначе. "
                        + "seed=" + seed + ", план=" + plan);
    }

    private static TaskProgress progress(TaskNode plan, Random random) {
        return switch (plan) {
            case ScriptCall ignored -> new ScriptAt(state(random));
            case SequenceNode sequence -> stepped(random, sequence.steps());
            case RepeatNode repeat -> repeated(random, repeat);
            case FallbackNode fallback -> fallbackProgress(random, fallback);
            case ParallelNode parallel -> new ParallelAt(parallelChildren(parallel.steps(), random));
            case ConditionNode condition -> conditionProgress(condition, random);
        };
    }

    private static TaskProgress stepped(Random random, List<TaskNode> steps) {
        int index = chosenIndex(random, steps.size());
        TaskProgress child = index < steps.size() && random.nextBoolean()
                ? progress(steps.get(index), random)
                : null;
        return new SequenceAt(index, child);
    }

    private static TaskProgress repeated(Random random, RepeatNode repeat) {
        int index = chosenIndex(random, repeat.times());
        TaskProgress child = index < repeat.times() && random.nextBoolean()
                ? progress(repeat.step(), random)
                : null;
        return new RepeatAt(index, child);
    }

    private static TaskProgress chosenChild(Random random, List<TaskNode> steps) {
        int index = chosenIndex(random, steps.size());
        return index < steps.size() && random.nextBoolean() ? progress(steps.get(index), random) : null;
    }

    private static TaskProgress fallbackProgress(Random random, FallbackNode fallback) {
        int index = chosenIndex(random, fallback.steps().size());
        TaskProgress child = index < fallback.steps().size() && random.nextBoolean()
                ? progress(fallback.steps().get(index), random)
                : null;
        return new FallbackAt(index, random.nextBoolean() ? null : "ветка провалилась", child);
    }

    private static int chosenIndex(Random random, int size) {
        return random.nextInt(size + 1);
    }

    private static List<ParallelChild> parallelChildren(List<TaskNode> steps, Random random) {
        List<ParallelChild> children = new ArrayList<>(steps.size());
        for (TaskNode step : steps) {
            ScriptStatus status = STATUSES.get(random.nextInt(STATUSES.size()));
            if (status == ScriptStatus.RUNNING) {
                TaskProgress child = random.nextBoolean() ? progress(step, random) : null;
                children.add(new ParallelChild(status, null, child));
            } else {
                String reason = status == ScriptStatus.FAILED && random.nextBoolean() ? "провалился" : null;
                children.add(new ParallelChild(status, reason, null));
            }
        }
        return children;
    }

    private static TaskProgress conditionProgress(ConditionNode condition, Random random) {
        return switch (random.nextInt(3)) {
            case 0 -> new ConditionAt(ConditionBranch.UNRESOLVED, null);
            case 1 -> new ConditionAt(ConditionBranch.THEN, progress(condition.thenStep(), random));
            default -> condition.elseStep() == null
                    ? new ConditionAt(ConditionBranch.UNRESOLVED, null)
                    : new ConditionAt(ConditionBranch.ELSE, progress(condition.elseStep(), random));
        };
    }

    private static Map<String, Object> state(Random random) {
        List<Object> values = List.of("метка", random.nextInt(1000), random.nextBoolean(),
                List.of(random.nextInt(10), random.nextInt(10)));
        int count = 1 + random.nextInt(3);
        Map<String, Object> state = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            state.put("key" + i, values.get(random.nextInt(values.size())));
        }
        return state;
    }
}
