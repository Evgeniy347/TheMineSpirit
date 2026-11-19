package com.theminespirit.core.planner;

import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

record ResourceHoldings(
        Map<String, Integer> inventory,
        Set<String> stations,
        @Nullable AcquireSupplies chests) {}
