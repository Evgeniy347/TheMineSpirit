package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildBox;
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
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;

public final class BuildPlanOwner {
    private static final int MAX_DEMOLISH_SCAN = 32768;

    private BuildPlanOwner() {}

    public static PlanBuilding of(SpiritEntity spirit) {
        Optional<TaskNode> plan = spirit.currentPlan();
        if (plan.isEmpty() || !(spirit.level() instanceof ServerLevel server)) {
            return PlanBuilding.NONE;
        }
        ScriptCall call = buildCall(plan.get());
        if (call != null) {
            BlockCoord origin = origin(call);
            return SettlementPlotStore.get(server)
                    .site()
                    .map(stage -> PlanBuildings.ofBuild(stage, file(call), origin))
                    .orElseGet(() -> PlanBuildings.ofScheme(file(call)));
        }
        ScriptCall clearing = demolitionCall(plan.get());
        if (clearing == null) {
            return PlanBuilding.NONE;
        }
        return SettlementPlotStore.get(server)
                .site()
                .map(stage -> demolished(stage, clearing))
                .orElse(PlanBuilding.NONE);
    }

    private static ScriptCall demolitionCall(TaskNode node) {
        return switch (node) {
            case ScriptCall call ->
                "clear_area".equals(call.script())
                                && Boolean.TRUE.equals(call.params().get("demolish"))
                        ? call
                        : null;
            case SequenceNode sequence -> firstDemolition(sequence.steps());
            case FallbackNode fallback -> firstDemolition(fallback.steps());
            case RepeatNode repeat -> demolitionCall(repeat.step());
            case ParallelNode parallel -> firstDemolition(parallel.steps());
            case ConditionNode condition -> {
                ScriptCall found = demolitionCall(condition.thenStep());
                if (found != null || condition.elseStep() == null) {
                    yield found;
                }
                yield demolitionCall(condition.elseStep());
            }
        };
    }

    private static ScriptCall firstDemolition(List<TaskNode> steps) {
        for (TaskNode step : steps) {
            ScriptCall found = demolitionCall(step);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static PlanBuilding demolished(StageSite site, ScriptCall call) {
        BuildBox box = BuildBox.of(
                intParam(call, "x1"),
                intParam(call, "y1"),
                intParam(call, "z1"),
                intParam(call, "x2"),
                intParam(call, "y2"),
                intParam(call, "z2"));
        if (box.volume() > MAX_DEMOLISH_SCAN) {
            return PlanBuilding.NONE;
        }
        PlanBuilding found = PlanBuilding.NONE;
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    PlanBuilding at = site.protectionBuilding(x, y, z);
                    if (at == PlanBuilding.NONE) {
                        continue;
                    }
                    if (found != PlanBuilding.NONE && found != at) {
                        return PlanBuilding.NONE;
                    }
                    found = at;
                }
            }
        }
        return found;
    }

    private static int intParam(ScriptCall call, String name) {
        return call.params().get(name) instanceof Integer value ? value : 0;
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
