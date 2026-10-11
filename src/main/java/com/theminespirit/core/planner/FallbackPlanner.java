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
    private static final List<Rule> RULES = List.of(
            new Rule("сруб", FallbackPlanner::chopPlan),
            new Rule("дерев", FallbackPlanner::chopPlan),
            new Rule("руд", FallbackPlanner::minePlan),
            new Rule("шахт", FallbackPlanner::minePlan),
            new Rule("следу", FallbackPlanner::followPlan),
            new Rule("за мной", FallbackPlanner::followPlan),
            new Rule("домой", FallbackPlanner::homePlan),
            new Rule("верни", FallbackPlanner::homePlan),
            new Rule("атак", FallbackPlanner::attackPlan),
            new Rule("убей", FallbackPlanner::attackPlan));

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
        TaskNode node = rule.action.build(goal);
        TaskNode validated = PlanValidator.validate(node, scripts, conditions);
        return new PlanResult(validated, PlanResult.SOURCE_FALLBACK, Optional.empty());
    }

    private static Optional<Rule> match(String text) {
        List<String> tokens = tokenize(text);
        Rule best = null;
        int bestIndex = Integer.MAX_VALUE;
        for (Rule rule : RULES) {
            int index = matchIndex(tokens, rule.keyword);
            if (index >= 0 && index < bestIndex) {
                best = rule;
                bestIndex = index;
            }
        }
        return Optional.ofNullable(best);
    }

    private static int matchIndex(List<String> tokens, String keyword) {
        List<String> parts = tokenize(keyword);
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

    private static TaskNode chopPlan(Goal goal) {
        Map<String, Object> params = goal.params();
        Map<String, Object> scan = new LinkedHashMap<>();
        scan.put("radius", params.getOrDefault("radius", 32));
        scan.put("filter", List.of("*_log"));
        Map<String, Object> chop = new LinkedHashMap<>();
        chop.put("species", params.getOrDefault("species", "any"));
        chop.put("count", params.getOrDefault("count", 1));
        return new SequenceNode(List.of(new ScriptCall("scan", scan), new ScriptCall("chop_tree", chop)));
    }

    private static TaskNode minePlan(Goal goal) {
        Map<String, Object> params = goal.params();
        Map<String, Object> scan = new LinkedHashMap<>();
        scan.put("radius", params.getOrDefault("radius", 32));
        scan.put("filter", List.of("*_ore"));
        Map<String, Object> mine = new LinkedHashMap<>();
        mine.put("ore", params.getOrDefault("ore", "any"));
        mine.put("count", params.getOrDefault("count", 1));
        return new SequenceNode(List.of(new ScriptCall("scan", scan), new ScriptCall("mine_vein", mine)));
    }

    private static TaskNode followPlan(Goal goal) {
        Map<String, Object> params = goal.params();
        Map<String, Object> follow = new LinkedHashMap<>();
        follow.put("target", params.getOrDefault("target", "player"));
        follow.put("radius", params.getOrDefault("radius", 3));
        return new ScriptCall("follow", follow);
    }

    private static TaskNode homePlan(Goal goal) {
        return new ScriptCall("return_home", Map.of());
    }

    private static TaskNode attackPlan(Goal goal) {
        return new ScriptCall("attack", Map.of("target", goal.params().getOrDefault("target", "hostile")));
    }

    private record Rule(String keyword, Action action) {
    }

    private interface Action {
        TaskNode build(Goal goal);
    }
}
