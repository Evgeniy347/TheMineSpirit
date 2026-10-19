package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.tactics.ScenarioFile;
import com.theminespirit.core.tactics.TacticalOutcome;
import com.theminespirit.core.tactics.TacticsConfig;
import com.theminespirit.core.tactics.TacticsEngine;
import com.theminespirit.core.tactics.Trace;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class TacticsRuleCoverageGuardTests {
    @Test
    void rulesJson_EveryRule_FiresInAtLeastOneScenario() {
        TacticsConfig config = TacticsConfig.loadDefault();
        TacticsEngine engine = new TacticsEngine(config);
        Set<String> fired = new TreeSet<>();
        for (ScenarioFile.Scenario scenario : ScenarioFile.load()) {
            TacticalOutcome outcome = engine.step(scenario.situation(), scenario.memory(), scenario.tick());
            fired.addAll(firedRules(outcome.trace()));
        }

        List<String> dead = uncovered(allRuleIds(config), fired);

        assertEquals(List.of(), dead,
                "Правило из rules.json не срабатывает ни в одном сценарии scenarios.json: оно либо мёртвое (условие "
                        + "недостижимо или перекрыто старшим правилом), либо не защищено тестом, и его поломку никто не "
                        + "заметит. Добавьте сценарий, который его вызывает, или удалите правило вместе с упоминаниями в "
                        + "docs/tactics/README.md. Мёртвые правила");
    }

    @Test
    void uncovered_RuleWithoutScenario_IsReported() {
        List<String> dead = uncovered(Set.of("a_rule", "b_rule", "c_rule"), Set.of("a_rule", "c_rule"));

        assertEquals(List.of("b_rule"), dead,
                "Сторож ослеп: правило, которого нет среди сработавших, обязано попадать в список мёртвых");
    }

    @Test
    void uncovered_AllRulesFired_ReportsNothing() {
        assertEquals(List.of(), uncovered(Set.of("a_rule"), Set.of("a_rule", "score")),
                "Сторож ложно срабатывает: лишние служебные имена среди сработавших (score, hold) не мёртвые правила");
    }

    private static Set<String> allRuleIds(TacticsConfig config) {
        Set<String> ids = new TreeSet<>();
        config.rules().hard().forEach(rule -> ids.add(rule.id()));
        config.rules().actions().forEach(rule -> ids.add(rule.id()));
        config.rules().modeTransitions().forEach(rule -> ids.add(rule.id()));
        config.rules().states().forEach(rule -> ids.add(rule.id()));
        config.rules().retreat().entries().forEach(rule -> ids.add(rule.id()));
        return ids;
    }

    private static Set<String> firedRules(Trace trace) {
        Set<String> fired = new TreeSet<>(trace.excludingRules());
        fired.add(trace.rule());
        fired.add(trace.actionRule());
        trace.retreatRule().ifPresent(fired::add);
        trace.stateRule().ifPresent(fired::add);
        trace.modeRule().ifPresent(fired::add);
        return fired;
    }

    private static List<String> uncovered(Set<String> all, Set<String> fired) {
        List<String> dead = new ArrayList<>(new TreeSet<>(all));
        dead.removeAll(fired);
        return dead;
    }
}
