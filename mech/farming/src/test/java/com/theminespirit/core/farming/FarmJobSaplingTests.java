package com.theminespirit.core.farming;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.farming.api.CropKind;
import org.junit.jupiter.api.Test;

class FarmJobSaplingTests {
    @Test
    void bonemealSaplings_AnyKind_OnlyBoneMeal() {
        assertTrue(
                FarmJob.BONE_MEAL.bonemealSaplings(CropKind.ANY),
                "Костная мука «любого вида» растит и саженцы: без этого посаженное дерево не ускорить, и сад "
                        + "ждёт десятки тиков роста. При смене FarmJob.BONE_MEAL.bonemealSaplings проверь живой "
                        + "подкорм саженца и bonemealSapling_SaplingCell_OnlyBoneMeal");
        assertFalse(
                FarmJob.BONE_MEAL.bonemealSaplings(CropKind.WHEAT),
                "Названная культура растит только её: саженцы в чужую подкормку не попадают, иначе пшеница "
                        + "вдруг ускорит дерево и расход муки станет непредсказуем. При смене проверки вида в "
                        + "bonemealSaplings проверь строку WHEAT в этом тесте");
        assertFalse(
                FarmJob.HARVEST.bonemealSaplings(CropKind.ANY),
                "Саженцы — только забота костной муки: сбор урожая их не трогает, иначе harvest тратит муку на "
                        + "чужую операцию и сад не закрывается. При смене таблицы задач FarmJob проверь строку "
                        + "HARVEST этого теста и живой сбор урожая рядом с саженцами");
    }

    @Test
    void bonemealSapling_SaplingCell_OnlyBoneMeal() {
        assertTrue(
                FarmJob.BONE_MEAL.bonemealSapling(true),
                "Костная мука растит саженец и без культуры в клетке: сад не ждёт зрелого посева, иначе дерево "
                        + "растёт только с ручной культурой и план не закрывается. При смене bonemealSapling "
                        + "проверь живой уск саженца в клетке без культуры");
        assertFalse(
                FarmJob.BONE_MEAL.bonemealSapling(false),
                "Обычную клетку костная мука растит по культуре, а не как саженец: иначе мука применится к "
                        + "любой клетке и сгорит на пустой земле. При смене проверки саженца в bonemealSapling "
                        + "проверь пару true/false этого теста");
        assertFalse(
                FarmJob.PLANT.bonemealSapling(true),
                "Посадка не подкармливает саженцы: у неё другая работа, иначе PLANT начнёт тратить муку вместо "
                        + "посадки и сад встанет. При смене FarmJob.PLANT проверь таблицу задач и живой цикл "
                        + "фермы после посадки");
    }
}
