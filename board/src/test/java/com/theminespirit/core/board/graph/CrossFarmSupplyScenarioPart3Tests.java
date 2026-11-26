package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.CrossScene.stageTwo;
import static com.theminespirit.core.board.graph.CrossScene.task;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.graph.GraphState.Built;
import org.junit.jupiter.api.Test;

class CrossFarmSupplyScenarioPart3Tests {
    @Test
    void field_EmptyWithoutSeeds_RequestsSchematicRepairAndRaisesGather() {
        GraphWorld world = stageTwo()
                .built("wheat_field", Built.BUILT)
                .with("wheat_seeds", 0)
                .event("field_empty");
        GraphPlan plan = world.plan();
        GraphPlan.Task repair = task(plan, "event:field_empty:repair:wheat_field");

        assertTrue(
                repair.feasible(),
                "Пустая клетка поля заказывает ремонт схемы: иначе пшеница останется без единственного "
                        + "разрешённого пути посадки. При смене field_EmptyWithoutSeeds_RequestsSchematicRepairAndRaisesGather "
                        + "проверь farm.json field_empty и WorldPost.post.");
        assertTrue(
                plan.raised("gather_seeds"),
                "Событие пустого поля поднимает сбор семян: иначе ремонт схемы будет ждать материал и поле "
                        + "не засеется. При смене field_EmptyWithoutSeeds_RequestsSchematicRepairAndRaisesGather "
                        + "проверь field_empty push wheat_seeds.");
        assertTrue(
                plan.task("plant_crop").isEmpty(),
                "Ручной посев удалён из графа: иначе появится второй путь посадки в обход схемы. При смене "
                        + "field_EmptyWithoutSeeds_RequestsSchematicRepairAndRaisesGather проверь farm.json ways.");
    }

    @Test
    void field_EmptyWithFarmlandAndSeeds_RepairsWithoutManualTilling() {
        GraphWorld world = stageTwo()
                .built("wheat_field", Built.BUILT)
                .with("farmland", 8)
                .with("wheat_seeds", 8)
                .event("field_empty");
        GraphPlan plan = world.plan();
        GraphPlan.Task repair = task(plan, "event:field_empty:repair:wheat_field");

        assertTrue(
                repair.feasible(),
                "Готовая пашня и семена оставляют только ремонт схемы: иначе поле снова уйдёт в ручной "
                        + "посев или вспашку. При смене field_EmptyWithFarmlandAndSeeds_RepairsWithoutManualTilling "
                        + "проверь FarmInputs.stock и farm.json field_empty.");
        assertTrue(
                plan.task("till_soil").isEmpty() && plan.task("plant_crop").isEmpty(),
                "Ручные вспашка и посев удалены: иначе дух ходит к земле у дома и продовольственная цепочка "
                        + "расходится со схемой. При смене field_EmptyWithFarmlandAndSeeds_RepairsWithoutManualTilling "
                        + "проверь FarmInputs.crops и GraphPlanner.roots.");
    }
}
