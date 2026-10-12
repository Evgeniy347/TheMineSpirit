package com.theminespirit.core.tactics.adapter;

import com.theminespirit.core.tactics.Situation;

public interface FactCollector {
    Situation collect(SituationContext context);
}
