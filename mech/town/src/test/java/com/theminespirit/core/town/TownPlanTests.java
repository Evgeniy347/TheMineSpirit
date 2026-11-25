package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TownPlanTests {
    private static final BlockCoord ANCHOR = new BlockCoord(200, 70, -300);

    @Test
    void plaza_PlanWithoutPlaza_ReturnsEmpty() {
        TownPlan plan = plan(new Placement("hut", "Хижина", 1, 0, 0, 5, 5, Placement.Facing.NORTH));

        assertEquals(
                Optional.empty(),
                plan.plaza(),
                "Без площади в плане нет и точки возрождения на площади: иначе возрождение уводит духа на случайный участок, а не на прежнюю базу"
                        + " При смене plaza_PlanWithoutPlaza_ReturnsEmpty проверь это утверждение.");
    }

    @Test
    void plaza_SeveralPlazas_ReturnsLowestId() {
        TownPlan plan = plan(
                new Placement("plaza_2", TownPlan.PLAZA_NAME, 1, 40, 0, 16, 16, Placement.Facing.NORTH),
                new Placement("plaza", TownPlan.PLAZA_NAME, 1, 0, 0, 32, 32, Placement.Facing.NORTH));

        assertEquals(
                Optional.of("plaza"),
                plan.plaza().map(Placement::id),
                "При нескольких площадях берётся главная с наименьшим id: иначе духи возрождаются на разных площадях от порядка участков в плане"
                        + " При смене plaza_SeveralPlazas_ReturnsLowestId проверь это утверждение.");
    }

    @Test
    void plaza_ForeignName_IsIgnored() {
        TownPlan plan = plan(new Placement("yard", "Двор", 1, 0, 0, 8, 8, Placement.Facing.NORTH));

        assertEquals(
                Optional.empty(),
                plan.plaza(),
                "Площадь узнаётся по подписи участка: иначе двор и прочие участки становятся точкой возрождения по ошибке"
                        + " При смене plaza_ForeignName_IsIgnored проверь это утверждение.");
    }

    private static TownPlan plan(Placement... places) {
        return new TownPlan(
                ANCHOR,
                ColumnMask.rect(0, 0, 63, 63),
                new TownStreets(ColumnMask.rect(0, 0, 0, 63), ColumnMask.rect(0, 0, 0, 0)),
                List.of(places),
                new FenceLine(List.of(), List.of()),
                List.of());
    }
}
