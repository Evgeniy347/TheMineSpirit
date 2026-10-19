package com.theminespirit.neoforge.intellect;

import com.theminespirit.core.intellect.ActivityId;
import com.theminespirit.core.intellect.ActivityIntent;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import java.util.Map;
import java.util.Optional;

public final class ActivityTaskFactory {
    private ActivityTaskFactory() {
    }

    public static Optional<TaskNode> taskFor(ActivityIntent intent) {
        if (intent == null) {
            throw new IllegalArgumentException(
                    "Задача без намерения не строится: скрипт и параметры берутся из order деятельности");
        }
        if (intent.script().isEmpty()) {
            return Optional.empty();
        }
        String script = intent.script().get();
        Map<String, Object> params = switch (script) {
            case "chop_tree" -> Map.of("species", "any", "count", 4, "radius", 16, "replant", true);
            case "harvest_crop" -> Map.of("crop", "any", "radius", 8, "replant", true);
            case "mine_vein" -> Map.of("ore", "any", "count", 8, "radius", 16);
            case "unload" -> Map.of("container", "auto", "radius", 8, "force", false);
            case "wait" -> Map.of("ticks", 200);
            default -> intent.params();
        };
        return Optional.of(new ScriptCall(script, params));
    }

}
