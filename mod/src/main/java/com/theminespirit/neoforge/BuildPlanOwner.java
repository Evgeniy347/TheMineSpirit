package com.theminespirit.neoforge;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ConditionNode;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ParallelNode;
import com.theminespirit.core.plan.RepeatNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.town.PlanBuilding;
import com.theminespirit.core.town.PlanBuildings;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;

public final class BuildPlanOwner {
    private BuildPlanOwner() {}

    public static PlanBuilding of(SpiritEntity spirit) {
        Optional<TaskNode> plan = spirit.currentPlan();
        if (plan.isEmpty()) {
            return PlanBuilding.NONE;
        }
        ScriptCall call = buildCall(plan.get());
        if (call == null || !(spirit.level() instanceof ServerLevel server)) {
            return PlanBuilding.NONE;
        }
        BlockCoord origin = origin(call);
        return SettlementPlotStore.get(server)
                .site()
                .map(stage -> PlanBuildings.ofBuild(stage, file(call), origin))
                .orElseGet(() -> PlanBuildings.ofScheme(file(call)));
    }

    private static ScriptCall buildCall(TaskNode node) {
        return switch (node) {
            case ScriptCall call -> "build_schematic".equals(call.script()) ? call : null;
            case SequenceNode sequence -> first(sequence.steps());
            case FallbackNode fallback -> first(fallback.steps());
            case RepeatNode repeat -> buildCall(repeat.step());
            case ParallelNode parallel -> first(parallel.steps());
            case ConditionNode condition -> {
                ScriptCall found = buildCall(condition.thenStep());
                if (found != null || condition.elseStep() == null) {
                    yield found;
                }
                yield buildCall(condition.elseStep());
            }
        };
    }

    private static ScriptCall first(List<TaskNode> steps) {
        for (TaskNode step : steps) {
            ScriptCall found = buildCall(step);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static String file(ScriptCall call) {
        return call.params().get("file") instanceof String file ? file : "";
    }

    private static BlockCoord origin(ScriptCall call) {
        int x = call.params().get("x") instanceof Integer value ? value : 0;
        int y = call.params().get("y") instanceof Integer value ? value : 0;
        int z = call.params().get("z") instanceof Integer value ? value : 0;
        return new BlockCoord(x, y, z);
    }
}
