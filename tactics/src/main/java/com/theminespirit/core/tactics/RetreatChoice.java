package com.theminespirit.core.tactics;

import java.util.Optional;

public record RetreatChoice(RetreatMethod method, Optional<String> rule) {
    public static final RetreatChoice NONE = new RetreatChoice(RetreatMethod.NONE, Optional.empty());
}
