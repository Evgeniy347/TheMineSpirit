package com.theminespirit.core.tactics;

import java.util.Optional;

public record StateStep(TacticalState state, Optional<String> rule) {
}
