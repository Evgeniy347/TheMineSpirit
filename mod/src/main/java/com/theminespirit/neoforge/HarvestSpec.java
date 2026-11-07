package com.theminespirit.neoforge;

import com.theminespirit.core.coordinator.WorkKind;

record HarvestSpec(
        HarvestTargets targets,
        WorkKind kind,
        String label,
        int count,
        int radius,
        boolean skipWithoutTool,
        boolean vein,
        boolean allowTunnel) {}
