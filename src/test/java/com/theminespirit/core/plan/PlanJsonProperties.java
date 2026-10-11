package com.theminespirit.core.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.ConditionSpec;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.ParamSpec;
import com.theminespirit.core.script.ScriptCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

class PlanJsonProperties {
    private static final ScriptCatalog SCRIPTS = CoreScripts.defaultCatalog();
    private static final ConditionCatalog CONDITIONS = ConditionCatalog.of(List.of(
            new ConditionSpec("seen", "проверка видимости цели", List.of(
                    ParamSpec.integer("limit", false, 0, 100, 5)))));
    private static final int MAX_DEPTH = 4;

    @Property
    void toJson_AnyValidPlan_RoundTripsThroughParser(@ForAll @IntRange(min = 0, max = 1_000_000) int seed) {
        TaskNode plan = PlanValidator.validate(randomNode(new Random(seed), MAX_DEPTH), SCRIPTS, CONDITIONS);

        TaskNode reparsed = PlanParser.parse(PlanJson.toJson(plan), SCRIPTS, CONDITIONS);

        assertEquals(plan, reparsed,
                "любой валидный план обязан переживать запись в JSON и обратный разбор без потерь: потеря шага, "
                        + "типа параметра или ветки условия после перезапуска заставит духа исполнить не тот план. "
                        + "seed=" + seed + ", план=" + plan);
    }

    private static TaskNode randomNode(Random random, int depth) {
        if (depth <= 0 || random.nextInt(5) == 0) {
            return randomScript(random);
        }
        return switch (random.nextInt(6)) {
            case 0 -> new SequenceNode(randomChildren(random, depth));
            case 1 -> new RepeatNode(1 + random.nextInt(4), randomNode(random, depth - 1));
            case 2 -> new FallbackNode(randomChildren(random, depth));
            case 3 -> new ParallelNode(random.nextBoolean() ? ParallelMode.ALL : ParallelMode.ANY,
                    randomChildren(random, depth));
            case 4 -> new ConditionNode("seen", randomConditionParams(random),
                    randomNode(random, depth - 1),
                    random.nextBoolean() ? randomNode(random, depth - 1) : null);
            default -> randomScript(random);
        };
    }

    private static List<TaskNode> randomChildren(Random random, int depth) {
        int count = 1 + random.nextInt(3);
        List<TaskNode> steps = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            steps.add(randomNode(random, depth - 1));
        }
        return steps;
    }

    private static TaskNode randomScript(Random random) {
        return switch (random.nextInt(4)) {
            case 0 -> new ScriptCall("wait", Map.of("ticks", 1 + random.nextInt(6000)));
            case 1 -> new ScriptCall("say", Map.of("text", randomText(random)));
            case 2 -> new ScriptCall("goto", Map.of(
                    "x", random.nextInt(2001) - 1000,
                    "y", random.nextInt(101),
                    "z", random.nextInt(2001) - 1000));
            default -> new ScriptCall("return_home", Map.of());
        };
    }

    private static Map<String, Object> randomConditionParams(Random random) {
        if (random.nextBoolean()) {
            return Map.of();
        }
        return Map.of("limit", random.nextInt(101));
    }

    private static String randomText(Random random) {
        String alphabet = "абвгд";
        int length = 1 + random.nextInt(6);
        StringBuilder text = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            text.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return text.toString();
    }
}
