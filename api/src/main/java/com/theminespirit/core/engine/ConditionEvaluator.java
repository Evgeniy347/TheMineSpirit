package com.theminespirit.core.engine;

import java.util.Map;

@FunctionalInterface
public interface ConditionEvaluator {
    boolean test(Map<String, Object> params);
}
