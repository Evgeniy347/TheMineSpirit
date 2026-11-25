package com.theminespirit.core.chunk;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.FenceLine;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.TownStreets;
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SettlementZonePostsTests {

    @Test
    void update_SamePostsInstance_NoRebuild() {
        TownPlan plan = planAt(0, 0);
        Set<BlockCoord> anchors = Set.of(new BlockCoord(0, 70, 0));
        Set<ColumnMask.Cell> posts = Set.copyOf(List.of(new ColumnMask.Cell(100, 100)));
        SettlementZone zone = new SettlementZone();
        assertTrue(
                zone.update(plan, anchors, List.of(), 1, 8, posts),
                "Первый расчёт зоны обязан её построить: иначе тест update_SamePostsInstance_NoRebuild"
                        + " ничего не измеряет Иначе сборка «нет пересборки» проверяет пустую зону."
                        + " При смене update_SamePostsInstance_NoRebuild проверь это утверждение.");
        assertFalse(
                zone.update(plan, anchors, List.of(), 1, 8, posts),
                "Тот же неизменяемый набор столбов не должен пересобирать зону: иначе каждый тик"
                        + " сеть столбов ограды хешируется заново и держит серверный поток."
                        + " При смене update_SamePostsInstance_NoRebuild проверь это утверждение.");
    }

    @Test
    void update_ChangedPosts_Rebuilds() {
        TownPlan plan = planAt(0, 0);
        Set<BlockCoord> anchors = Set.of(new BlockCoord(0, 70, 0));
        SettlementZone zone = new SettlementZone();
        assertTrue(
                zone.update(plan, anchors, List.of(), 1, 8, Set.of(new ColumnMask.Cell(100, 100))),
                "Первый расчёт зоны обязан её построить: иначе тест update_ChangedPosts_Rebuilds"
                        + " не видит разницы между наборами столбов."
                        + " При смене update_ChangedPosts_Rebuilds проверь это утверждение.");
        assertTrue(
                zone.update(plan, anchors, List.of(), 1, 8, Set.of(new ColumnMask.Cell(101, 100))),
                "Смена столба ограды обязана пересобрать зону: иначе чанк нового столба выгрузится"
                        + " и ремонт ограды увидит «мир неизвестен»."
                        + " При смене update_ChangedPosts_Rebuilds проверь это утверждение.");
    }

    private static TownPlan planAt(int minX, int minZ) {
        ColumnMask empty = new ColumnMask(minX, minZ, 20, 20, new BitSet());
        return new TownPlan(
                new BlockCoord(minX, 70, minZ),
                ColumnMask.rect(minX, minZ, minX + 19, minZ + 19),
                new TownStreets(empty, empty),
                List.of(),
                new FenceLine(List.of(), List.of()),
                List.of());
    }
}
