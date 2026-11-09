package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.CrossActions.*;
import static com.theminespirit.core.board.graph.CrossScene.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardActivities;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CrossMovementPlanTests {
    @Test
    void movement_WorkCards_MoveToTargetThenAct() {
        BlockCoord at = new BlockCoord(3, 64, 3);
        BlockCoord chest = new BlockCoord(1, 64, 1);

        assertMovesFirst("жатва", ActivityPlans.till(at).task(), "till");
        assertMovesFirst("посев", ActivityPlans.plantCrop(at, "wheat", 4).task(), "plant_crop");
        assertMovesFirst("кормление", ActivityPlans.feed(at, Species.SHEEP, 4).task(), "feed");
        assertMovesFirst("дойка", ActivityPlans.milk(at, 2).task(), "milk");
        assertMovesFirst("крафт", ActivityPlans.craft(at, "torch", 4).task(), "craft");
        assertMovesFirst(
                "броня", ActivityPlans.equip(chest, at, "iron_chestplate").task(), "equip");
        assertMovesFirst("склад", ActivityPlans.unload(at).task(), "unload");
        assertMovesFirst(
                "перенос", ActivityPlans.moveItem(chest, at, at, "bread", 1).task(), "deposit");
        assertMovesFirst("еда", ActivityPlans.withdraw(chest, at, "bread", 2).task(), "withdraw");
        assertMovesFirst("плавка", ActivityPlans.smelt(at, "iron_ore", 2).task(), "smelt");

        List<String> chop = scripts(BoardCase.GATHER_MATERIALS, chopTarget());
        assertEquals("goto", chop.get(0), "Рубка начинается с подхода к дереву: " + chop);
        assertTrue(chop.contains("chop_tree"), "Подошёл — валит дерево: " + chop);
        List<String> build = scripts(BoardCase.BUILD_HOUSE);
        assertEquals("goto", build.get(0), "Стройка начинается с подхода к участку: " + build);
        assertTrue(build.contains("build_schematic"), "На участке ставит схему: " + build);
        List<String> fence = scripts(BoardCase.REPAIR_FENCE);
        assertTrue(
                fence.contains("goto") && fence.contains("fill"),
                "Ремонт забора идёт к пролому и заделывает его: " + fence);
        List<String> light = scripts(BoardCase.LIGHTING);
        assertEquals("goto", light.get(0), "Свет начинается с подхода к тёмному месту: " + light);
        assertTrue(light.contains("place_block"), "На месте ставит факел: " + light);
        assertEquals(
                BoardCase.ACTIVITY_RETREAT,
                BoardActivities.caseOfScript("retreat").orElseThrow(),
                "Бегство остаётся действием отхода, а не рубкой");
        assertEquals(
                BoardCase.ACTIVITY_EXPEDITION,
                BoardActivities.caseOfScript("follow").orElseThrow(),
                "Поход идёт сопровождением follow");
    }

    private static void assertMovesFirst(String what, TaskNode plan, String action) {
        List<String> scripts = new ArrayList<>();
        collect(plan, scripts);
        assertEquals(
                "goto", scripts.get(0), what + " начинается с перехода к цели, а не с действия на месте: " + scripts);
        assertTrue(scripts.contains(action), what + " после перехода выполняет своё действие: " + scripts);
    }

    private static void collect(TaskNode node, List<String> scripts) {
        if (node instanceof ScriptCall call) {
            scripts.add(call.script());
            return;
        }
        if (node instanceof SequenceNode sequence) {
            sequence.steps().forEach(step -> collect(step, scripts));
            return;
        }
        if (node instanceof FallbackNode fallback) {
            fallback.steps().forEach(step -> collect(step, scripts));
        }
    }

    @Test
    void pit_PillarNeedsTrailClean_SecondRunOpensExit() {
        GraphWorld world = stageOne();
        world.with("oak_planks", 64).with("cobblestone", 64).with("oak_log", 64);
        GraphFacts facts = facts(
                named(spirit("spirit-1"), spirit("spirit-2", 20, 20, true, false, "pit", List.of(), List.of())),
                List.of(),
                List.of(),
                GraphSiteFacts.Terrain.empty(),
                Map.of());
        GraphPlan first = plan(world, facts);

        assertFalse(
                task(first, "pillar").feasible(), "Столб из ямы не ставится, пока не возвращён след: " + ids(first));
        assertTrue(task(first, "restore_trace").feasible(), "Первый прогон даёт возврат следа: " + ids(first));

        world.with("trail_clean", 1);
        GraphPlan second = plan(world, facts);

        assertTrue(
                task(second, "pillar").feasible(),
                "След возвращён — выход столбом появляется со следующего прогона: " + ids(second));
        List<String> pillar = new ArrayList<>();
        collect(ActivityPlans.pillar(3, "oak_planks").task(), pillar);
        assertEquals("pillar_up", pillar.get(0), "Столб строится действием pillar_up, а не телепортом: " + pillar);
        assertEquals(
                BoardCase.ACTIVITY_BUILD,
                BoardActivities.caseOfScript("pillar_up").orElseThrow(),
                "Столб меняет мир отдельным узлом pillar, а не телепортом");
    }
}
