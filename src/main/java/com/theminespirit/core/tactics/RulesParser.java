package com.theminespirit.core.tactics;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

final class RulesParser {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_]*");
    private static final String[] SECTIONS = {"hard", "actions", "modeTransitions", "states", "retreat"};
    private static final String[] PRIORITY_RULE = {"id", "priority", "when", "then"};
    private static final int MAX_PRIORITY = 1000;

    private final Set<String> ids = new HashSet<>();

    private RulesParser() {}

    static Rules parse(ConfigNode root) {
        RulesParser parser = new RulesParser();
        root.fields(SECTIONS);
        return new Rules(
                parser.hard(root.get("hard")),
                parser.actions(root.get("actions")),
                parser.modeTransitions(root.get("modeTransitions")),
                parser.states(root.get("states")),
                RetreatParser.parse(root.get("retreat"), parser::claimId));
    }

    private String claimId(ConfigNode node) {
        String id = node.text();
        if (!ID.matcher(id).matches()) {
            throw node.fail(
                    "id '" + id + "' не подходит под шаблон [a-z][a-z0-9_]*",
                    "id правила - латиница в нижнем регистре и подчёркивания");
        }
        if (!ids.add(id)) {
            throw node.fail(
                    "id '" + id + "' уже занят другим правилом",
                    "id уникален во всём rules.json: по нему сценарии и trace находят правило");
        }
        return id;
    }

    private List<HardRule> hard(ConfigNode section) {
        List<HardRule> rules = new ArrayList<>();
        for (ConfigNode item : section.nonEmptyItems()) {
            item.fields(PRIORITY_RULE);
            rules.add(new HardRule(
                    claimId(item.get("id")),
                    item.get("priority").integer(-MAX_PRIORITY, MAX_PRIORITY),
                    ConditionParser.parse(item.get("when")),
                    hardEffect(item.get("then"))));
        }
        requireClosingNoRouteRule(section, rules);
        return rules;
    }

    private static void requireClosingNoRouteRule(ConfigNode section, List<HardRule> rules) {
        boolean closed = rules.stream()
                .anyMatch(rule ->
                        rule.noRouteOption().isPresent() && rule.when().atoms().isEmpty());
        if (!closed) {
            throw section.fail(
                    "нет замыкающего правила подмены с noRoute и пустым when",
                    "добавьте правило без условий с then.noRoute: иначе RETREAT без способа отхода останется без подмены "
                            + "и вернётся запасное FIGHT");
        }
    }

    private static HardEffect hardEffect(ConfigNode then) {
        then.fields(new String[] {}, "decision", "exclude", "noRoute");
        int effects = (then.has("decision") ? 1 : 0) + (then.has("exclude") ? 1 : 0) + (then.has("noRoute") ? 1 : 0);
        if (effects != 1) {
            throw then.fail(
                    "нужно ровно одно из полей decision, exclude и noRoute",
                    "правило либо решает (decision), либо вычёркивает варианты (exclude), либо подменяет RETREAT, когда "
                            + "отступать некуда (noRoute)");
        }
        if (then.has("noRoute")) {
            TacticalOption option = then.get("noRoute").enumValue(TacticalOption.class);
            if (option == TacticalOption.RETREAT) {
                throw then.get("noRoute")
                        .fail(
                                "подмена RETREAT на RETREAT",
                                "noRoute заменяет отход, которому некуда идти: допустимы FIGHT и IGNORE");
            }
            return new HardEffect.NoRoute(option);
        }
        if (then.has("decision")) {
            return new HardEffect.Decide(then.get("decision").enumValue(TacticalOption.class));
        }
        Set<TacticalOption> excluded = EnumSet.noneOf(TacticalOption.class);
        for (ConfigNode item : then.get("exclude").nonEmptyItems()) {
            excluded.add(item.enumValue(TacticalOption.class));
        }
        return new HardEffect.Exclude(excluded);
    }

    private List<ActionRule> actions(ConfigNode section) {
        List<ActionRule> rules = new ArrayList<>();
        for (ConfigNode item : section.nonEmptyItems()) {
            item.fields(PRIORITY_RULE);
            ConfigNode then = item.get("then").fields(new String[] {"action"});
            rules.add(new ActionRule(
                    claimId(item.get("id")),
                    item.get("priority").integer(-MAX_PRIORITY, MAX_PRIORITY),
                    ConditionParser.parse(item.get("when")),
                    then.get("action").enumValue(TacticalAction.class)));
        }
        requireClosingRules(section, rules);
        return rules;
    }

    private static void requireClosingRules(ConfigNode section, List<ActionRule> rules) {
        for (TacticalOption option : TacticalOption.values()) {
            boolean closed = rules.stream()
                    .anyMatch(rule -> rule.action().option() == option
                            && rule.when().atoms().isEmpty());
            if (!closed) {
                throw section.fail(
                        "для решения " + option + " нет замыкающего правила с пустым when",
                        "добавьте правило без условий для " + option
                                + ": иначе для этого решения может не найтись действие");
            }
        }
    }

    private List<ModeTransition> modeTransitions(ConfigNode section) {
        List<ModeTransition> transitions = new ArrayList<>();
        for (ConfigNode item : section.items()) {
            item.fields(new String[] {"id", "from", "to", "when"});
            transitions.add(new ModeTransition(
                    claimId(item.get("id")),
                    enums(item.get("from"), CombatMode.class),
                    item.get("to").enumValue(CombatMode.class),
                    ConditionParser.parse(item.get("when"))));
        }
        return transitions;
    }

    private List<StateTransition> states(ConfigNode section) {
        List<StateTransition> transitions = new ArrayList<>();
        for (ConfigNode item : section.nonEmptyItems()) {
            item.fields(new String[] {"id", "from", "to", "when"}, "decision");
            Optional<TacticalOption> decision = item.has("decision")
                    ? Optional.of(item.get("decision").enumValue(TacticalOption.class))
                    : Optional.empty();
            transitions.add(new StateTransition(
                    claimId(item.get("id")),
                    enums(item.get("from"), TacticalState.class),
                    item.get("to").enumValue(TacticalState.class),
                    decision,
                    ConditionParser.parse(item.get("when"))));
        }
        return transitions;
    }

    private static <E extends Enum<E>> Set<E> enums(ConfigNode array, Class<E> type) {
        Set<E> values = EnumSet.noneOf(type);
        for (ConfigNode item : array.nonEmptyItems()) {
            values.add(item.enumValue(type));
        }
        return values;
    }
}
