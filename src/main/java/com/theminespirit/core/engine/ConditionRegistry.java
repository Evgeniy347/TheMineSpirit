package com.theminespirit.core.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ConditionRegistry {
    private final Map<String, ConditionEvaluator> byCheck;

    private ConditionRegistry(Map<String, ConditionEvaluator> byCheck) {
        this.byCheck = Collections.unmodifiableMap(new LinkedHashMap<>(byCheck));
    }

    public static ConditionRegistry of(Map<String, ConditionEvaluator> evaluators) {
        Objects.requireNonNull(evaluators, "evaluators не может быть null: пустой реестр задают пустой картой");
        Map<String, ConditionEvaluator> byCheck = new LinkedHashMap<>();
        for (Map.Entry<String, ConditionEvaluator> entry : evaluators.entrySet()) {
            String check = Objects.requireNonNull(
                    entry.getKey(), "имя проверки не может быть null: по нему условие выбирает ветку");
            ConditionEvaluator evaluator = Objects.requireNonNull(
                    entry.getValue(),
                    "вычислитель проверки '" + check + "' не может быть null: без него условие не выберет ветку");
            byCheck.put(check, evaluator);
        }
        return new ConditionRegistry(byCheck);
    }

    public Optional<ConditionEvaluator> find(String check) {
        return Optional.ofNullable(byCheck.get(check));
    }
}
