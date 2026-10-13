package com.theminespirit.core.intellect;

public record ActivityDemand(ActivityId id, double demand) {
    public ActivityDemand {
        if (id == null) {
            throw new IllegalArgumentException("Спрос без деятельности не распределяется: id входит в план духа");
        }
        Activity.checkedDemand(id, demand);
    }
}
