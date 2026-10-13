package com.theminespirit.core.intellect;

import java.util.List;
import java.util.UUID;

public record ActivityPlan(List<ActivityAssignment> assignments, List<UUID> dropped) {
    public ActivityPlan {
        if (assignments == null) {
            throw new IllegalArgumentException("План без назначений не применяется: пустой план задают пустым списком");
        }
        if (dropped == null) {
            throw new IllegalArgumentException("План без списка выбывших не применяется: задают пустым списком");
        }
        assignments = List.copyOf(assignments);
        dropped = List.copyOf(dropped);
    }
}
