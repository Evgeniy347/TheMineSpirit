package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageWork;
import com.theminespirit.core.town.TownFixtures;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrchardSaplingSourceTests {
    private static final String SAPLING = "minecraft:oak_sapling";
    private static final String LEAVES = "oak_leaves";

    @Test
    void plan_Orchard_SaplingsComeFromLeavesNotPlacedOnes() {
        List<ScriptCall> calls = calls(orchardPlan());

        assertTrue(
                calls.stream()
                        .anyMatch(call -> "gather".equals(call.script())
                                && LEAVES.equals(call.params().get("block"))),
                "Саженцы сада берутся с листвы: размещённый саженец — посаженное дерево, а не запас (N8). План: "
                        + calls);
        assertTrue(
                calls.stream()
                        .anyMatch(call -> "chop_tree".equals(call.script())
                                && "oak".equals(call.params().get("species"))
                                && Boolean.FALSE.equals(call.params().get("replant"))),
                "Перед сбором листвы сад валит дуб без пересадки — листва даёт саженцы только с поваленного "
                        + "дерева. План: " + calls);
        assertFalse(
                calls.stream()
                        .anyMatch(call -> "acquire".equals(call.script())
                                && SAPLING.equals(call.params().get("item"))),
                "Сад не просит acquire саженца: acquire уходил в «добыть oak_sapling ×4» из мира и падал "
                        + "без стоянки. План: " + calls);
    }

    @Test
    void plan_SaplingGather_TargetsTrunkNotOrchardCenter() {
        StageSite site = TownFixtures.flatSite();
        BlockCoord plot = site.orchardOrigin();
        TicketTarget target = TicketTarget.at(plot).withSupply(SAPLING, 24);

        List<ScriptCall> calls = calls(TicketPlans.plan(BoardCase.GATHER_MATERIALS, 1, target));
        int chop = firstChop(calls);

        assertFalse(
                calls.subList(0, chop).stream()
                        .anyMatch(call -> "goto".equals(call.script())
                                && Integer.valueOf(plot.x())
                                        .equals(call.params().get("x"))
                                && Integer.valueOf(plot.y())
                                        .equals(call.params().get("y"))
                                && Integer.valueOf(plot.z())
                                        .equals(call.params().get("z"))),
                "Цель добычи саженцев — ствол настоящего дерева, а не центр участка сада: до рубки дух не идёт в "
                        + "пустую точку сада, а ищет дерево из своего положения. План: " + calls);
        assertTrue(
                calls.stream()
                        .anyMatch(call -> "chop_tree".equals(call.script())
                                && "oak".equals(call.params().get("species"))
                                && Boolean.FALSE.equals(call.params().get("replant"))),
                "Саженцы даёт поваленное дерево: план валит дуб без пересадки, а не идёт в центр участка. План: "
                        + calls);
    }

    @Test
    void plan_SaplingGather_CollectsEnoughLeavesForWholeOrchard() {
        StageSite site = TownFixtures.flatSite();
        TicketTarget target = TicketTarget.at(site.orchardOrigin()).withSupply(SAPLING, 24);

        int leaves = calls(TicketPlans.plan(BoardCase.GATHER_MATERIALS, 1, target)).stream()
                .filter(call -> "gather".equals(call.script())
                        && LEAVES.equals(call.params().get("block")))
                .mapToInt(call -> (Integer) call.params().get("count"))
                .sum();

        assertTrue(
                leaves >= 24 * 20,
                "Саженец падает примерно с каждого двадцатого листа: на 24 саженца нужно не меньше 480 листьев, "
                        + "а план собирает " + leaves + " — иначе сад остаётся 0 из 24");
    }

    private static int firstChop(List<ScriptCall> calls) {
        for (int index = 0; index < calls.size(); index++) {
            if ("chop_tree".equals(calls.get(index).script())) {
                return index;
            }
        }
        throw new AssertionError("В плане саженцев нет рубки дерева: " + calls);
    }

    @Test
    void supply_TorchFuel_FirstBranchIsCharcoal() {
        TaskNode first = TorchPlans.supply(4).get(0);

        FallbackNode fallback = assertInstanceOf(
                FallbackNode.class,
                first,
                "Топливо для факелов добывается с запасной веткой: без неё шахта без стоянки валит задание (N8)");

        List<ScriptCall> branches = calls(fallback);
        assertTrue(
                branches.stream()
                        .anyMatch(call -> "acquire".equals(call.script())
                                && "minecraft:charcoal".equals(call.params().get("item"))),
                "Первая ветка — древесный уголь обжигом: доска требует ровно то, что план добывает первым "
                        + "(observe12 N19), а угольная руда в глубине не всегда достижима. Ветки: " + branches);
        assertTrue(
                branches.stream()
                        .anyMatch(call -> "acquire".equals(call.script())
                                && "minecraft:coal".equals(call.params().get("item"))),
                "Вторая ветка — уголь из шахты: факел принимает и уголь, и древесный уголь. Ветки: " + branches);
        assertEquals(
                2,
                branches.size(),
                "Веток ровно две: уголь и древесный уголь, иначе раздел «сколько угля» расходится с планом. "
                        + "Ветки: " + branches);
    }

    private static TaskNode orchardPlan() {
        StageSite site = TownFixtures.flatSite();
        TicketTarget target = TicketTarget.at(site.workYard()).withWork(new StageWork(site, List.of()));
        return TicketPlans.plan(BoardCase.BUILD_ORCHARD, 1, target);
    }

    private static List<ScriptCall> calls(TaskNode plan) {
        List<ScriptCall> out = new ArrayList<>();
        collect(plan, out);
        return out;
    }

    private static void collect(TaskNode node, List<ScriptCall> out) {
        switch (node) {
            case ScriptCall call -> out.add(call);
            case SequenceNode sequence -> sequence.steps().forEach(step -> collect(step, out));
            case FallbackNode fallback -> fallback.steps().forEach(step -> collect(step, out));
            default ->
                throw new IllegalArgumentException("Узел " + node.getClass().getSimpleName()
                        + " не разбирается: тест ждёт script, sequence, " + "fallback");
        }
    }
}
