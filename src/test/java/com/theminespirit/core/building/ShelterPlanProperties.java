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
        return Combinators.combine(Arbitraries.integers().between(-40, 40),
                Arbitraries.integers().between(0, 200), Arbitraries.integers().between(-40, 40))
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
    void shelter_AnyInput_OnlyPerimeterCells(@ForAll("origins") BlockCoord origin,
            @ForAll("heights") int wallHeight, @ForAll("budgets") int budget) {
        List<BlockCoord> cells = ShelterPlan.shelter(origin, wallHeight, budget);

        for (BlockCoord cell : cells) {
            int dx = cell.x() - origin.x();
            int dz = cell.z() - origin.z();
            int dy = cell.y() - origin.y();
            assertTrue(dx >= 0 && dx < ShelterPlan.SHELTER_SIDE && dz >= 0 && dz < ShelterPlan.SHELTER_SIDE
                    && dy >= 0 && dy < wallHeight,
                    "Клетка обязана лежать в коробе стен: иначе стройка расползается за укрытие");
            assertTrue(dx == 0 || dx == ShelterPlan.SHELTER_SIDE - 1 || dz == 0
                    || dz == ShelterPlan.SHELTER_SIDE - 1,
                    "Клетка обязана быть периметром, а не интерьером: иначе дух замурован внутри коробки");
        }
    }

    @Property
    void shelter_AnyInput_NoDuplicatesWithinBudget(@ForAll("origins") BlockCoord origin,
            @ForAll("heights") int wallHeight, @ForAll("budgets") int budget) {
        List<BlockCoord> cells = ShelterPlan.shelter(origin, wallHeight, budget);

        assertEquals(cells.size(), new HashSet<>(cells).size(),
                "Дубли — двойной расход блоков: каждая клетка ставится один раз");
        assertTrue(cells.size() <= budget,
                "План обязан влезать в бюджет: иначе строитель берётся за то, на что нет блоков");
    }

    @Property
    void shelter_SameInput_TwiceSameResult(@ForAll("origins") BlockCoord origin,
            @ForAll("heights") int wallHeight, @ForAll("budgets") int budget) {
        assertEquals(ShelterPlan.shelter(origin, wallHeight, budget),
                ShelterPlan.shelter(origin, wallHeight, budget),
                "Тот же вход даёт тот же план: иначе бронь клеток не сходится с постановкой");
    }
}
