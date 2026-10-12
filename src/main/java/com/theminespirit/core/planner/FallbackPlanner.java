package com.theminespirit.core.planner;

import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.core.plan.PlanValidator;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.ScriptCatalog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class FallbackPlanner implements Planner {
    private static final String SPEAKER = "speaker";

    private static final List<Rule> RULES = List.of(
            new Rule(List.of("сруб", "дерев"), List.of(
                    new Step("scan", List.of("radius"), Map.of("filter", List.of("*_log"))),
                    new Step("chop_tree", List.of("species", "count"), Map.of()))),
            new Rule(List.of("руд", "шахт"), List.of(
                    new Step("scan", List.of("radius"), Map.of("filter", List.of("*_ore"))),
                    new Step("mine_vein", List.of("ore", "count"), Map.of()))),
            new Rule(List.of("следу", "за мной"), List.of(
                    new Step("follow", List.of("radius"), Map.of(SPEAKER, "target"), Map.of()))),
            new Rule(List.of("домой", "верни"), List.of(
                    new Step("return_home", List.of(), Map.of()))),
            new Rule(List.of("атак", "убей"), List.of(
                    new Step("attack", List.of("target"), Map.of()))));

    private final ScriptCatalog scripts;
    private final ConditionCatalog conditions;

    public FallbackPlanner() {
        this(CoreScripts.defaultCatalog(), ConditionCatalog.empty());
    }

    public FallbackPlanner(ScriptCatalog scripts, ConditionCatalog conditions) {
        this.scripts = Objects.requireNonNull(scripts, "scripts не может быть null: план валидируется по каталогу");
        this.conditions = Objects.requireNonNull(conditions,
                "conditions не может быть null: каталог проверок обязателен");
    }

    @Override
    public PlanResult plan(Goal goal, WorldObservation observation) {
        Objects.requireNonNull(goal, "goal не может быть null: без текста цели сценарий не выбрать");
        Rule rule = match(goal.text()).orElseThrow(() -> new UnplannableGoalException("Цель '" + goal.text()
                + "' не распознана: измените формулировку или передайте её ИИ-планировщику"));
        TaskNode validated = PlanValidator.validate(build(rule, goal), scripts, conditions);
        return new PlanResult(validated, PlanSource.FALLBACK, Optional.empty());
    }

    private static TaskNode build(Rule rule, Goal goal) {
        List<TaskNode> steps = new ArrayList<>(rule.steps.size());
        for (Step step : rule.steps) {
            Map<String, Object> params = new LinkedHashMap<>(step.fixed());
            for (String name : step.sameName()) {
                if (goal.params().containsKey(name)) {
                    params.put(name, goal.params().get(name));
                }
            }
            for (Map.Entry<String, String> binding : step.renamed().entrySet()) {
                if (goal.params().containsKey(binding.getKey())) {
                    params.put(binding.getValue(), goal.params().get(binding.getKey()));
                }
            }
            steps.add(new ScriptCall(step.script(), params));
        }
        return steps.size() == 1 ? steps.get(0) : new SequenceNode(steps);
    }

    public static boolean requiresPlayerSource(String text) {
        return match(text)
                .map(rule -> rule.steps().stream().anyMatch(step -> step.renamed().containsKey(SPEAKER)))
                .orElse(false);
    }

    public static boolean requiresLogEvidence(String text) {
        return match(text)
                .map(rule -> rule.steps().stream().anyMatch(step -> step.script().equals("chop_tree")))
                .orElse(false);
    }

    public static boolean requiresOreEvidence(String text) {
        return match(text)
                .map(rule -> rule.steps().stream().anyMatch(step -> step.script().equals("mine_vein")))
                .orElse(false);
    }

    private static Optional<Rule> match(String text) {
        List<String> tokens = tokenize(text);
        Rule best = null;
        int bestIndex = Integer.MAX_VALUE;
        for (Rule rule : RULES) {
            int index = firstIndex(tokens, rule.stems);
            if (index >= 0 && index < bestIndex) {
                best = rule;
                bestIndex = index;
            }
        }
        return Optional.ofNullable(best);
    }

    private static int firstIndex(List<String> tokens, List<String> stems) {
        int best = -1;
        for (String stem : stems) {
            int index = matchIndex(tokens, stem);
            if (index >= 0 && (best < 0 || index < best)) {
                best = index;
            }
        }
        return best;
    }

    private static int matchIndex(List<String> tokens, String stem) {
        List<String> parts = tokenize(stem);
        if (parts.isEmpty()) {
            return -1;
        }
        for (int start = 0; start + parts.size() <= tokens.size(); start++) {
            if (startsWithAt(tokens, start, parts)) {
                return start;
            }
        }
        return -1;
    }

    private static boolean startsWithAt(List<String> tokens, int start, List<String> parts) {
        for (int offset = 0; offset < parts.size(); offset++) {
            if (!tokens.get(start + offset).startsWith(parts.get(offset))) {
                return false;
            }
        }
        return true;
    }

    private static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        for (String token : text.strip().toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private record Rule(List<String> stems, List<Step> steps) {
    }

    private record Step(String script, List<String> sameName, Map<String, String> renamed, Map<String, Object> fixed) {
        private Step(String script, List<String> sameName, Map<String, Object> fixed) {
            this(script, sameName, Map.of(), fixed);
        }
    }
}
