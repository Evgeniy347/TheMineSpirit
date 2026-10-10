package com.theminespirit.core.planner;

import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

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

    @Override
    public TaskNode plan(Goal goal, WorldObservation observation) {
        Objects.requireNonNull(goal, "goal не может быть null: без текста цели сценарий не выбрать");
        String normalized = goal.text().strip().toLowerCase(Locale.ROOT);
        for (Rule rule : RULES) {
            if (normalized.contains(rule.keyword)) {
                return rule.action.build(goal);
            }
        }
        throw new UnplannableGoalException("Цель '" + goal.text()
                + "' не распознана: измените формулировку или передайте её ИИ-планировщику");
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