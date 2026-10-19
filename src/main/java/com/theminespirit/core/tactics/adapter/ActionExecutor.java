package com.theminespirit.core.tactics.adapter;

import com.theminespirit.core.tactics.TacticalOutcome;

@FunctionalInterface
public interface ActionExecutor {
    ExecutionResult execute(TacticalOutcome outcome, SituationContext context);
}
