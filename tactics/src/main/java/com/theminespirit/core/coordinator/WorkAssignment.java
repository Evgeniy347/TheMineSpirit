package com.theminespirit.core.coordinator;

import com.theminespirit.core.coordinator.work.WorkTarget;
import java.util.UUID;

public record WorkAssignment(UUID spirit, WorkTarget target) {}
