package com.theminespirit.core.planner;

import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.core.plan.PlanValidationException;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.ScriptCatalog;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class BatchPlanner {
    private BatchPlanner() {
    }

    public static List<TaskNode> planAll(List<ScriptCatalog> catalogs, Goal goal,
            List<WorldObservation> observations) {
        Objects.requireNonNull(catalogs, "catalogs не может быть null: без каталогов планировать нечего");
        Objects.requireNonNull(goal, "goal не может быть null: план строится по цели");
        Objects.requireNonNull(observations,
                "observations не может быть null: каждому духу нужно своё наблюдение");
        if (catalogs.size() != observations.size()) {
            throw new IllegalArgumentException("Каталогов " + catalogs.size() + ", а наблюдений "
                    + observations.size() + ": каждому духу нужны свой каталог и своё наблюдение");
        }
        Set<String> seen = new HashSet<>();
        for (WorldObservation observation : observations) {
            if (!seen.add(observation.botId())) {
                throw new IllegalArgumentException("Наблюдение духа '" + observation.botId()
                        + "' передано дважды: каждому духу нужно своё наблюдение, общее скрывает чужие "
                        + "позицию и инвентарь");
            }
        }
        List<TaskNode> plans = new ArrayList<>(catalogs.size());
        for (int i = 0; i < catalogs.size(); i++) {
            try {
                plans.add(new FallbackPlanner(catalogs.get(i), ConditionCatalog.empty())
                        .plan(goal, observations.get(i)).plan());
            } catch (UnplannableGoalException | PlanValidationException rejection) {
                throw new BatchPlanException(i, rejection);
            }
        }
        return List.copyOf(plans);
    }
}
