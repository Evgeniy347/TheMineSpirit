package com.theminespirit.core.coordinator;

import com.theminespirit.core.tactics.api.Point;
import java.util.UUID;

public record DefenseAssignment(UUID defender, Point post, double bearing) {}
