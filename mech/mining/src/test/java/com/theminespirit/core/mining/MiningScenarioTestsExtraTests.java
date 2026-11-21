package com.theminespirit.core.mining;

import static com.theminespirit.core.mining.MiningScenarioTests.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.api.ClearScaffold;
import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MiningScenarioTestsExtraTests {

    @Test
    void clearScaffold_NeighbourPillars_EachSpiritClimbsOnceInOwnColumn() {
        List<ClearScaffoldWorld.Spirit> spirits = List.of(
                        new ClearScaffoldWorld.Pillar(10, 10, 15),
                        new ClearScaffoldWorld.Pillar(12, 10, 15),
                        new ClearScaffoldWorld.Pillar(14, 10, 15))
                .stream()
                .map(ClearScaffoldWorld.Spirit::new)
                .toList();
        Set<BlockCoord> world = new HashSet<>();
        int guard = 0;
        while (spirits.stream().anyMatch(clearing -> !clearing.done()) && guard++ < 400) {
            for (ClearScaffoldWorld.Spirit clearing : spirits) {
                if (!clearing.done()) {
                    clearing.act(world);
                }
            }
        }
        for (ClearScaffoldWorld.Spirit clearing : spirits) {
            assertEquals(
                    1,
                    clearing.climbs,
                    "Соседний столб разбирается одним подъёмом: " + ClearScaffoldWorld.summary(spirits)
                            + " Иначе тест clearScaffold_NeighbourPillars_EachSpiritClimbsOnceInOwnColumn перестанет ловить эту поломку. При смене clearScaffold_NeighbourPillars_EachSpiritClimbsOnceInOwnColumn проверь это утверждение.");
            assertEquals(
                    1,
                    clearing.columns.size(),
                    "Один подъём — одна колонка подмостей: " + clearing.columns
                            + " Иначе тест clearScaffold_NeighbourPillars_EachSpiritClimbsOnceInOwnColumn перестанет ловить эту поломку. При смене clearScaffold_NeighbourPillars_EachSpiritClimbsOnceInOwnColumn проверь это утверждение.");
            assertEquals(
                    clearing.feet.x() * 100_000 + clearing.feet.z(),
                    clearing.columns.iterator().next(),
                    "Подмости стоят в колонке духа: " + clearing.feet
                            + " Иначе тест clearScaffold_NeighbourPillars_EachSpiritClimbsOnceInOwnColumn перестанет ловить эту поломку. При смене clearScaffold_NeighbourPillars_EachSpiritClimbsOnceInOwnColumn проверь это утверждение.");
        }
        assertTrue(
                world.isEmpty(),
                "Соседние столбы не оставили башен: " + world
                        + " Иначе тест clearScaffold_NeighbourPillars_EachSpiritClimbsOnceInOwnColumn перестанет ловить эту поломку. При смене clearScaffold_NeighbourPillars_EachSpiritClimbsOnceInOwnColumn проверь это утверждение.");
    }

    @Test
    void clearScaffold_LeasedCells_OwnerKeepsThemForCleanup() {
        List<BlockCoord> column = new ClearScaffoldWorld.Pillar(10, 10, 8).cells();
        Set<BlockCoord> leased = Set.of(column.get(column.size() - 1), column.get(column.size() - 2));

        List<BlockCoord> forOwner = ClearScaffold.withoutLeased(column, leased::contains);
        List<BlockCoord> forOther = ClearScaffold.withoutLeased(column, cell -> false);

        assertEquals(
                column.size() - leased.size(),
                forOwner.size(),
                "Клетки своей аренды не цель: иначе дух ломает свои же подмости и строит новый столб"
                        + " При смене clearScaffold_LeasedCells_OwnerKeepsThemForCleanup проверь это утверждение.");
        assertFalse(
                forOwner.containsAll(leased),
                "Свои клетки исключены из целей: " + forOwner
                        + " Иначе тест clearScaffold_LeasedCells_OwnerKeepsThemForCleanup перестанет ловить эту поломку. При смене clearScaffold_LeasedCells_OwnerKeepsThemForCleanup проверь это утверждение.");
        assertEquals(
                column.size(),
                forOther.size(),
                "Чужая аренда не отнимает у владельца его клетки"
                        + " Иначе тест clearScaffold_LeasedCells_OwnerKeepsThemForCleanup перестанет ловить эту поломку. При смене clearScaffold_LeasedCells_OwnerKeepsThemForCleanup проверь это утверждение.");
        assertEquals(
                column.size() - leased.size(),
                ClearScaffold.withoutLeased(forOther, leased::contains).size(),
                "Второй дух видит только незанятые клетки: чужие подмости не его работа"
                        + " Иначе тест clearScaffold_LeasedCells_OwnerKeepsThemForCleanup перестанет ловить эту поломку. При смене clearScaffold_LeasedCells_OwnerKeepsThemForCleanup проверь это утверждение.");
    }
}
