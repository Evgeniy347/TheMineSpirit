package com.theminespirit.core.rest;

import org.jspecify.annotations.Nullable;

public record SettlementLevelChangeResult(@Nullable Integer previous, int level, boolean changed) {}
