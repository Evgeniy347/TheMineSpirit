package com.theminespirit.core.coordinator;

import com.theminespirit.core.tactics.Point;
import java.util.UUID;

public record DefenseAssignment(UUID defender, Point post, double bearing) {
}
