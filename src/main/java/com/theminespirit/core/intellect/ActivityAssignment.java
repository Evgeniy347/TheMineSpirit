package com.theminespirit.core.intellect;

import java.util.UUID;

public record ActivityAssignment(UUID spirit, ActivityId activity, long since, double demand) {
    public ActivityAssignment {
        if (spirit == null) {
            throw new IllegalArgumentException("Занятие без духа не учитывается: план раздают конкретным духам");
        }
        if (activity == null) {
            throw new IllegalArgumentException("Занятие без деятельности не учитывается: id входит в план духа");
        }
        if (since < 0) {
            throw new IllegalArgumentException(
                    "Занятие с since = " + since + ": время назначения не может быть отрицательным");
        }
        Activity.checkedDemand(activity, demand);
    }
}
