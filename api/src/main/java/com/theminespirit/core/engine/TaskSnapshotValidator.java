package com.theminespirit.core.engine;

import com.theminespirit.core.engine.TaskProgress.ConditionAt;
import com.theminespirit.core.engine.TaskProgress.FallbackAt;
import com.theminespirit.core.engine.TaskProgress.ParallelAt;
import com.theminespirit.core.engine.TaskProgress.RepeatAt;
import com.theminespirit.core.engine.TaskProgress.ScriptAt;
import com.theminespirit.core.engine.TaskProgress.SequenceAt;
import com.theminespirit.core.plan.ConditionNode;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ParallelNode;
import com.theminespirit.core.plan.RepeatNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class TaskSnapshotValidator {
    private TaskSnapshotValidator() {}

    static void checkAgainstPlan(TaskNode plan, TaskProgress progress) {
        switch (plan) {
            case ScriptCall call -> requireProgress(progress, ScriptAt.class, "script '" + call.script() + "'");
            case SequenceNode sequence ->
                checkStepAt(TaskSnapshotCodec.SEQUENCE, sequence.steps().size(), sequence.steps(), progress);
            case RepeatNode repeat -> checkRepeat(repeat, progress);
            case FallbackNode fallback -> checkFallback(fallback, progress);
            case ParallelNode parallel -> checkParallel(parallel, progress);
            case ConditionNode condition -> checkCondition(condition, progress);
        }
    }

    private static void checkStepAt(String owner, int size, List<TaskNode> steps, TaskProgress progress) {
        if (!(progress instanceof SequenceAt at)) {
            throw mismatch(owner, progress);
        }
        checkStepIndex(owner, at.index(), size, at.child(), steps);
    }

    private static void checkStepIndex(
            String owner, int index, int size, @Nullable TaskProgress child, List<TaskNode> steps) {
        if (index > size) {
            throw outOfRange(owner, index, size);
        }
        if (child == null) {
            return;
        }
        if (index >= size) {
            throw new SnapshotFormatException("Прогресс '" + owner + "' указывает на шаг " + index
                    + ", но у узла всего " + size + " шагов: child висит на несуществующем шаге, снимок повреждён");
        }
        checkAgainstPlan(steps.get(index), child);
    }

    private static void checkRepeat(RepeatNode repeat, TaskProgress progress) {
        if (!(progress instanceof RepeatAt at)) {
            throw mismatch(TaskSnapshotCodec.REPEAT, progress);
        }
        int size = repeat.times();
        if (at.index() > size) {
            throw outOfRange(TaskSnapshotCodec.REPEAT, at.index(), size);
        }
        if (at.child() == null) {
            return;
        }
        if (at.index() >= size) {
            throw new SnapshotFormatException("Прогресс repeat указывает на повтор " + at.index() + " из " + size
                    + ": child висит на несуществующем повторе, снимок повреждён");
        }
        checkAgainstPlan(repeat.step(), at.child());
    }

    private static void checkFallback(FallbackNode fallback, TaskProgress progress) {
        if (!(progress instanceof FallbackAt at)) {
            throw mismatch(TaskSnapshotCodec.FALLBACK, progress);
        }
        checkStepIndex(TaskSnapshotCodec.FALLBACK, at.index(), fallback.steps().size(), at.child(), fallback.steps());
    }

    private static void checkParallel(ParallelNode parallel, TaskProgress progress) {
        if (!(progress instanceof ParallelAt at)) {
            throw mismatch("parallel", progress);
        }
        int size = parallel.steps().size();
        if (at.children().size() != size) {
            throw new SnapshotFormatException(
                    "Прогресс parallel несёт " + at.children().size() + " потомков, а план — " + size
                            + ": снимок повреждён или собран другой версией плана");
        }
        for (int i = 0; i < size; i++) {
            TaskProgress child = at.children().get(i).child();
            if (child != null) {
                checkAgainstPlan(parallel.steps().get(i), child);
            }
        }
    }

    private static void checkCondition(ConditionNode condition, TaskProgress progress) {
        if (!(progress instanceof ConditionAt at)) {
            throw mismatch("condition", progress);
        }
        switch (at.branch()) {
            case UNRESOLVED -> {
                if (at.child() != null) {
                    throw new SnapshotFormatException("Прогресс condition с веткой unresolved несёт child: "
                            + "ветка ещё не выбрана, значит запущенного потомка быть не может, снимок повреждён");
                }
            }
            case THEN -> requireBranchChild("then", condition.thenStep(), at.child());
            case ELSE -> {
                TaskNode elseStep = condition.elseStep();
                if (elseStep == null) {
                    throw new SnapshotFormatException("Прогресс condition с веткой else, но у плана нет ветки else: "
                            + "снимок повреждён или собран другой версией плана");
                }
                requireBranchChild("else", elseStep, at.child());
            }
        }
    }

    private static void requireBranchChild(String branch, TaskNode step, @Nullable TaskProgress child) {
        if (child == null) {
            throw new SnapshotFormatException("Прогресс condition с веткой " + branch + " без child: выбранная ветка "
                    + "обязана нести потомка, иначе восстановление не знает, что продолжать");
        }
        checkAgainstPlan(step, child);
    }

    private static void requireProgress(TaskProgress progress, Class<? extends TaskProgress> expected, String owner) {
        if (!expected.isInstance(progress)) {
            throw mismatch(owner, progress);
        }
    }

    private static SnapshotFormatException mismatch(String owner, TaskProgress progress) {
        return new SnapshotFormatException("Прогресс не совпадает по форме с планом: узел " + owner + " ожидает другой "
                + "тип прогресса, а получен " + progress.getClass().getSimpleName()
                + ": снимок повреждён или собран другой версией плана, сверьте plan ↔ progress");
    }

    private static SnapshotFormatException outOfRange(String owner, int index, int size) {
        return new SnapshotFormatException("Прогресс '" + owner + "' ссылается на позицию " + index + " при " + size
                + " шагах плана: снимок повреждён или собран другой версией плана");
    }
}
