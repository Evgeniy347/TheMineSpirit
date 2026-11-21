package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.CrossActions.id;
import static com.theminespirit.core.board.graph.CrossActions.scripts;
import static com.theminespirit.core.board.graph.CrossScene.ahead;
import static com.theminespirit.core.board.graph.CrossScene.chest;
import static com.theminespirit.core.board.graph.CrossScene.damaged;
import static com.theminespirit.core.board.graph.CrossScene.facts;
import static com.theminespirit.core.board.graph.CrossScene.ids;
import static com.theminespirit.core.board.graph.CrossScene.named;
import static com.theminespirit.core.board.graph.CrossScene.plan;
import static com.theminespirit.core.board.graph.CrossScene.spirit;
import static com.theminespirit.core.board.graph.CrossScene.stageOne;
import static com.theminespirit.core.board.graph.CrossScene.stageTwo;
import static com.theminespirit.core.board.graph.CrossScene.task;
import static com.theminespirit.core.board.graph.CrossScene.threat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardActivities;
import com.theminespirit.core.board.RescuePlans;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.board.graph.GraphState.Built;
import com.theminespirit.core.movement.GateCrossing;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CrossBattleWorkScenarioPart3Tests {
    @Test
    void combat_PitTrappedSpiritUnderMobs_RescueWaitsForDefend() {
        GraphWorld world = stageOne();
        world.with("dirt", 64).with("cobblestone", 64).with("oak_log", 64).with("oak_planks", 64);
        GraphFacts facts = facts(
                named(
                        spirit("spirit-1", 4, 20),
                        spirit("spirit-2"),
                        spirit(new CrossScene.SpiritSpec("spirit-3", 20, 20, true, true, "pit", List.of(), List.of()))),
                List.of(
                        threat("mob-a", "spirit-1", GraphFacts.TargetKind.SPIRIT, 1),
                        threat("mob-b", "spirit-1", GraphFacts.TargetKind.SPIRIT, 2)),
                List.of(),
                new GraphSiteFacts.Terrain(2, 0, 0, 2, Map.of(), Map.of("minecraft:dirt", 4), Map.of()),
                Map.of());
        GraphPlan plan = plan(world, facts);
        GraphPlan.Task defend = task(plan, "event:life_threat:defend");
        GraphPlan.Task rescue = task(plan, "rescue_spirit");
        GraphPlan.Task retreat = task(plan, "event:life_threat:retreat");
        assertEquals(
                List.of(),
                ahead(plan, defend),
                "Мобы у раненого духа поднимают защиту на потолок:  Иначе тест combat_PitTrappedSpiritUnderMobs_RescueWaitsForDefend перестанет ловить эту поломку. При смене combat_PitTrappedSpiritUnderMobs_RescueWaitsForDefend проверь это утверждение."
                        + ids(plan));
        assertTrue(
                rescue.feasible(),
                "Застрявший в яме дух получает спасение:  Иначе тест combat_PitTrappedSpiritUnderMobs_RescueWaitsForDefend перестанет ловить эту поломку. При смене combat_PitTrappedSpiritUnderMobs_RescueWaitsForDefend проверь это утверждение."
                        + ids(plan));
        assertTrue(
                retreat.feasible(),
                "Бегство из-под мобов тоже на доске:  Иначе тест combat_PitTrappedSpiritUnderMobs_RescueWaitsForDefend перестанет ловить эту поломку. При смене combat_PitTrappedSpiritUnderMobs_RescueWaitsForDefend проверь это утверждение."
                        + ids(plan));
        assertTrue(
                defend.priority() > rescue.priority(),
                "Сначала бой, потом спасение из ямы:  Иначе тест combat_PitTrappedSpiritUnderMobs_RescueWaitsForDefend перестанет ловить эту поломку. При смене combat_PitTrappedSpiritUnderMobs_RescueWaitsForDefend проверь это утверждение."
                        + ids(plan));
        assertEquals(
                RescuePlans.Self.PIT,
                RescuePlans.self(new RescuePlans.Situation(true, true, false, false, true)),
                "Из ямы есть выход по ступеням: механика выбирает PIT, а не прогулку Иначе тест combat_PitTrappedSpiritUnderMobs_RescueWaitsForDefend перестанет ловить эту поломку. При смене combat_PitTrappedSpiritUnderMobs_RescueWaitsForDefend проверь это утверждение.");
        assertEquals(
                RescuePlans.Self.NONE,
                RescuePlans.self(new RescuePlans.Situation(true, false, false, true, true)),
                "Без ступеней и инструмента из ямы не выйти: механика честно говорит NONE Иначе тест combat_PitTrappedSpiritUnderMobs_RescueWaitsForDefend перестанет ловить эту поломку. При смене combat_PitTrappedSpiritUnderMobs_RescueWaitsForDefend проверь это утверждение.");
        assertEquals(
                BoardCase.ACTIVITY_ESCAPE,
                BoardActivities.caseOfScript("dig_out").orElseThrow(),
                "Выход из клетки — действие escape Иначе тест combat_PitTrappedSpiritUnderMobs_RescueWaitsForDefend перестанет ловить эту поломку. При смене combat_PitTrappedSpiritUnderMobs_RescueWaitsForDefend проверь это утверждение.");
    }

    @Test
    void combat_HungryWoundedSpirit_FightsBeforeEating() {
        GraphWorld world = stageTwo();
        world.with("food", 8).with("coal", 0);
        GraphFacts facts = facts(
                named(spirit("spirit-1", 4, 4), spirit("spirit-2")),
                List.of(
                        threat("mob-a", "spirit-1", GraphFacts.TargetKind.SPIRIT, 1),
                        threat("mob-b", "spirit-1", GraphFacts.TargetKind.SPIRIT, 2)),
                List.of(chest(Map.of("food", 8))),
                GraphSiteFacts.Terrain.empty(),
                Map.of());
        GraphPlan plan = plan(world, facts);
        GraphPlan.Task defend = task(plan, "event:life_threat:defend");
        GraphPlan.Task eat = task(plan, "event:life_threat:eat");
        GraphPlan.Task mine = task(plan, "mine_coal");
        assertEquals(
                List.of(),
                ahead(plan, defend),
                "Раненый голодный дух под мобами: защита выбирается раньше всего:  Иначе тест combat_HungryWoundedSpirit_FightsBeforeEating перестанет ловить эту поломку. При смене combat_HungryWoundedSpirit_FightsBeforeEating проверь это утверждение."
                        + ids(plan));
        assertTrue(
                eat.feasible(),
                "Еда на складе делает обед выполнимым:  Иначе тест combat_HungryWoundedSpirit_FightsBeforeEating перестанет ловить эту поломку. При смене combat_HungryWoundedSpirit_FightsBeforeEating проверь это утверждение."
                        + ids(plan));
        assertTrue(
                defend.priority() > eat.priority(),
                "Сначала отбиться, потом есть:  Иначе тест combat_HungryWoundedSpirit_FightsBeforeEating перестанет ловить эту поломку. При смене combat_HungryWoundedSpirit_FightsBeforeEating проверь это утверждение."
                        + ids(plan));
        assertTrue(
                eat.priority() > mine.priority(),
                "Еда жизни выше шахты: голодный раненый дух ест раньше, чем копает (баг 12-14):  Иначе тест combat_HungryWoundedSpirit_FightsBeforeEating перестанет ловить эту поломку. При смене combat_HungryWoundedSpirit_FightsBeforeEating проверь это утверждение."
                        + ids(plan));
        assertEquals(
                BoardCase.EAT_TO_FULL,
                eat.boardCase(),
                "Голод закрывается внешним кейсом EAT_TO_FULL, а не шахтой Иначе тест combat_HungryWoundedSpirit_FightsBeforeEating перестанет ловить эту поломку. При смене combat_HungryWoundedSpirit_FightsBeforeEating проверь это утверждение.");
        assertEquals(
                BoardCase.ACTIVITY_COMBAT,
                BoardActivities.caseOfScript("attack").orElseThrow(),
                "Бой идёт действием attack Иначе тест combat_HungryWoundedSpirit_FightsBeforeEating перестанет ловить эту поломку. При смене combat_HungryWoundedSpirit_FightsBeforeEating проверь это утверждение.");
    }

    @Test
    void combat_RaidAndStrategistOrder_DefendOutranksFenceAndWarehouse() {
        GraphWorld world = stageTwo();
        world.built("warehouse", Built.ABSENT).built("fence", Built.PLANNED);
        world.with("oak_log", 64).with("oak_planks", 64).with("cobblestone", 64).with("oak_fence", 5);
        world.event("expedition_ready").event("more4_player_near");
        GraphFacts facts = facts(
                named(spirit("spirit-1", 4, 20), spirit("spirit-2")),
                List.of(
                        threat("mob-a", "spirit-1", GraphFacts.TargetKind.SPIRIT, 1),
                        threat("mob-b", "spirit-1", GraphFacts.TargetKind.SPIRIT, 2)),
                List.of(),
                GraphSiteFacts.Terrain.empty(),
                Map.of("fence", damaged("fence", 40, 3, Map.of("oak_fence", 5))));
        GraphPlan plan = plan(world, facts);
        GraphPlan.Task defend = task(plan, "event:life_threat:defend");
        GraphPlan.Task expedition = task(plan, "event:expedition_ready:expedition");
        GraphPlan.Task repair = task(plan, "event:repair_fence:repair:fence");
        GraphPlan.Task guard = task(plan, "event:more4_player_near:more4_guard_settlement");
        assertEquals(
                List.of(),
                ahead(plan, defend),
                "Набег поднимает защиту на потолок:  Иначе тест combat_RaidAndStrategistOrder_DefendOutranksFenceAndWarehouse перестанет ловить эту поломку. При смене combat_RaidAndStrategistOrder_DefendOutranksFenceAndWarehouse проверь это утверждение."
                        + ids(plan));
        assertTrue(
                expedition.feasible(),
                "Готовый поход не отменяется набегом:  Иначе тест combat_RaidAndStrategistOrder_DefendOutranksFenceAndWarehouse перестанет ловить эту поломку. При смене combat_RaidAndStrategistOrder_DefendOutranksFenceAndWarehouse проверь это утверждение."
                        + ids(plan));
        assertTrue(
                repair.feasible(),
                "Побитый забор остаётся выполнимым ремонтом:  Иначе тест combat_RaidAndStrategistOrder_DefendOutranksFenceAndWarehouse перестанет ловить эту поломку. При смене combat_RaidAndStrategistOrder_DefendOutranksFenceAndWarehouse проверь это утверждение."
                        + ids(plan));
        assertTrue(
                defend.priority() > expedition.priority(),
                "Поход ждёт, пока отобьёмся:  Иначе тест combat_RaidAndStrategistOrder_DefendOutranksFenceAndWarehouse перестанет ловить эту поломку. При смене combat_RaidAndStrategistOrder_DefendOutranksFenceAndWarehouse проверь это утверждение."
                        + ids(plan));
        assertTrue(
                expedition.priority() > repair.priority(),
                "Поход выше ремонта забора:  Иначе тест combat_RaidAndStrategistOrder_DefendOutranksFenceAndWarehouse перестанет ловить эту поломку. При смене combat_RaidAndStrategistOrder_DefendOutranksFenceAndWarehouse проверь это утверждение."
                        + ids(plan));
        assertTrue(
                repair.priority() > guard.priority(),
                "Ремонт забора выше патруля:  Иначе тест combat_RaidAndStrategistOrder_DefendOutranksFenceAndWarehouse перестанет ловить эту поломку. При смене combat_RaidAndStrategistOrder_DefendOutranksFenceAndWarehouse проверь это утверждение."
                        + ids(plan));
        assertTrue(
                plan.notices().stream().anyMatch(notice -> "warehouse".equals(notice.building())),
                "Склада нет в плане — граф просит стратега, а не строит вслепую:  Иначе тест combat_RaidAndStrategistOrder_DefendOutranksFenceAndWarehouse перестанет ловить эту поломку. При смене combat_RaidAndStrategistOrder_DefendOutranksFenceAndWarehouse проверь это утверждение."
                        + plan.notices());
        assertTrue(
                plan.task("building:warehouse").isEmpty(),
                "Без лота склада задание не ставится: "
                        + plan.task(
                                "building:warehouse Иначе тест combat_RaidAndStrategistOrder_DefendOutranksFenceAndWarehouse перестанет ловить эту поломку. При смене combat_RaidAndStrategistOrder_DefendOutranksFenceAndWarehouse проверь это утверждение."));
        assertEquals(
                BoardCase.ACTIVITY_EXPEDITION,
                BoardActivities.caseOfScript("follow").orElseThrow(),
                "Поход выполняется действием follow/escort Иначе тест combat_RaidAndStrategistOrder_DefendOutranksFenceAndWarehouse перестанет ловить эту поломку. При смене combat_RaidAndStrategistOrder_DefendOutranksFenceAndWarehouse проверь это утверждение.");
        GateCrossing gate = new GateCrossing(new BlockCoord(5, 64, 0), new BlockCoord(0, 64, 0));
        assertEquals(
                GateCrossing.Phase.INSIDE,
                gate.observe(new BlockCoord(5, 64, 0)),
                "Дух в створке ворот — проход ещё не закончен Иначе тест combat_RaidAndStrategistOrder_DefendOutranksFenceAndWarehouse перестанет ловить эту поломку. При смене combat_RaidAndStrategistOrder_DefendOutranksFenceAndWarehouse проверь это утверждение.");
        assertFalse(
                gate.closable(),
                "Пока дух в воротах, закрывать нельзя Иначе тест combat_RaidAndStrategistOrder_DefendOutranksFenceAndWarehouse перестанет ловить эту поломку. При смене combat_RaidAndStrategistOrder_DefendOutranksFenceAndWarehouse проверь это утверждение.");
        assertEquals(
                GateCrossing.Phase.BEYOND,
                gate.observe(new BlockCoord(6, 64, 0)),
                "Дух вышел на другую сторону — ворота можно закрыть Иначе тест combat_RaidAndStrategistOrder_DefendOutranksFenceAndWarehouse перестанет ловить эту поломку. При смене combat_RaidAndStrategistOrder_DefendOutranksFenceAndWarehouse проверь это утверждение.");
        assertTrue(
                gate.closable(),
                "После перехода ворота закрываются за духом Иначе тест combat_RaidAndStrategistOrder_DefendOutranksFenceAndWarehouse перестанет ловить эту поломку. При смене combat_RaidAndStrategistOrder_DefendOutranksFenceAndWarehouse проверь это утверждение.");
    }

    @Test
    void rescue_TrappedSpiritAndExpedition_RescueOutranksFollow() {
        GraphWorld world = stageOne();
        world.built("hut", Built.PLANNED);
        world.with("oak_log", 64).with("oak_planks", 64).with("cobblestone", 64);
        world.event("expedition_ready");
        GraphFacts facts = facts(
                named(
                        spirit("spirit-1"),
                        spirit("spirit-2"),
                        spirit(new CrossScene.SpiritSpec("spirit-3", 20, 20, true, true, "pit", List.of(), List.of()))),
                List.of(),
                List.of(),
                GraphSiteFacts.Terrain.empty(),
                Map.of());
        GraphPlan plan = plan(world, facts);
        GraphPlan.Task rescue = task(plan, "rescue_spirit");
        GraphPlan.Task expedition = task(plan, "event:expedition_ready:expedition");
        GraphPlan.Task hut = task(plan, "building:hut");
        assertTrue(
                rescue.feasible(),
                "Застрявший дух получает спасение:  Иначе тест rescue_TrappedSpiritAndExpedition_RescueOutranksFollow перестанет ловить эту поломку. При смене rescue_TrappedSpiritAndExpedition_RescueOutranksFollow проверь это утверждение."
                        + ids(plan));
        assertTrue(
                rescue.priority() > expedition.priority(),
                "Спасение жизни выше похода:  Иначе тест rescue_TrappedSpiritAndExpedition_RescueOutranksFollow перестанет ловить эту поломку. При смене rescue_TrappedSpiritAndExpedition_RescueOutranksFollow проверь это утверждение."
                        + ids(plan));
        assertTrue(
                hut.feasible(),
                "Стройка хижины остаётся на доске рядом со спасением:  Иначе тест rescue_TrappedSpiritAndExpedition_RescueOutranksFollow перестанет ловить эту поломку. При смене rescue_TrappedSpiritAndExpedition_RescueOutranksFollow проверь это утверждение."
                        + ids(plan));
        assertEquals(
                RescuePlans.Mode.HELPER,
                RescuePlans.assign(id("spirit-3"), 5, "яма: выхода нет", Optional.of(id("spirit-1")))
                        .mode(),
                "Сам дух выйти не может — доска назначает помощника Иначе тест rescue_TrappedSpiritAndExpedition_RescueOutranksFollow перестанет ловить эту поломку. При смене rescue_TrappedSpiritAndExpedition_RescueOutranksFollow проверь это утверждение.");
        assertTrue(
                scripts(BoardCase.RESCUE_SPIRIT).contains("clear_area"),
                "Спасатель разбирает клетку скриптом clear_area:  Иначе тест rescue_TrappedSpiritAndExpedition_RescueOutranksFollow перестанет ловить эту поломку. При смене rescue_TrappedSpiritAndExpedition_RescueOutranksFollow проверь это утверждение."
                        + scripts(BoardCase.RESCUE_SPIRIT));
        assertTrue(
                scripts(BoardCase.BUILD_HOUSE).contains("build_schematic"),
                "Хижина после спасения строится схемой build_schematic:  Иначе тест rescue_TrappedSpiritAndExpedition_RescueOutranksFollow перестанет ловить эту поломку. При смене rescue_TrappedSpiritAndExpedition_RescueOutranksFollow проверь это утверждение."
                        + scripts(BoardCase.BUILD_HOUSE));
    }
}
