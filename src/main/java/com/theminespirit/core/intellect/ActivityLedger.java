package com.theminespirit.core.intellect;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ActivityLedger {
    private final Map<UUID, ActivityAssignment> entries = new LinkedHashMap<>();

    public void assign(UUID spirit, ActivityId activity, long now, double demand) {
        if (spirit == null) {
            throw new IllegalArgumentException("Занятие без духа не назначается: план раздают конкретным духам");
        }
        if (activity == null) {
            throw new IllegalArgumentException("Занятие без деятельности не назначается: id входит в план духа");
        }
        if (now < 0) {
            throw new IllegalArgumentException("Назначение в тике " + now + ": время не может быть отрицательным");
        }
        Activity.checkedDemand(activity, demand);
        ActivityAssignment current = entries.get(spirit);
        if (current != null && current.activity() == activity) {
            entries.put(spirit, new ActivityAssignment(spirit, activity, current.since(), demand));
        } else {
            entries.put(spirit, new ActivityAssignment(spirit, activity, now, demand));
        }
    }

    public boolean clear(UUID spirit) {
        if (spirit == null) {
            throw new IllegalArgumentException("Занятие не с кого снимать: дух обязан быть указан");
        }
        return entries.remove(spirit) != null;
    }

    public Optional<ActivityAssignment> of(UUID spirit) {
        if (spirit == null) {
            throw new IllegalArgumentException("Занятие некого спрашивать: дух обязан быть указан");
        }
        return Optional.ofNullable(entries.get(spirit));
    }

    public List<ActivityAssignment> all() {
        return entries.values().stream()
                .sorted(Comparator.comparing(ActivityAssignment::spirit))
                .toList();
    }
}
