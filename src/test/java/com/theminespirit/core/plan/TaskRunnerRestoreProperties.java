package com.theminespirit.core.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.engine.ConditionEvaluator;
import com.theminespirit.core.engine.ConditionRegistry;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptFactory;
import com.theminespirit.core.engine.ScriptInstance;
import com.theminespirit.core.engine.ScriptRegistry;
import com.theminespirit.core.engine.ScriptStatus;
import com.theminespirit.core.engine.TaskRunner;
import com.theminespirit.core.engine.TaskSnapshot;
import com.theminespirit.core.engine.TaskSnapshotCodec;
import com.theminespirit.core.script.ScriptSpec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class TaskRunnerRestoreProperties {
    private static final int TICKS_PER_SCRIPT = 2;
    private static final int MAX_TICKS = 100_000;
    private static final String FAIL_TEXT = "fail";

    @Property
    void restore_AnyRunningSnapshot_ContinuesExactlyLikeUninterruptedRun(
            @ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        Random random = new Random(seed);
        TaskNode plan = RandomPlans.valid(random);
        Assume.that(!containsAny(plan));

        List<String> continuous = new ArrayList<>();
        Outcome continuousOutcome = runToTerminal(plan, continuous, MAX_TICKS);
        int totalTicks = continuousOutcome.ticks();
        Assume.that(totalTicks > 0);
        int cut = random.nextInt(totalTicks);

        List<String> restarted = new ArrayList<>();
        TaskRunner runner = TaskRunner.start(plan, registryFor(restarted), conditions());
        for (int i = 0; i < cut; i++) {
            runner.tick();
        }
        TaskSnapshot snapshot = runner.snapshot()
                .orElseThrow(() -> new AssertionError("работающая задача обязана отдавать снимок для сохранения"));
        TaskSnapshot decoded = TaskSnapshotCodec.decode(TaskSnapshotCodec.encode(snapshot));
        TaskRunner resumed = TaskRunner.restore(decoded, registryFor(restarted), conditions());
        RunStatus restartedStatus = runToTerminal(resumed, MAX_TICKS).status();

        // Сравниваем в порядке выполнения, без сортировки: переставленные или повторённые шаги обязаны быть видны.
        // Исход задаёт план (say с текстом "fail" проваливается, limit=0 у проверки — ложь), поэтому список
        // завершённых скриптов непрерывного и перезапущенного прогона обязан совпадать поэлементно.
        assertEquals(
                continuous,
                restarted,
                "перезапуск с тика " + cut + " обязан дать тот же порядок завершённых скриптов, что и непрерывный "
                        + "прогон: пропуск шага, повтор уже сделанного или неверная ветка fallback/else видны в списке. "
                        + "seed=" + seed + ", план=" + plan);
        assertEquals(
                continuousOutcome.status(),
                restartedStatus,
                "итоговый статус перезапущенной задачи обязан совпасть с непрерывным прогоном. seed=" + seed + ", план="
                        + plan);
    }

    private static Outcome runToTerminal(TaskNode plan, List<String> recorder, int maxTicks) {
        return runToTerminal(TaskRunner.start(plan, registryFor(recorder), conditions()), maxTicks);
    }

    private static Outcome runToTerminal(TaskRunner runner, int maxTicks) {
        for (int i = 1; i <= maxTicks; i++) {
            RunStatus status = runner.tick();
            if (status != RunStatus.RUNNING) {
                return new Outcome(status, i);
            }
        }
        throw new AssertionError("план не завершился за " + maxTicks + " тиков");
    }

    private static ScriptRegistry registryFor(List<String> recorder) {
        Map<String, ScriptFactory> factories = new LinkedHashMap<>();
        for (ScriptSpec spec : RandomPlans.SCRIPTS.all()) {
            factories.put(spec.id(), params -> new RecordingInstance(spec.id(), params, recorder));
        }
        return ScriptRegistry.of(factories);
    }

    private static ConditionRegistry conditions() {
        Map<String, ConditionEvaluator> checks = new LinkedHashMap<>();
        // Проверка ложна при limit=0: генератор выдаёт и 0, поэтому свойство проходит обе ветки condition.
        checks.put("seen", params -> {
            Object limit = params.get("limit");
            return limit instanceof Number number && number.intValue() != 0;
        });
        return ConditionRegistry.of(checks);
    }

    private static boolean containsAny(TaskNode node) {
        return switch (node) {
            case ScriptCall ignored -> false;
            case SequenceNode sequence -> anyOf(sequence.steps());
            case FallbackNode fallback -> anyOf(fallback.steps());
            case ParallelNode parallel -> parallel.mode() == ParallelMode.ANY || anyOf(parallel.steps());
            case RepeatNode repeat -> containsAny(repeat.step());
            case ConditionNode condition ->
                containsAny(condition.thenStep())
                        || (condition.elseStep() != null && containsAny(condition.elseStep()));
        };
    }

    private record Outcome(RunStatus status, int ticks) {}

    private static boolean anyOf(List<TaskNode> steps) {
        for (TaskNode step : steps) {
            if (containsAny(step)) {
                return true;
            }
        }
        return false;
    }

    private static final class RecordingInstance implements ScriptInstance {
        private final String id;
        private final List<String> recorder;
        private final boolean fails;
        private int elapsed;

        RecordingInstance(String id, Map<String, Object> params, List<String> recorder) {
            this.id = id;
            this.recorder = recorder;
            this.fails = id.equals("say") && FAIL_TEXT.equals(params.get("text"));
        }

        @Override
        public ScriptStatus tick() {
            elapsed++;
            if (elapsed < TICKS_PER_SCRIPT) {
                return ScriptStatus.RUNNING;
            }
            recorder.add(fails ? id + ":fail" : id);
            return fails ? ScriptStatus.FAILED : ScriptStatus.SUCCEEDED;
        }

        @Override
        public Map<String, Object> saveState() {
            return Map.of("elapsed", elapsed);
        }

        @Override
        public void restoreState(Map<String, Object> state) {
            Object value = state.get("elapsed");
            if (value instanceof Number number) {
                elapsed = number.intValue();
            }
        }
    }
}
