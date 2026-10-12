package com.theminespirit.core.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.theminespirit.core.plan.ConditionNode;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ParallelMode;
import com.theminespirit.core.plan.ParallelNode;
import com.theminespirit.core.plan.RepeatNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class TaskRunnerProperties {
    private static final String OK = "ok";
    private static final String BAD = "bad";
    private static final String YES = "yes";
    private static final String NO = "no";
    private static final int MAX_DEPTH = 3;
    private static final int TICK_LIMIT = 100_000;

    private static final ScriptRegistry SCRIPTS = ScriptRegistry.of(Map.of(
            OK, TestScripts.succeeding(2),
            BAD, TestScripts.failing(2)));
    private static final ConditionRegistry CONDITIONS = ConditionRegistry.of(Map.of(
            YES, params -> true,
            NO, params -> false));

    @Property(tries = 200)
    void run_RandomTree_TerminatesWithoutLeakingAndEndsInTerminalStatus(
            @ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        TaskNode plan = randomTree(new Random(seed), MAX_DEPTH);
        TaskRunner runner = TaskRunner.start(plan, SCRIPTS, CONDITIONS);

        RunStatus status = null;
        int ticks = 0;
        try {
            while (ticks <= TICK_LIMIT) {
                status = runner.tick();
                ticks++;
                if (status != RunStatus.RUNNING) {
                    break;
                }
            }
        } catch (RuntimeException error) {
            fail("движок не должен бросать исключения наружу на любом плане из простейших скриптов: "
                    + "исключение уронит серверный тик. seed=" + seed + ", план=" + plan, error);
            return;
        }

        assertTrue(ticks <= TICK_LIMIT,
                "исполнение обязано завершаться за ограниченное число тиков: бесконечный прогон съест серверный тик. "
                        + "seed=" + seed + ", тиков=" + ticks + ", план=" + plan);
        assertTrue(status == RunStatus.SUCCEEDED || status == RunStatus.FAILED,
                "после завершения статус обязан быть терминальным из RunStatus: получили " + status
                        + ". seed=" + seed + ", план=" + plan);
    }

    private static TaskNode randomTree(Random random, int depth) {
        if (depth <= 0 || random.nextInt(5) == 0) {
            return new ScriptCall(random.nextBoolean() ? OK : BAD, Map.of());
        }
        return switch (random.nextInt(5)) {
            case 0 -> new SequenceNode(randomChildren(random, depth));
            case 1 -> new RepeatNode(random.nextInt(4), randomTree(random, depth - 1));
            case 2 -> new FallbackNode(randomChildren(random, depth));
            case 3 -> new ParallelNode(random.nextBoolean() ? ParallelMode.ALL : ParallelMode.ANY,
                    randomChildren(random, depth));
            default -> new ConditionNode(random.nextBoolean() ? YES : NO, Map.of(),
                    randomTree(random, depth - 1),
                    random.nextBoolean() ? randomTree(random, depth - 1) : null);
        };
    }

    private static List<TaskNode> randomChildren(Random random, int depth) {
        int count = 1 + random.nextInt(3);
        List<TaskNode> steps = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            steps.add(randomTree(random, depth - 1));
        }
        return steps;
    }
}
