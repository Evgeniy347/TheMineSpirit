package com.theminespirit.core.tactics;

import java.util.List;

public final class ActionTable {
    private final List<ActionRule> rules;
    private final RuleCounter counter;

    public ActionTable(List<ActionRule> rules, RuleCounter counter) {
        this.rules = List.copyOf(rules);
        this.counter = counter;
    }

    public ActionChoice choose(TacticalOption decision, FactMap facts) {
        for (ActionRule rule : rules) {
            if (rule.action().option() != decision) {
                continue;
            }
            counter.check();
            if (rule.when().matches(facts)) {
                return new ActionChoice(rule.action(), rule.id());
            }
        }
        throw new IllegalStateException("Для решения " + decision + " не нашлось действия в rules.json → actions: "
                + "загрузчик обязан был потребовать замыкающее правило без условий, проверьте валидацию");
    }
}
