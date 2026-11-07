package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.PlanValidator;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.CoreScripts;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExpeditionMinePlanTests {
    private static final BlockCoord BOTTOM = new BlockCoord(-170, -58, -520);

    @Test
    void plan_MineBottom_GoesDownFirstThenTunnel() {
        TaskNode plan = ExpeditionMinePlan.plan(BOTTOM);

        assertEquals(
                List.of("goto", "build_schematic", "mine_vein", "return_home"),
                scripts(plan),
                "Поход идёт к низу спуска, строит ход по схеме и только из хода берёт руду: иначе у склада "
                        + "появляется яма от mine_vein на месте");
        assertTrue(
                !scripts(plan).isEmpty() && "goto".equals(scripts(plan).get(0)),
                "Первый шаг похода — дойти до низа шахты, а не копать с текущего места");
    }

    @Test
    void plan_MineBottom_BuildsTunnelAtPlannedOrigin() {
        TaskNode plan = ExpeditionMinePlan.plan(BOTTOM);
        ScriptCall build = calls(plan).stream()
                .filter(call -> "build_schematic".equals(call.script()))
                .findFirst()
                .orElseThrow();

        BlockCoord origin = ExpeditionMinePlan.tunnelOrigin(BOTTOM);
        assertEquals(ExpeditionMinePlan.SCHEME, build.params().get("file"), "Строится именно mine_tunnel_v1");
        assertEquals(origin.x(), build.params().get("x"), "Начало хода по x считается от низа спуска");
        assertEquals(origin.y(), build.params().get("y"), "Начало хода по y — на уровне низа спуска");
        assertEquals(origin.z(), build.params().get("z"), "Начало хода по z считается от низа спуска");
        assertEquals(
                ExpeditionMinePlan.ROTATION,
                build.params().get("rotation"),
                "Ход повёрнут от спуска: продолжает выработку, а не идёт обратно в лестницу");
    }

    @Test
    void plan_MineBottom_RotatedEntranceIsBottom() {
        BlockCoord origin = ExpeditionMinePlan.tunnelOrigin(BOTTOM);

        assertTrue(
                origin.x() != BOTTOM.x() || origin.z() != BOTTOM.z(),
                "Начало схемы смещено от входа: вход хода совпадает с низом спуска, а не с углом схемы");
        assertEquals(
                BOTTOM.y() - 1,
                origin.y(),
                "Низ спуска — клетка ног; схема ставится от пола, поэтому начало на блок ниже");
    }

    @Test
    void plan_MineBottom_PassesCatalog() {
        TaskNode plan = ExpeditionMinePlan.plan(BOTTOM);

        PlanValidator.validate(plan, CoreScripts.defaultCatalog(), ConditionCatalog.empty());
        assertEquals(
                4,
                scripts(plan).size(),
                "Поход собран из четырёх шагов каталога: спуск, ход, руда из хода, возврат домой");
    }

    private static List<String> scripts(TaskNode plan) {
        List<String> names = new ArrayList<>();
        collect(plan, names);
        return names;
    }

    private static List<ScriptCall> calls(TaskNode plan) {
        List<ScriptCall> calls = new ArrayList<>();
        collectCalls(plan, calls);
        return calls;
    }

    private static void collect(TaskNode plan, List<String> names) {
        if (plan instanceof ScriptCall call) {
            names.add(call.script());
        } else if (plan instanceof SequenceNode sequence) {
            sequence.steps().forEach(step -> collect(step, names));
        } else if (plan instanceof FallbackNode fallback) {
            fallback.steps().forEach(step -> collect(step, names));
        }
    }

    private static void collectCalls(TaskNode plan, List<ScriptCall> calls) {
        if (plan instanceof ScriptCall call) {
            calls.add(call);
        } else if (plan instanceof SequenceNode sequence) {
            sequence.steps().forEach(step -> collectCalls(step, calls));
        } else if (plan instanceof FallbackNode fallback) {
            fallback.steps().forEach(step -> collectCalls(step, calls));
        }
    }
}
