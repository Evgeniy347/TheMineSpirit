package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.CrossActions.scripts;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardActivities;
import com.theminespirit.core.board.BoardCase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CrossSupplyScenarioTests {
    @Test
    void combat_WoundedWithoutArmor_FightsBeforeCraftingArmor() {
        GraphWorld world = stageTwo();
        world.with("iron_ingot", 64);
        GraphFacts facts = facts(
                named(spirit("spirit-1", 4, 20), spirit("spirit-2")),
                List.of(
                        threat("mob-a", "spirit-1", GraphFacts.TargetKind.SPIRIT, 1),
                        threat("mob-b", "spirit-1", GraphFacts.TargetKind.SPIRIT, 2)),
                List.of(),
                GraphSiteFacts.Terrain.empty(),
                Map.of());
        GraphPlan plan = plan(world, facts);

        GraphPlan.Task defend = task(plan, "event:life_threat:defend");
        GraphPlan.Task armor = task(plan, "event:gear_craft_armor:craft_armor");

        assertEquals(100, defend.priority(), "Защита жизни на потолке: " + ids(plan));
        assertTrue(armor.feasible(), "Броня без надетых слотов ставится в крафт: " + ids(plan));
        assertTrue(
                defend.priority() > armor.priority(),
                "Раненый дух сначала отбивается, броню крафтит позже: " + ids(plan));
        assertEquals(
                BoardCase.ACTIVITY_CRAFT,
                BoardActivities.caseOfScript("craft").orElseThrow(),
                "Крафт брони — действие крафта");
        assertTrue(
                BoardActivities.caseOfScript("attack")
                        .filter(BoardCase.ACTIVITY_COMBAT::equals)
                        .isPresent(),
                "Бой идёт действием attack, а не крафтом");
    }

    @Test
    void social_PlayerNearAndExpedition_ExpeditionOutranksGuard() {
        GraphWorld world = stageOne();
        world.event("more4_player_near").event("expedition_ready");
        GraphPlan plan = plan(world, facts(named(spirit("spirit-1"), spirit("spirit-2"))));

        GraphPlan.Task expedition = task(plan, "event:expedition_ready:expedition");
        GraphPlan.Task guard = task(plan, "event:more4_player_near:more4_guard_settlement");

        assertTrue(expedition.feasible(), "Готовый поход остаётся выполнимым: " + ids(plan));
        assertTrue(guard.feasible(), "Игрок рядом — поселение охраняется: " + ids(plan));
        assertTrue(expedition.priority() > guard.priority(), "Поход важнее охраны при игроке рядом: " + ids(plan));
        assertEquals(
                BoardCase.ACTIVITY_EXPEDITION,
                BoardActivities.caseOfScript("follow").orElseThrow(),
                "Поход идёт действием follow/escort");
        assertEquals(
                BoardCase.ACTIVITY_GUARD,
                BoardActivities.caseOfScript("patrol").orElseThrow(),
                "Охрана при игроке — патруль, а не поход");
    }

    @Test
    void storage_FullWarehouseNotBuilt_GraphBuildsStorageAndPostsSpace() {
        GraphWorld world = stageTwo();
        world.storageFree = 2;
        GraphPlan plan = plan(world, facts(named(spirit("spirit-1"), spirit("spirit-2"))));

        GraphPlan.Task warehouse = task(plan, "building:warehouse");
        GraphPlan.Task space = task(plan, "store_space");

        assertTrue(warehouse.feasible(), "Склад переполнен — граф ставит стройку склада: " + ids(plan));
        assertTrue(space.feasible(), "Граф также ставит расширение/сортировку места: " + ids(plan));
        assertTrue(
                scripts(BoardCase.BUILD_WAREHOUSE).contains("build_schematic"),
                "Склад строится схемой build_schematic: " + scripts(BoardCase.BUILD_WAREHOUSE));
        assertTrue(
                scripts(BoardCase.STOCK_CHESTS).contains("deposit"),
                "Склад раскладывает предметы через deposit: " + scripts(BoardCase.STOCK_CHESTS));
    }
}
