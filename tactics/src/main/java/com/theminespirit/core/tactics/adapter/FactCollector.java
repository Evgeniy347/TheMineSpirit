package com.theminespirit.core.tactics.adapter;

import com.theminespirit.core.tactics.Situation;

@FunctionalInterface
public interface FactCollector {
    Situation collect(SituationContext context);
}
