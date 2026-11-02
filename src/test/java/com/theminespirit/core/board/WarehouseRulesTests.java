package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WarehouseRulesTests {
    @Test
    void nextLevel_StandingLevels_ReturnsFirstMissing() {
        assertEquals(
                1, WarehouseRules.nextLevel(Set.of()), "Первый склад — уровень 1: начинать со старшего уровня нельзя");
        assertEquals(2, WarehouseRules.nextLevel(Set.of(1)), "После первого склада строится второй уровень");
        assertEquals(3, WarehouseRules.nextLevel(Set.of(1, 2)), "После второго — третий");
        assertEquals(
                0,
                WarehouseRules.nextLevel(Set.of(1, 2, 3, 4)),
                "Все четыре уровня стоят: следующий уровень не выдаётся");
    }

    @Test
    void canBuild_IntermediateLevels_LimitedToThree() {
        assertTrue(
                WarehouseRules.canBuild(1, Set.of()),
                "Первый склад не последнего уровня разрешён: лимит ещё не исчерпан");
        assertTrue(
                WarehouseRules.canBuild(3, Set.of(1, 2)),
                "Третий склад не последнего уровня разрешён: стоящих не последнего уровня два");
        assertFalse(
                WarehouseRules.canBuild(1, Set.of(1, 2, 3)),
                "Четвёртый склад не последнего уровня запрещён: складов не последнего уровня уже три");
        assertTrue(
                WarehouseRules.canBuild(4, List.of(1, 2, 3, 4, 4, 4)),
                "Складов последнего уровня можно сколько угодно: лимит трёх на них не действует");
    }

    @Test
    void canBuild_LevelOutsideOneToFour_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> WarehouseRules.canBuild(0, Set.of()),
                "Нулевого уровня склада нет: заказ вне шкалы обязан отвергаться");
        assertThrows(
                IllegalArgumentException.class,
                () -> WarehouseRules.canBuild(5, Set.of()),
                "Пятого уровня склада нет: заказ вне шкалы обязан отвергаться");
    }

    @Test
    void relocateOldLevels_HigherStanding_ReturnsEveryOldLevel() {
        assertEquals(List.of(), WarehouseRules.relocateOldLevels(Set.of()), "Без складов переносить нечего");
        assertEquals(
                List.of(),
                WarehouseRules.relocateOldLevels(Set.of(1)),
                "Один склад переносить некуда: старшего уровня ещё нет");
        assertEquals(
                List.of(1),
                WarehouseRules.relocateOldLevels(Set.of(1, 2)),
                "После постройки второго склада первый обязан получить задачу переноса и сноса");
        assertEquals(
                List.of(1, 2),
                WarehouseRules.relocateOldLevels(Set.of(1, 2, 3)),
                "После третьего склада переносятся и первый, и второй");
    }

    @Test
    void site_EachLevel_StaysAtHomeAnchor() {
        BlockCoord home = new BlockCoord(10, 64, -5);

        assertEquals(home, WarehouseRules.site(home, 1), "Первый склад ставится у дома");
        assertEquals(
                home,
                WarehouseRules.site(home, 2),
                "Второй склад улучшает первый на месте: второй участок не занимается");
        assertThrows(
                IllegalArgumentException.class, () -> WarehouseRules.site(home, 0), "Места для нулевого уровня нет");
    }

    @Test
    void stageOpen_StageOneBusy_BlocksOnlyUntilSecondStage() {
        assertFalse(
                WarehouseRules.stageOpen(1, true),
                "Этап 1 ещё строится — склад не начинают: иначе стройка склада спорит с домом и двором");
        assertTrue(
                WarehouseRules.stageOpen(2, true),
                "Этап 2 начат — ремонт этапа 1 не держит склад: иначе склад не выдаётся никогда");
        assertTrue(WarehouseRules.stageOpen(1, false), "Этап 1 свободен — склад можно начинать сразу после дома");
        assertThrows(
                IllegalArgumentException.class,
                () -> WarehouseRules.stageOpen(0, false),
                "Нулевого этапа поселения нет: этап вне шкалы обязан отвергаться");
    }

    @Test
    void scheme_EachLevel_NamesItsOwnPreset() {
        assertEquals("storage_v1", WarehouseRules.scheme(1), "Уровень 1 — схема storage_v1");
        assertEquals("storage_v4", WarehouseRules.scheme(4), "Уровень 4 — схема storage_v4");
        assertThrows(IllegalArgumentException.class, () -> WarehouseRules.scheme(5), "Схемы пятого уровня нет");
    }
}
