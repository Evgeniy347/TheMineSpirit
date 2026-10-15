package com.theminespirit.core.inventory;

import com.theminespirit.core.reload.DataTables;
import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.plan.ConditionNode;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ParallelNode;
import com.theminespirit.core.plan.RepeatNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class KitFor {
    private static final Set<String> ROOT_FIELDS = Set.of("activities", "child");
    private static final Set<String> ACTIVITY_FIELDS = Set.of("scripts", "rules");
    private static final Set<String> CHILD_FIELDS = Set.of("rules");

    private final String origin;
    private final List<Activity> activities;
    private final List<KitRule> childRules;
    private final List<String> childNeverGiveAway;

    private KitFor(String origin, List<Activity> activities, List<KitRule> childRules,
            List<String> childNeverGiveAway) {
        this.origin = origin;
        this.activities = List.copyOf(activities);
        this.childRules = List.copyOf(childRules);
        this.childNeverGiveAway = List.copyOf(childNeverGiveAway);
    }

    public static KitFor load() {
        return DataTables.INSTANCE.kitFor().current();
    }

    /** Чтение из classpath для начальной версии держателя: не заглядывает в сам держатель. */
    public static KitFor readClasspath() {
        return parse(JsonResource.readClasspath("data/theminespirit/inventory/kit_activities.json"),
                "data/theminespirit/inventory/kit_activities.json");
    }

    public static KitFor load(String resourcePath) {
        return parse(JsonResource.readClasspath(resourcePath), resourcePath);
    }

    public static KitFor parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, ROOT_FIELDS, origin, "файла наборов по деятельностям");
        JsonNode activitiesNode = root.get("activities");
        if (activitiesNode == null || !activitiesNode.isObject() || activitiesNode.isEmpty()) {
            throw new IllegalStateException("Файл '" + origin
                    + "' без списка activities: без деятельностей набор духа не знал бы, что ему нужно в шахте");
        }
        List<String> names = new ArrayList<>();
        activitiesNode.fieldNames().forEachRemaining(names::add);
        List<Activity> activities = new ArrayList<>(names.size());
        for (int index = 0; index < names.size(); index++) {
            String name = names.get(index);
            JsonNode value = activitiesNode.get(name);
            if (!value.isObject()) {
                throw new IllegalStateException("Файл '" + origin + "': деятельность '" + name
                        + "' — не объект с полями scripts и rules");
            }
            JsonResource.checkFields(value, ACTIVITY_FIELDS, origin, "деятельности '" + name + "'");
            List<String> scripts = JsonResource.strings(value.get("scripts"), origin,
                    "поля scripts деятельности '" + name + "'");
            if (scripts.isEmpty()) {
                throw new IllegalStateException("Файл '" + origin + "': у деятельности '" + name
                        + "' пустой список scripts: деятельность без скриптов не узнаётся в задачах духа");
            }
            activities.add(new Activity(name, scripts, parseRules(value.get("rules"), origin, name)));
        }
        List<Activity> byName = new ArrayList<>();
        for (Activity activity : activities) {
            for (Activity existing : byName) {
                if (existing.name().equals(activity.name())) {
                    throw new IllegalStateException("Файл '" + origin + "': деятельность '" + activity.name()
                            + "' описана дважды: повтор делает выбор набора неопределённым");
                }
            }
            byName.add(activity);
        }
        List<KitRule> childRules = parseRules(childRulesNode(root, origin).get("rules"), origin, "child");
        List<String> childNeverGiveAway = JsonResource.optionalStrings(
                childRulesNode(root, origin).get("never_give_away"), origin, "списка never_give_away child");
        return new KitFor(origin, byName, childRules, childNeverGiveAway);
    }

    public static BaseKit kitFor(String activity, boolean isChild) {
        return load().kit(activity, isChild);
    }

    public BaseKit kit(String activity, boolean isChild) {
        if (isChild) {
            return BaseKit.of(childRules, childNeverGiveAway);
        }
        BaseKit constant = BaseKit.load();
        Activity found = activity == null ? null : find(activity);
        if (found == null) {
            return constant;
        }
        List<KitRule> merged = new ArrayList<>(found.rules());
        merged.addAll(constant.rules());
        return BaseKit.of(merged, constant.neverGiveAway());
    }

    public static Optional<String> activityOfScript(String script) {
        return load().activityOfScriptInstance(script);
    }

    private Optional<String> activityOfScriptInstance(String script) {
        Objects.requireNonNull(script, "Имя скрипта не может быть null: деятельность ищут по имени скрипта");
        for (Activity activity : activities) {
            if (activity.covers(script)) {
                return Optional.of(activity.name());
            }
        }
        return Optional.empty();
    }

    public static Optional<String> activityOfPlan(TaskNode plan) {
        Objects.requireNonNull(plan, "План не может быть null: деятельность ищут по шагам плана");
        return load().activityOfNode(plan);
    }

    public String origin() {
        return origin;
    }

    public List<Activity> activities() {
        return activities;
    }

    public List<KitRule> childRules() {
        return childRules;
    }

    private Optional<String> activityOfNode(TaskNode node) {
        if (node instanceof ScriptCall call) {
            return activityOfScript(call.script());
        }
        if (node instanceof SequenceNode sequence) {
            return activityOfSteps(sequence.steps());
        }
        if (node instanceof FallbackNode fallback) {
            return activityOfSteps(fallback.steps());
        }
        if (node instanceof ParallelNode parallel) {
            return activityOfSteps(parallel.steps());
        }
        if (node instanceof RepeatNode repeat) {
            return activityOfNode(repeat.step());
        }
        if (node instanceof ConditionNode condition) {
            Optional<String> then = activityOfNode(condition.thenStep());
            return then.isPresent() ? then
                    : condition.elseStep() == null ? Optional.empty() : activityOfNode(condition.elseStep());
        }
        return Optional.empty();
    }

    private Optional<String> activityOfSteps(List<TaskNode> steps) {
        for (TaskNode step : steps) {
            Optional<String> found = activityOfNode(step);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private Activity find(String activity) {
        for (Activity candidate : activities) {
            if (candidate.name().equals(activity)) {
                return candidate;
            }
        }
        return null;
    }

    private static JsonNode childRulesNode(JsonNode root, String origin) {
        JsonNode node = root.get("child");
        if (node == null || !node.isObject()) {
            throw new IllegalStateException("Файл '" + origin
                    + "' без объекта child: без детского набора ребёнок носил бы весь взрослый набор");
        }
        JsonResource.checkFields(node, CHILD_FIELDS, origin, "child");
        return node;
    }

    private static List<KitRule> parseRules(JsonNode node, String origin, String activity) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        List<KitRule> rules = new ArrayList<>();
        List<JsonNode> nodes = JsonResource.items(node);
        for (int index = 0; index < nodes.size(); index++) {
            JsonNode ruleNode = nodes.get(index);
            String what = "правила №" + index + " деятельности '" + activity + "'";
            if (!ruleNode.isObject()) {
                throw new IllegalStateException("Файл '" + origin + "': " + what + " — не объект с полями "
                        + "match/category, keep и mode");
            }
            JsonResource.checkFields(ruleNode, BaseKit.RULE_FIELDS, origin, what);
            JsonNode matchNode = ruleNode.get("match");
            List<String> match = matchNode == null || matchNode.isNull() ? null
                    : JsonResource.optionalStrings(matchNode, origin, "поля match " + what);
            rules.add(rule(match, BaseKit.textField(ruleNode.get("category"), origin, what),
                    BaseKit.keepField(ruleNode.get("keep"), origin, "поля keep " + what),
                    BaseKit.modeField(ruleNode.get("mode"), origin, "поля mode " + what), origin, what));
        }
        return rules;
    }

    private static KitRule rule(List<String> match, String category, int keep, KitMode mode, String origin,
            String what) {
        try {
            return new KitRule(match, category, keep, mode);
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': " + what + " неверно — "
                    + error.getMessage(), error);
        }
    }

    public record Activity(String name, List<String> scripts, List<KitRule> rules) {
        private boolean covers(String script) {
            return scripts.contains(script);
        }
    }
}
