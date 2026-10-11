package com.theminespirit.core.planner;

import com.theminespirit.core.observation.WorldObservation;
import com.theminespirit.core.plan.PlanValidationException;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.ScriptCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class BatchPlanner {
    private BatchPlanner() {
    }

    public static List<TaskNode> planAll(List<ScriptCatalog> catalogs, Goal goal, WorldObservation observation) {
        Objects.requireNonNull(catalogs, "catalogs не может быть null: без каталогов планировать нечего");
        Objects.requireNonNull(goal, "goal не может быть null: план строится по цели");
        Objects.requireNonNull(observation, "observation не может быть null: план строится по наблюдению");
        List<TaskNode> plans = new ArrayList<>(catalogs.size());
        for (int i = 0; i < catalogs.size(); i++) {
            try {
                plans.add(new FallbackPlanner(catalogs.get(i), ConditionCatalog.empty())
                        .plan(goal, observation).plan());
            } catch (UnplannableGoalException | PlanValidationException rejection) {
                throw new BatchPlanException(i, rejection);
            }
        }
        return List.copyOf(plans);
    }
}
