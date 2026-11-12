package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.CrossActions.scripts;
import static com.theminespirit.core.board.graph.CrossScene.ahead;
import static com.theminespirit.core.board.graph.CrossScene.chest;
import static com.theminespirit.core.board.graph.CrossScene.damaged;
import static com.theminespirit.core.board.graph.CrossScene.ids;
import static com.theminespirit.core.board.graph.CrossScene.named;
import static com.theminespirit.core.board.graph.CrossScene.plan;
import static com.theminespirit.core.board.graph.CrossScene.ranked;
import static com.theminespirit.core.board.graph.CrossScene.spirit;
import static com.theminespirit.core.board.graph.CrossScene.stageOne;
import static com.theminespirit.core.board.graph.CrossScene.task;
import static com.theminespirit.core.board.graph.CrossScene.threat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardActivities;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CrossSiegeScenarioTests {
    private static GraphFacts siegeFacts() {
        return new GraphFacts(
                named(spirit("spirit-1", 4, 2), spirit("spirit-2"), spirit("spirit-3")),
                List.of(
                        threat("mob-a", "spirit-1", GraphFacts.TargetKind.SPIRIT, 1),
                        threat("mob-b", "spirit-1", GraphFacts.TargetKind.SPIRIT, 2)),
                List.of(chest(Map.of("food", 8))),
                List.of(),
                Map.of(),
                Map.of("fence", damaged("fence", 40, 3, Map.of("oak_fence", 5))),
                List.of(new GraphSiteFacts.Stray(
                        "stray:chest@1,64,1",
                        "chest",
                        new GraphFacts.Position(1, 64, 1),
                        "spirit-1",
                        Map.of("minecraft:chest", 1))),
                new GraphSiteFacts.Terrain(2, 0, 0, 2, 0, Map.of(), Map.of("minecraft:dirt", 2), Map.of()),
                new GraphSiteFacts.StageReqs(Map.of(), Map.of()),
                GraphFeedback.none(),
                List.of(),
                List.of(),
                List.of());
    }

    @Test
    void siege_LifeAboveWork_WorkStaysPosted() {
        GraphWorld world = stageOne();
        world.built("hut", Built.PLANNED).built("fence", Built.PLANNED);
        world.with("oak_log", 0).with("oak_planks", 64).with("cobblestone", 64).with("dirt", 64);
        world.with("coal", 8)
                .with("torch", 0)
                .with("stick", 64)
                .with("iron_ingot", 64)
                .with("food", 8)
                .with("oak_fence", 16)
                .with("birch_log", 8);
        world.event("night")
                .event("hungry")
                .event("expedition_ready")
                .event("more4_player_near")
                .event("more5_mine_dark");
        GraphPlan plan = plan(world, siegeFacts());

        GraphPlan.Task defend = task(plan, "event:life_threat:defend");
        GraphPlan.Task retreat = task(plan, "event:life_threat:retreat");
        GraphPlan.Task arm = task(plan, "event:life_threat:arm");
        GraphPlan.Task eat = task(plan, "event:life_threat:eat");
        GraphPlan.Task sleep = task(plan, "event:life_threat:sleep");
        GraphPlan.Task mine = task(plan, "mine_coal");
        GraphPlan.Task chop = task(plan, "chop_oak_log");
        GraphPlan.Task hut = task(plan, "building:hut");
        GraphPlan.Task fence = task(plan, "event:repair_fence:repair:fence");
        GraphPlan.Task backfill = task(plan, "event:backfill:backfill");
        GraphPlan.Task demolish = task(plan, "event:demolish_stray:demolish_stray");
        GraphPlan.Task expedition = task(plan, "event:expedition_ready:expedition");
        GraphPlan.Task guard = task(plan, "event:more4_player_near:more4_guard_settlement");
        GraphPlan.Task torch = task(plan, "event:more5_mine_dark:more5_craft_mine_torch");

        assertEquals(List.of(), ahead(plan, defend), "Мобы у раненого духа — защита на потолке: " + ids(plan));
        assertTrue(defend.priority() > mine.priority(), "Бой выше шахты: " + ids(plan));
        assertTrue(defend.priority() > chop.priority(), "Бой выше рубки: " + ids(plan));
        assertTrue(defend.priority() > hut.priority(), "Бой выше стройки: " + ids(plan));
        assertTrue(defend.priority() > backfill.priority(), "Бой выше засыпки: " + ids(plan));
        assertTrue(defend.priority() > demolish.priority(), "Бой выше сноса: " + ids(plan));
        assertTrue(defend.priority() > torch.priority(), "Бой выше света: " + ids(plan));
        assertTrue(defend.priority() > guard.priority(), "Бой выше охраны: " + ids(plan));

        assertTrue(retreat.priority() > mine.priority(), "Бегство выше шахты: " + ids(plan));
        assertTrue(retreat.priority() > demolish.priority(), "Бегство выше сноса: " + ids(plan));
        assertTrue(retreat.priority() > guard.priority(), "Бегство выше охраны: " + ids(plan));
        assertTrue(retreat.priority() > eat.priority(), "Бегство выше еды: дух уходит от мобов до обеда: " + ids(plan));

        assertTrue(arm.priority() > eat.priority(), "Раненый сперва надевает броню, потом ест: " + ids(plan));
        assertTrue(eat.priority() > sleep.priority(), "Голодный ест раньше сна: " + ids(plan));
        assertTrue(eat.priority() > fence.priority(), "Еда выше ремонта забора: " + ids(plan));
        assertTrue(fence.priority() > hut.priority(), "Побитый забор важнее новой хижины: " + ids(plan));
        assertTrue(fence.priority() > backfill.priority(), "Забор важнее засыпки воронки: " + ids(plan));
        assertTrue(fence.priority() > demolish.priority(), "Забор важнее сноса лишнего сундука: " + ids(plan));
        assertTrue(
                guard.priority() >= mine.priority(),
                "Охрана не ниже шахты (без факелов уголь — свет тёмной шахты, честный соперник охраны), шахта на доске: "
                        + ranked(plan));
        assertTrue(expedition.priority() > fence.priority(), "Поход выше ремонта забора: " + ids(plan));

        GraphPlan.Task hungryEat = task(plan, "event:hungry:eat");
        assertTrue(hungryEat.priority() > hut.priority(), "Голодный ест раньше стройки: " + ids(plan));
        assertTrue(mine.priority() > torch.priority(), "Сначала уголь, потом факел: " + ids(plan));
        assertFalse(
                plan.task("event:more5_mine_dark:more5_light_mine")
                        .filter(GraphPlan.Task::feasible)
                        .isPresent(),
                "Без факела шахту не освещают: " + ids(plan));
        assertEquals(
                BoardCase.ACTIVITY_RETREAT,
                BoardActivities.caseOfScript("retreat").orElseThrow(),
                "Бегство остаётся действием отхода");
        assertEquals(
                BoardCase.ACTIVITY_EXPEDITION,
                BoardActivities.caseOfScript("follow").orElseThrow(),
                "Поход остаётся действием сопровождения");
        assertTrue(scripts(BoardCase.LIGHTING).contains("place_block"), "Свет ставится блоками: " + ids(plan));
        assertTrue(scripts(BoardCase.CRAFT_TORCHES).contains("craft"), "Факел крафтится: " + ids(plan));
    }
}
