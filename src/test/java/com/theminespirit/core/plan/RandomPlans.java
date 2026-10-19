package com.theminespirit.core.plan;

import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.ConditionSpec;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.ParamSpec;
import com.theminespirit.core.script.ScriptCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

final class RandomPlans {
    static final ScriptCatalog SCRIPTS = CoreScripts.defaultCatalog();
    static final ConditionCatalog CONDITIONS = ConditionCatalog.of(List.of(new ConditionSpec(
            "seen", "проверка видимости цели", List.of(ParamSpec.integer("limit", false, 0, 100, 5)))));
    static final int MAX_DEPTH = 4;

    private RandomPlans() {}

    static TaskNode valid(Random random) {
        return PlanValidator.validate(node(random, MAX_DEPTH), SCRIPTS, CONDITIONS);
    }

    static TaskNode node(Random random, int depth) {
        if (depth <= 0 || random.nextInt(5) == 0) {
            return script(random);
        }
        return switch (random.nextInt(6)) {
            case 0 -> new SequenceNode(children(random, depth));
            case 1 -> new RepeatNode(1 + random.nextInt(4), node(random, depth - 1));
            case 2 -> new FallbackNode(children(random, depth));
            case 3 ->
                new ParallelNode(random.nextBoolean() ? ParallelMode.ALL : ParallelMode.ANY, children(random, depth));
            case 4 ->
                new ConditionNode(
                        "seen",
                        conditionParams(random),
                        node(random, depth - 1),
                        random.nextBoolean() ? node(random, depth - 1) : null);
            default -> script(random);
        };
    }

    private static List<TaskNode> children(Random random, int depth) {
        int count = 1 + random.nextInt(3);
        List<TaskNode> steps = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            steps.add(node(random, depth - 1));
        }
        return steps;
    }

    private static TaskNode script(Random random) {
        return switch (random.nextInt(4)) {
            case 0 -> new ScriptCall("wait", Map.of("ticks", 1 + random.nextInt(6000)));
            case 1 -> new ScriptCall("say", Map.of("text", text(random)));
            case 2 ->
                new ScriptCall(
                        "goto",
                        Map.of(
                                "x", random.nextInt(2001) - 1000,
                                "y", random.nextInt(101),
                                "z", random.nextInt(2001) - 1000));
            default -> new ScriptCall("return_home", Map.of());
        };
    }

    private static Map<String, Object> conditionParams(Random random) {
        if (random.nextBoolean()) {
            return Map.of();
        }
        return Map.of("limit", random.nextInt(101));
    }

    private static String text(Random random) {
        // Часть сообщений — "fail": свойства с прогоном задачи трактуют его как провал шага и проверяют ветки
        // fallback/else и итог FAILED, которых иначе не бывает (R5 чеклиста 13).
        if (random.nextInt(4) == 0) {
            return "fail";
        }
        String alphabet = "абвгд";
        int length = 1 + random.nextInt(6);
        StringBuilder text = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            text.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return text.toString();
    }
}
