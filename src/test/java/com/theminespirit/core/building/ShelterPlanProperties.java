package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class ShelterPlanProperties {
    @Provide
    Arbitrary<BlockCoord> origins() {
        return Combinators.combine(
                        Arbitraries.integers().between(-40, 40),
                        Arbitraries.integers().between(0, 200),
                        Arbitraries.integers().between(-40, 40))
                .as(BlockCoord::new);
    }

    @Provide
    Arbitrary<Integer> heights() {
        return Arbitraries.integers().between(1, 5);
    }

    @Provide
    Arbitrary<Integer> budgets() {
        return Arbitraries.integers().between(1, 128);
    }

    @Property
    void shelter_AnyInput_OnlyWallsOrRoof(
            @ForAll("origins") BlockCoord origin, @ForAll("heights") int wallHeight, @ForAll("budgets") int budget) {
        List<BlockCoord> cells = ShelterPlan.shelter(origin, wallHeight, budget);

        for (BlockCoord cell : cells) {
            int dx = cell.x() - origin.x();
            int dz = cell.z() - origin.z();
            int dy = cell.y() - origin.y();
            boolean wall = dy >= 0
                    && dy < wallHeight
                    && (dx == 0 || dx == ShelterPlan.SHELTER_SIDE - 1 || dz == 0 || dz == ShelterPlan.SHELTER_SIDE - 1);
            boolean roof = dy == wallHeight
                    && dx >= 0
                    && dx < ShelterPlan.SHELTER_SIDE
                    && dz >= 0
                    && dz < ShelterPlan.SHELTER_SIDE;
            assertTrue(
                    wall || roof,
                    "Клетка обязана быть стеной или крышей: интерьер пуст, иначе дух замурован внутри коробки");
        }
    }

    @Property
    void shelter_FullBudget_RoofCoversInterior(
            @ForAll("origins") BlockCoord origin, @ForAll("heights") int wallHeight) {
        List<BlockCoord> cells = ShelterPlan.shelter(origin, wallHeight, 128);

        for (int dx = 1; dx < ShelterPlan.SHELTER_SIDE - 1; dx++) {
            for (int dz = 1; dz < ShelterPlan.SHELTER_SIDE - 1; dz++) {
                assertTrue(
                        cells.contains(new BlockCoord(origin.x() + dx, origin.y() + wallHeight, origin.z() + dz)),
                        "Крыша обязана закрывать весь интерьер сверху: иначе пауки лезут внутрь, "
                                + "а скелеты стреляют поверх стен");
            }
        }
    }

    @Property
    void shelter_AnyInput_NoDuplicatesWithinBudget(
            @ForAll("origins") BlockCoord origin, @ForAll("heights") int wallHeight, @ForAll("budgets") int budget) {
        List<BlockCoord> cells = ShelterPlan.shelter(origin, wallHeight, budget);

        assertEquals(
                cells.size(),
                new HashSet<>(cells).size(),
                "Дубли — двойной расход блоков: каждая клетка ставится один раз");
        assertTrue(
                cells.size() <= budget,
                "План обязан влезать в бюджет: иначе строитель берётся за то, на что нет блоков");
    }

    @Property
    void shelter_SameInput_TwiceSameResult(
            @ForAll("origins") BlockCoord origin, @ForAll("heights") int wallHeight, @ForAll("budgets") int budget) {
        assertEquals(
                ShelterPlan.shelter(origin, wallHeight, budget),
                ShelterPlan.shelter(origin, wallHeight, budget),
                "Тот же вход даёт тот же план: иначе бронь клеток не сходится с постановкой");
    }
}
