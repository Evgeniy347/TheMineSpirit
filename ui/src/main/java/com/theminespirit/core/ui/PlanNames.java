package com.theminespirit.core.ui;

import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public final class PlanNames {
    public interface Resolver {
        @Nullable
        String title(String planId);
    }

    private PlanNames() {}

    public static String title(String planId, @Nullable Resolver resolver) {
        Objects.requireNonNull(planId, "Id участка не может быть null: по нему ищется название");
        if (resolver != null) {
            String title = resolver.title(planId);
            if (title != null && !title.isBlank()) {
                return title;
            }
        }
        return prettify(planId);
    }

    static String prettify(String planId) {
        String text = planId.replace('_', ' ').trim();
        if (text.isEmpty()) {
            return planId;
        }
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }
}
