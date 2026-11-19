package com.theminespirit.neoforge;

import com.theminespirit.core.coordinator.work.WorkKind;

record HarvestSpec(
        HarvestTargets targets,
        WorkKind kind,
        String label,
        int count,
        int radius,
        boolean skipWithoutTool,
        boolean vein,
        boolean allowTunnel) {}
